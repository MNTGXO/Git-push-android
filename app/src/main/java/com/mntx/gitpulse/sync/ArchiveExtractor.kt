package com.mntx.gitpulse.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Safe zip extraction.
 *
 * Handles the realities of "I picked a zip from my Downloads folder":
 *  - **zip-slip** protection (entries escaping the destination are rejected)
 *  - **single root folder collapse** (most zips wrap everything in one dir; we strip it so the
 *    extracted tree maps 1:1 onto the repository root)
 *  - **__MACOSX / .DS_Store** noise removal
 *  - corrupted-entry tolerance (one bad entry never aborts the run)
 *  - entry + size limits so a malicious archive can't fill the device
 */
class ArchiveExtractor {

    data class Result(
        val root: File,
        val files: Int,
        val bytes: Long,
        val skipped: List<String>,
        val collapsedRoot: Boolean,
    )

    suspend fun extract(
        source: InputStream,
        destination: File,
        maxEntries: Int = 20_000,
        maxTotalBytes: Long = 1_500L * 1024 * 1024,
        onProgress: (files: Int, bytes: Long, current: String) -> Unit = { _, _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        val canonicalDest = destination.canonicalPath + File.separator
        var files = 0
        var bytes = 0L
        val skipped = mutableListOf<String>()

        ZipInputStream(source.buffered(64 * 1024)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                ensureActive()
                val name = entry.name
                when {
                    name.startsWith("__MACOSX/") || name.contains("__MACOSX/") -> skipped += name
                    name.endsWith(".DS_Store") -> skipped += name
                    entry.isDirectory -> File(destination, safe(name)).mkdirs()
                    else -> {
                        val target = File(destination, safe(name))
                        if (!target.canonicalPath.startsWith(canonicalDest)) {
                            skipped += "$name (unsafe path)"
                        } else {
                            target.parentFile?.mkdirs()
                            val written = runCatching {
                                FileOutputStream(target).use { out ->
                                    val buffer = ByteArray(128 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read <= 0) break
                                        total += read
                                        out.write(buffer, 0, read)
                                        if (bytes + total > maxTotalBytes) throw IllegalStateException("archive exceeds ${humanSize(maxTotalBytes)}")
                                    }
                                    total
                                }
                            }.getOrElse { e ->
                                skipped += "$name (${e.message ?: "corrupt"})"
                                target.delete()
                                0L
                            }
                            if (written > 0) {
                                bytes += written
                                files++
                                if (files > maxEntries) throw IllegalStateException("Archive has more than $maxEntries files.")
                            }
                            onProgress(files, bytes, name.substringAfterLast('/'))
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val collapsed = collapseSingleRoot(destination)
        Result(destination, files, bytes, skipped.distinct(), collapsed)
    }

    private fun safe(name: String): String {
        val normalised = name.replace('\\', '/').trimStart('/')
        val parts = normalised.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) error("unsafe zip entry")
        return parts.joinToString(File.separator)
    }

    /** If the zip held exactly one top-level directory, lift its contents up one level. */
    private fun collapseSingleRoot(root: File): Boolean {
        val children = root.listFiles() ?: return false
        if (children.size != 1) return false
        val only = children.first()
        if (!only.isDirectory) return false
        val inner = only.listFiles() ?: return false
        if (inner.isEmpty()) return false
        var moved = true
        inner.forEach { child ->
            val target = File(root, child.name)
            if (target.exists()) moved = false
            if (moved) child.renameTo(target)
        }
        if (moved) only.delete()
        return moved
    }

    companion object {
        /** Zip magic numbers: PK\x03\x04 (also PK\x05\x06 empty, PK\x07\x08 spanned). */
        fun looksLikeZip(header: ByteArray): Boolean =
            header.size >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
                (header[2].toInt() == 3 || header[2].toInt() == 5 || header[2].toInt() == 7)
    }
}

/** Builds a [LocalManifest] from a directory tree on disk. */
object ManifestBuilder {

    fun fromDirectory(root: File, includeHidden: Boolean = true, maxFiles: Int = 20_000): LocalManifest {
        val files = mutableListOf<LocalFile>()
        val emptyDirs = mutableListOf<String>()
        val rootName = root.name

        fun walk(dir: File, depth: Int) {
            if (depth > 30 || files.size >= maxFiles) return
            val children = dir.listFiles() ?: return
            if (children.isEmpty() && dir != root) {
                emptyDirs += dir.relativeTo(root).invariantSeparatorsPath
            }
            for (child in children) {
                if (child.isDirectory) {
                    if (!includeHidden && child.name.startsWith(".")) continue
                    walk(child, depth + 1)
                } else if (child.isFile) {
                    if (!includeHidden && child.name.startsWith(".")) continue
                    if (SyncFilters.DEFAULT_EXCLUDES.any { child.name == it }) continue
                    if (child.name in SyncFilters.EXCLUDED_FILES) continue
                    files += LocalFile.fromFile(root, child)
                }
            }
        }
        walk(root, 0)
        return LocalManifest(rootName = rootName, files = files.sortedBy { it.path }, emptyDirs = emptyDirs)
    }

    fun fromFiles(baseName: String, files: List<Pair<String, LocalFile>>): LocalManifest =
        LocalManifest(baseName, files.map { (p, f) -> LocalFile(p, f.size, f.lastModified) { f.open() } })
}
