package com.mntx.gitpulse.sync

import android.util.Base64
import java.io.InputStream
import java.security.MessageDigest

/**
 * Git's object id for a blob is `sha1("blob " + size + "\0" + content)`.
 *
 * Computing that hash locally means we can compare a local folder against the remote tree
 * **without downloading anything** — the tree API already gives us the blob SHAs.
 */
object GitHashing {

    fun blobSha(size: Long, digest: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
        md.update(digest)
        return md.digest().toHex()
    }

    fun blobSha(header: String, digestOfContent: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(header.toByteArray(Charsets.UTF_8))
        md.update(digestOfContent)
        return md.digest().toHex()
    }

    /** Streams the file once, returning (gitBlobSha, sha1OfContent, base64Content). */
    fun hashAndEncode(input: InputStream, size: Long): LocalHash {
        val contentDigest = MessageDigest.getInstance("SHA-1")
        val buffer = java.io.ByteArrayOutputStream(if (size in 1..8_000_000) size.toInt() else 65536)
        val chunk = ByteArray(64 * 1024)
        var read: Int
        var actual = 0L
        input.use {
            while (it.read(chunk).also { r -> read = r } != -1) {
                contentDigest.update(chunk, 0, read)
                buffer.write(chunk, 0, read)
                actual += read
            }
        }
        val digest = contentDigest.digest()
        val sha = blobSha(actual, digest)
        val base64 = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING)
        return LocalHash(sha, actual, base64)
    }

    fun walk(input: InputStream): Pair<String, Long> {
        val md = MessageDigest.getInstance("SHA-1")
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        input.use {
            while (true) {
                val r = it.read(chunk)
                if (r == -1) break
                md.update(chunk, 0, r)
                total += r
            }
        }
        return blobSha(total, md.digest()) to total
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
            filters.shouldSkip(file.path, file.size)?.let {
                skipped += SkippedFile(file.path, it)
                return@let
            }
            // Cleaner: re-check because `return@let` above only skips the lambda.
            if (filters.shouldSkip(file.path, file.size) != null) continue
            val remote = remoteEntries[file.path]
            if (remote == null) {
                added += FileChange(file.path, ChangeKind.ADDED, local = file)
            } else {
                val localSha = file.vcsSha
                if (localSha != null && localSha == remote.sha) {
                    unchanged++
                } else if (localSha == null) {
                    // Unknown hash → compare by size, then verify during upload.
                    if (remote.size > 0 && remote.size == file.size) {
                        unchanged++
                    } else {
                        modified += FileChange(file.path, ChangeKind.MODIFIED, local = file, remoteSha = remote.sha, remoteSize = remote.size)
                    }
                } else {
                    modified += FileChange(file.path, ChangeKind.MODIFIED, local = file, remoteSha = remote.sha, remoteSize = remote.size, localSha = localSha)
                }
            }
        }

        for ((path, remote) in remoteEntries) {
            if (path !in localPaths && path !in local.emptyDirs) {
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
