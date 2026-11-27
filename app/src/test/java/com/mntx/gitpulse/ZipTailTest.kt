package com.mntx.gitpulse

import com.mntx.gitpulse.sync.ZipTail
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The push screen shows "N files" using this count, and it used to be produced by reading the
 * whole archive into a ByteArray — which OOMs on the large zips people actually upload.
 *
 * [ZipTail.entryCount] is the pure part: given the archive's *tail*, report how many entries it
 * has. Testing it against real `ZipOutputStream` bytes is the only way to be sure the byte offsets
 * are right, since a mistaken offset silently reports 0 and the UI falls back to a guess.
 */
class ZipTailTest {

    private fun zipOf(count: Int, padding: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            repeat(count) { i ->
                zip.putNextEntry(ZipEntry("file-$i.txt"))
                zip.write("content $i".toByteArray())
                zip.closeEntry()
            }
            if (padding > 0) {
                // Simulates an archive comment region occupying the tail.
                repeat(padding) { i -> zip.putNextEntry(ZipEntry("pad-$i.bin")); zip.closeEntry() }
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `counts a small archive`() {
        val bytes = zipOf(3)
        assertEquals(3, ZipTail.entryCount(bytes))
    }

    @Test
    fun `counts a 76-file archive - the size that killed the push`() {
        val bytes = zipOf(76)
        assertEquals(76, ZipTail.entryCount(bytes))
    }

    @Test
    fun `counts a large archive`() {
        val bytes = zipOf(500)
        assertEquals(500, ZipTail.entryCount(bytes))
    }

    /** The production path only feeds the tail, so the tail alone must be enough. */
    @Test
    fun `works when only the tail is supplied`() {
        val full = zipOf(120)
        val tail = full.copyOfRange((full.size - ZipTail.TAIL_BYTES).coerceAtLeast(0), full.size)
        assertEquals(120, ZipTail.entryCount(tail))
    }

    @Test
    fun `returns zero for a non-zip`() {
        assertEquals(0, ZipTail.entryCount("this is definitely not a zip file".toByteArray()))
    }

    @Test
    fun `returns zero for input shorter than a record`() {
        assertEquals(0, ZipTail.entryCount(byteArrayOf(0x50, 0x4b, 0x05, 0x06)))
        assertEquals(0, ZipTail.entryCount(ByteArray(0)))
    }
}
