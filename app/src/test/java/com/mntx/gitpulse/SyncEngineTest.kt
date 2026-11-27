package com.mntx.gitpulse

import com.mntx.gitpulse.sync.ChangeKind
import com.mntx.gitpulse.sync.DiffEngine
import com.mntx.gitpulse.sync.GitHashing
import com.mntx.gitpulse.sync.LocalFile
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.sync.RemoteBlob
import com.mntx.gitpulse.sync.SyncFilters
import com.mntx.gitpulse.sync.VerifiedLocalFile
import com.mntx.gitpulse.sync.vcsSha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diff engine is the most safety-critical part of the app: a wrong answer silently deletes
 * files from a user's repository. These tests pin git's blob hashing and the mirror semantics.
 */
class SyncEngineTest {

    /** Known git blob SHA for the content "hello\n". */
    private val helloBlob = "ce013625030ba8dba906f756967f9e9ca394464a"

    @Test
    fun `git blob sha matches real git`() {
        val bytes = "hello\n".toByteArray()
        val sha = GitHashing.blobSha(bytes.size.toLong(), java.security.MessageDigest.getInstance("SHA-1").digest(bytes))
        assertEquals(helloBlob, sha)
    }

    @Test
    fun `streaming hash matches in-memory hash`() {
        val content = "hello\n".toByteArray()
        val (sha, size) = GitHashing.walk(content.inputStream())
        assertEquals(helloBlob, sha)
        assertEquals(5L, size)
    }

    @Test
    fun `base64 payload round-trips`() {
        val content = "fun main() = println(\"pulse\")\n".toByteArray()
        val hashed = GitHashing.hashAndEncode(content.inputStream(), content.size.toLong())
        val decoded = android.util.Base64.decode(hashed.base64, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        assertEquals(String(content), String(decoded))
        assertEquals(hashed.blobSha, GitHashing.blobSha(hashed.size, java.security.MessageDigest.getInstance("SHA-1").digest(content)))
    }

    private fun localFile(path: String, sha: String, size: Long = 10) =
        VerifiedLocalFile(LocalFile(path, size) { ByteArray(0).inputStream() }, sha)

    @Test
    fun `identical file is unchanged`() {
        val manifest = LocalManifest("site", listOf(localFile("index.html", "abc")))
        val remote = mapOf("index.html" to RemoteBlob("index.html", "abc", 10))
        val diff = DiffEngine.diff(manifest, remote)
        assertEquals(1, diff.unchanged)
        assertTrue(diff.isEmpty)
    }

    @Test
    fun `changed sha is modified`() {
        val manifest = LocalManifest("site", listOf(localFile("index.html", "new")))
        val remote = mapOf("index.html" to RemoteBlob("index.html", "old", 10))
        val diff = DiffEngine.diff(manifest, remote)
        assertEquals(1, diff.modified.size)
        assertEquals("index.html", diff.modified.first().path)
    }

    @Test
    fun `missing local file is deleted from remote - mirror semantics`() {
        val manifest = LocalManifest("site", listOf(localFile("index.html", "abc")))
        val remote = mapOf(
            "index.html" to RemoteBlob("index.html", "abc", 10),
            "legacy/old.css" to RemoteBlob("legacy/old.css", "zzz", 42),
        )
        val diff = DiffEngine.diff(manifest, remote)
        assertEquals(1, diff.deleted.size)
        assertEquals("legacy/old.css", diff.deleted.first().path)
        assertEquals(ChangeKind.DELETED, diff.deleted.first().kind)
    }

    @Test
    fun `new local file is added`() {
        val manifest = LocalManifest("site", listOf(localFile("assets/hero.webp", "h1")))
        val diff = DiffEngine.diff(manifest, emptyMap())
        assertEquals(1, diff.added.size)
        assertEquals(0, diff.deleted.size)
    }

    @Test
    fun `junk files are skipped and reported`() {
        val manifest = LocalManifest(
            "site",
            listOf(
                localFile("index.html", "a"),
                localFile("node_modules/react/index.js", "b"),
                localFile(".DS_Store", "c"),
            ),
        )
        val diff = DiffEngine.diff(manifest, emptyMap())
        assertEquals(1, diff.added.size)
        assertEquals(2, diff.skipped.size)
        assertTrue(diff.skipped.any { it.path.contains("node_modules") })
    }

    @Test
    fun `oversized files are skipped`() {
        val filters = SyncFilters(maxFileBytes = 100)
        val big = localFile("video.mp4", "big", size = 5_000)
        val diff = DiffEngine.diff(LocalManifest("site", listOf(big)), emptyMap(), filters)
        assertTrue(diff.added.isEmpty())
        assertEquals(1, diff.skipped.size)
    }

    @Test
    fun `unknown hash falls back to size comparison`() {
        val plain = LocalFile("readme.md", 100) { ByteArray(0).inputStream() }
        assertNull(plain.vcsSha)
        val sameSize = DiffEngine.diff(LocalManifest("s", listOf(plain)), mapOf("readme.md" to RemoteBlob("readme.md", "x", 100)))
        assertEquals(1, sameSize.unchanged)

        val differentSize = DiffEngine.diff(LocalManifest("s", listOf(plain)), mapOf("readme.md" to RemoteBlob("readme.md", "x", 55)))
        assertEquals(1, differentSize.modified.size)
    }

    @Test
    fun `empty folder against populated repo deletes everything`() {
        val diff = DiffEngine.diff(
            LocalManifest("empty", emptyList()),
            mapOf(
                "a.txt" to RemoteBlob("a.txt", "1", 1),
                "dir/b.txt" to RemoteBlob("dir/b.txt", "2", 2),
            ),
        )
        assertEquals(2, diff.deleted.size)
        assertNotNull(diff.deleted.firstOrNull())
    }
}
