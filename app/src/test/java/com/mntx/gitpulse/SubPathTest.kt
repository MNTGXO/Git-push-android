package com.mntx.gitpulse

import com.mntx.gitpulse.sync.DiffEngine
import com.mntx.gitpulse.sync.LocalFile
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.sync.RemoteBlob
import com.mntx.gitpulse.sync.SyncPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "upload this folder into docs/" behaviour, including the pruning boundary. */
class SubPathTest {

    private fun file(path: String, size: Long = 10) =
        LocalFile(path, size) { ByteArray(0).inputStream() }

    @Test
    fun normaliseStripsSlashesAndEscapes() {
        assertEquals("docs/api", SyncPath.normalise("/docs//api/"))
        assertEquals("docs/evil", SyncPath.normalise("docs/../evil/"))
        assertEquals("", SyncPath.normalise("   "))
    }

    @Test
    fun joinAndStripRoundTrip() {
        assertEquals("docs/index.html", SyncPath.join("docs", "index.html"))
        assertEquals("index.html", SyncPath.join("", "index.html"))
        assertEquals("index.html", SyncPath.strip("docs", "docs/index.html"))
        assertEquals("docs", SyncPath.strip("docs", "docs").ifBlank { "docs" })
    }

    @Test
    fun isInsideRespectsSegmentBoundaries() {
        assertTrue(SyncPath.isInside("docs/a.html", "docs"))
        assertFalse("docs2/a.html must not count as inside docs", SyncPath.isInside("docs2/a.html", "docs"))
        assertTrue(SyncPath.isInside("anything", ""))
    }

    @Test
    fun pruningNeverTouchesFilesOutsideTheFolder() {
        val local = setOf("docs/index.html", "docs/logo.png")
        assertTrue(SyncPath.shouldPrune("docs/old.html", "docs", local))
        assertFalse(SyncPath.shouldPrune(".github/workflows/ci.yml", "docs", local))
        assertFalse(SyncPath.shouldPrune("docs2/keep.html", "docs", local))
        assertFalse(SyncPath.shouldPrune("docs/index.html", "docs", local))
    }

    @Test
    fun diffOfReRootedManifestDeletesOnlyInsideFolder() {
        val manifest = LocalManifest("site", listOf(file("index.html", 10), file("app.js", 33)))
        val reRooted = manifest.reRoot("site")
        assertEquals(listOf("site/app.js", "site/index.html"), reRooted.files.map { it.path }.sorted())

        val remote = mapOf(
            // Same size + no local hash => treated as unchanged by the size heuristic.
            "site/index.html" to RemoteBlob("site/index.html", "sha-old", 10),
            "site/gone.html" to RemoteBlob("site/gone.html", "sha-gone", 4),
            "site/page.html" to RemoteBlob("site/page.html", "sha-page", 99),
            "README.md" to RemoteBlob("README.md", "sha-readme", 4),
        )
        val diff = DiffEngine.diff(reRooted, remote)
        assertEquals(listOf("site/gone.html"), diff.deleted.map { it.path })
        assertEquals(listOf("site/app.js"), diff.added.map { it.path })
        assertEquals(listOf("site/page.html"), diff.modified.map { it.path })
    }

    /** The engine narrows the remote tree to the target folder *before* diffing. */
    @Test
    fun scopedRemoteTreeNeverPrunesOutsideTheFolder() {
        val fullRemote = mapOf(
            ".github/workflows/ci.yml" to RemoteBlob(".github/workflows/ci.yml", "ci", 1),
            "docs/guide.md" to RemoteBlob("docs/guide.md", "guide", 1),
            "docs/old.md" to RemoteBlob("docs/old.md", "old", 1),
        )
        val scoped = fullRemote.filterKeys { SyncPath.isInside(it, "docs") }
        val local = LocalManifest("docs", listOf(file("guide.md", 1)))

        val diff = DiffEngine.diff(local, scoped)
        assertEquals(listOf("docs/old.md"), diff.deleted.map { it.path })
    }

    @Test
    fun reRootIsANoOpAtRepositoryRoot() {
        val manifest = LocalManifest("site", listOf(file("a.txt")))
        assertEquals(listOf("a.txt"), manifest.reRoot("").files.map { it.path })
        assertEquals(listOf("a.txt"), manifest.reRoot("/").files.map { it.path })
    }
}
