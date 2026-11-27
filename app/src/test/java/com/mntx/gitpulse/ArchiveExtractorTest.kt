package com.mntx.gitpulse

import com.mntx.gitpulse.sync.ArchiveExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, body) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun tempDir() = File.createTempFile("extract", "").let { it.delete(); it.mkdirs(); it }

    @Test
    fun extractsAndCollapsesSingleRootFolder() {
        runBlocking { extractAndAssertCollapse() }
    }

    private suspend fun extractAndAssertCollapse() {
        val dest = tempDir()
        val payload = zip(mapOf(
            "website/index.html" to "<h1>hi</h1>",
            "website/assets/app.js" to "console.log(1)",
            "__MACOSX/website/._index.html" to "junk",
            "website/.DS_Store" to "junk",
        ))
        val result = ArchiveExtractor().extract(payload.inputStream(), dest)
        assertEquals(2, result.files)
        assertTrue(result.collapsedRoot)
        assertTrue(File(dest, "index.html").exists())
        assertTrue(File(dest, "assets/app.js").exists())
        assertTrue(result.skipped.any { it.contains("__MACOSX") })
        dest.deleteRecursively()
    }

    @Test
    fun rejectsZipSlipEntries() {
        runBlocking { assertZipSlipBlocked() }
    }

    private suspend fun assertZipSlipBlocked() {
        val dest = tempDir()
        val payload = zip(mapOf(
            "ok.txt" to "fine",
            "../../escaped.txt" to "nope",
        ))
        val result = ArchiveExtractor().extract(payload.inputStream(), dest)
        assertTrue(File(dest, "ok.txt").exists())
        assertTrue("must not write outside destination", result.skipped.any { it.contains("unsafe path") })
        dest.deleteRecursively()
    }

    @Test
    fun enforcesEntryLimit() {
        runBlocking { assertEntryLimitEnforced() }
    }

    private suspend fun assertEntryLimitEnforced() {
        val dest = tempDir()
        val payload = zip((1..30).associate { "f$it.txt" to "x" })
        var failed = false
        try {
            ArchiveExtractor().extract(payload.inputStream(), dest, maxEntries = 5)
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue("entry cap must abort the extraction", failed)
        dest.deleteRecursively()
    }
}
