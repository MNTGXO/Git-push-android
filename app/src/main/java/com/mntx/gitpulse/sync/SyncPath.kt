package com.mntx.gitpulse.sync

/**
 * Pure helpers for mapping an upload onto a repository.
 *
 * The product needs two shapes of the same idea:
 *  - **mirror a subtree**: "this zip is my `docs/` folder" → every local path is re-rooted
 *    under [subPath] and pruning only applies inside that subtree (a site zip must never
 *    delete the repo's CI config);
 *  - **mirror the whole repo**: [subPath] is blank, everything is fair game.
 */
object SyncPath {

    /** Normalise a user-typed folder path: trims slashes/dot-segments, rejects escapes. */
    fun normalise(subPath: String): String {
        val parts = subPath.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString("/")
    }

    /** True when [path] lives inside the [subPath] folder (or [subPath] is root). */
    fun isInside(path: String, subPath: String): Boolean {
        val root = normalise(subPath)
        return root.isEmpty() || path == root || path.startsWith("$root/")
    }

    /** Re-roots a local path under the target folder. */
    fun join(subPath: String, path: String): String {
        val root = normalise(subPath)
        return if (root.isEmpty()) path.trimStart('/') else "$root/${path.trimStart('/')}"
    }

    /** Inverse of [join] — strips the folder prefix when displaying a remote path. */
    fun strip(subPath: String, path: String): String {
        val root = normalise(subPath)
        return when {
            root.isEmpty() -> path
            path == root -> ""
            path.startsWith("$root/") -> path.removePrefix("$root/")
            else -> path
        }
    }

    /**
     * Decides whether a remote file should be pruned by a mirror push.
     *
     * @param remotePath path in the repository tree
     * @param subPath the folder this push owns ("" = whole repo)
     * @param localPaths every path being uploaded, *already re-rooted*
     */
    fun shouldPrune(remotePath: String, subPath: String, localPaths: Set<String>): Boolean =
        isInside(remotePath, subPath) && remotePath !in localPaths
}

/**
 * Human name for a SAF document (e.g. "site-build.zip"). Falls back to the last path
 * segment, then to [fallback] — the picker's URI alone never carries a usable name.
 */
fun documentDisplayName(context: android.content.Context, uri: android.net.Uri, fallback: String): String {
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
}
