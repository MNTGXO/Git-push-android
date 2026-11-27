package com.mntx.gitpulse

import com.mntx.gitpulse.sync.GitHashing
import com.mntx.gitpulse.sync.PullOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * An interrupted pull must not re-download bytes it already has — but a *damaged* leftover must
 * never be trusted. Both rules hinge on identifying a complete file by git's blob SHA-1.
 */
class PullResumeTest {

    private fun tempDir(): File = Files.createTempDirectory("pull-resume").toFile()

    @Test
    fun aCompleteFileIsIdentifiedByGitBlobSha() {
        val dir = tempDir()
        val content = "hello\n".toByteArray()
        val target = File(dir, "a.txt").apply { writeBytes(content) }
        val expected = GitHashing.blobSha(content)

        assertTrue("exact bytes must match", completed(target, expected, content.size.toLong()))
    }

    @Test
    fun aTruncatedFileIsReDownloaded() {
        val dir = tempDir()
        val full = "hello world, this is longer\n".toByteArray()
        val target = File(dir, "a.txt").apply { writeBytes(full.copyOf(5)) }
        val expected = GitHashing.blobSha(full)

        // Size gate alone already rejects it, and the sha would too.
        assertFalse("truncated bytes must not be trusted", completed(target, expected, full.size.toLong()))
    }

    @Test
    fun anEditedFileWithMatchingSizeIsStillRejected() {
        val dir = tempDir()
        val full = "hello\n".toByteArray()
        // Same length, different content: only the blob SHA can catch this.
        val target = File(dir, "a.txt").apply { writeBytes("world\n".toByteArray()) }
        val expected = GitHashing.blobSha(full)

        assertEquals(full.size, target.length())
        assertFalse("same size but different bytes must not match", completed(target, expected, full.size.toLong()))
    }

    @Test
    fun aMissingFileIsNotComplete() {
        val dir = tempDir()
        assertFalse(completed(File(dir, "nope.txt"), GitHashing.blobSha("x".toByteArray()), 1))
    }

    @Test
    fun resumeDefaultsOnAndCachesTheCommit() {
        val defaults = PullOptions(owner = "o", repo = "r", ref = "main", destination = tempDir())
        assertTrue("pulls should resume by default", defaults.resume)
        assertEquals(null, defaults.resumeMarker)
    }

    /** Mirrors SyncEngine.isCompleteCopy, which is private and needs a live tree entry. */
    private fun completed(target: File, expectedSha: String, expectedSize: Long): Boolean {
        if (!target.isFile) return false
        if (target.length() != expectedSize) return false
        val actual = runCatching { GitHashing.walk(target.inputStream()).first }.getOrNull() ?: return false
        return actual == expectedSha
    }
}
