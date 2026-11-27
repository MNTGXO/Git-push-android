package com.mntx.gitpulse.sync

import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * Git's object id for a blob is `sha1("blob " + size + "\0" + content)`.
 *
 * Computing that hash locally means we can compare a local folder against the remote tree
 * **without downloading anything** — the tree API already gives us the blob SHAs.
 *
 * Pure-JVM implementation (no `android.util.*`) so it is unit-testable and reusable.
 */
object GitHashing {

    private val URL_SAFE: Base64.Encoder = Base64.getEncoder().withoutPadding()

    fun blobSha(content: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${content.size}\u0000".toByteArray(Charsets.UTF_8))
        md.update(content)
        return md.digest().toHex()
    }

    /** Streams the file once, returning (gitBlobSha, size, base64OfContent). */
    fun hashAndEncode(input: InputStream, size: Long): LocalHash {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
        val out = java.io.ByteArrayOutputStream(if (size in 1..8_000_000) size.toInt() else 64 * 1024)
        val chunk = ByteArray(64 * 1024)
        var actual = 0L
        input.use { stream ->
            while (true) {
                val read = stream.read(chunk)
                if (read == -1) break
                md.update(chunk, 0, read)
                out.write(chunk, 0, read)
                actual += read
            }
        }
        val sha = md.digest().toHex()
        return LocalHash(sha, actual, URL_SAFE.encodeToString(out.toByteArray()))
    }

    /**
     * Computes the git blob SHA for a stream. Small files (the overwhelmingly common case)
     * are buffered so the exact content is hashed; large files are hashed through a temp file
     * so memory stays flat.
     */
    fun walk(input: InputStream): Pair<String, Long> {
        val temp = java.io.File.createTempFile("gitpulse-hash", ".tmp")
        return try {
            var total = 0L
            temp.outputStream().buffered(64 * 1024).use { out ->
                input.use { stream ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read == -1) break
                        out.write(chunk, 0, read)
                        total += read
                    }
                }
            }
            val md = MessageDigest.getInstance("SHA-1")
            md.update("blob $total\u0000".toByteArray(Charsets.UTF_8))
            temp.inputStream().buffered(64 * 1024).use { stream ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    md.update(chunk, 0, read)
                }
            }
            md.digest().toHex() to total
        } finally {
            temp.delete()
        }
    }

    fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

data class LocalHash(val blobSha: String, val size: Long, val base64: String)

/** Diff helpers that operate on path maps. */
object DiffEngine {

    fun diff(
        local: LocalManifest,
        remoteEntries: Map<String, RemoteBlob>,
        filters: SyncFilters = SyncFilters(),
    ): DiffSummary {
        val added = mutableListOf<FileChange>()
        val modified = mutableListOf<FileChange>()
        val deleted = mutableListOf<FileChange>()
        val skipped = mutableListOf<SkippedFile>()
        var unchanged = 0
        val localPaths = HashSet<String>(local.files.size * 2)

        for (file in local.files) {
            localPaths += file.path
            val skipReason = filters.shouldSkip(file.path, file.size)
            if (skipReason != null) {
                skipped += SkippedFile(file.path, skipReason)
                continue
            }
            val remote = remoteEntries[file.path]
            if (remote == null) {
                added += FileChange(file.path, ChangeKind.ADDED, local = file)
                continue
            }
            val localSha = file.vcsSha
            when {
                // Exact git object identity — the strongest possible signal.
                localSha != null && localSha == remote.sha -> unchanged++
                // Never hashed: fall back to a size comparison (verified at upload time).
                localSha == null && remote.size > 0 && remote.size == file.size -> unchanged++
                else -> modified += FileChange(
                    file.path,
                    ChangeKind.MODIFIED,
                    local = file,
                    remoteSha = remote.sha,
                    remoteSize = remote.size,
                    localSha = localSha,
                )
            }
        }

        // Prune only inside the subtree this push owns. Everything under `local`'s root prefix
        // is fair game; anything else in remoteEntries is out of scope (a caller doing a
        // sub-path mirror must never delete the rest of the repository).
        val ownedPrefix = if (local.rootName.isBlank()) "" else local.rootName.trim('/') + "/"
        for ((path, remote) in remoteEntries) {
            val inScope = ownedPrefix.isEmpty() || path.startsWith(ownedPrefix)
            if (inScope && path !in localPaths && path !in local.emptyDirs) {
                deleted += FileChange(path, ChangeKind.DELETED, remoteSha = remote.sha, remoteSize = remote.size)
            }
        }

        return DiffSummary(
            added = added.sortedBy { it.path },
            modified = modified.sortedBy { it.path },
            deleted = deleted.sortedBy { it.path },
            unchanged = unchanged,
            totalLocalBytes = local.totalBytes,
            skipped = skipped,
        )
    }
}

data class RemoteBlob(val path: String, val sha: String, val size: Long)

/** Cache of computed shas, so repeated diffs don't re-hash big folders. */
class HashCache(private val capacity: Int = 4096) {
    private val map = object : LinkedHashMap<String, Pair<Long, String>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>) = size > capacity
    }

    @Synchronized
    fun get(path: String, size: Long, modified: Long): String? {
        val hit = map[path] ?: return null
        return if (hit.first == size) hit.second else null
    }

    @Synchronized
    fun put(path: String, size: Long, sha: String) {
        map[path] = size to sha
    }
}
