package com.mntx.gitpulse.sync

import java.io.File

/** A file we can push: knows how to stream its bytes without loading it fully into memory. */
class LocalFile(
    val path: String,
    val size: Long,
    val lastModified: Long = 0,
    private val opener: () -> java.io.InputStream,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    fun open(): java.io.InputStream = opener()

    companion object {
        fun fromFile(root: File, file: File): LocalFile {
            val rel = file.relativeTo(root).invariantSeparatorsPath
            return LocalFile(rel, file.length(), file.lastModified()) { file.inputStream() }
        }
    }
}

/** Everything the user wants to upload, normalised into repo-relative paths. */
data class LocalManifest(
    val rootName: String,
    val files: List<LocalFile>,
    val emptyDirs: List<String> = emptyList(),
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    fun byPath(): Map<String, LocalFile> = files.associateBy { it.path }
}

enum class ChangeKind {
    ADDED, MODIFIED, DELETED, UNCHANGED
}

data class FileChange(
    val path: String,
    val kind: ChangeKind,
    val local: LocalFile? = null,
    val remoteSha: String? = null,
    val remoteSize: Long = 0,
    val localSha: String? = null,
) {
    val bytesDelta: Long get() = local?.size ?: 0
}

data class DiffSummary(
    val added: List<FileChange>,
    val modified: List<FileChange>,
    val deleted: List<FileChange>,
    val unchanged: Int,
    val totalLocalBytes: Long,
    val skipped: List<SkippedFile> = emptyList(),
) {
    val changes: List<FileChange> get() = added + modified + deleted
    val addedBytes: Long get() = added.sumOf { it.local?.size ?: 0 }
    val modifiedBytes: Long get() = modified.sumOf { it.local?.size ?: 0 }
    val uploadBytes: Long get() = addedBytes + modifiedBytes
    val isEmpty: Boolean get() = added.isEmpty() && modified.isEmpty() && deleted.isEmpty()

    fun asMarkdown(): String = buildString {
        appendLine("| Change | File | Size |")
        appendLine("| --- | --- | --- |")
        added.take(400).forEach { appendLine("| 🟢 added | `${it.path}` | ${humanSize(it.local?.size ?: 0)} |") }
        modified.take(400).forEach { appendLine("| 🟡 modified | `${it.path}` | ${humanSize(it.local?.size ?: 0)} |") }
        deleted.take(400).forEach { appendLine("| 🔴 deleted | `${it.path}` | — |") }
        if (unchanged > 0) appendLine()
        if (unchanged > 0) appendLine("_$unchanged unchanged file(s) skipped._")
    }
}

data class SkippedFile(val path: String, val reason: String)

/** Filters applied to every sync (the .gitignore-lite that keeps uploads clean). */
data class SyncFilters(
    val includeJunk: Boolean = false,
    val maxFileBytes: Long = 45L * 1024 * 1024,
    val excludeGlobs: List<String> = DEFAULT_EXCLUDES,
) {
    fun shouldSkip(path: String, size: Long): String? {
        if (size > maxFileBytes) return "exceeds ${humanSize(maxFileBytes)} limit"
        if (size == 0L && path.endsWith(".keep")) return null
        if (includeJunk) return null
        val segments = path.split('/')
        if (segments.any { it in EXCLUDED_DIRS }) return "ignored folder"
        val name = path.substringAfterLast('/')
        if (name in EXCLUDED_FILES) return "ignored file"
        if (name.endsWith(".tmp") || name.endsWith(".part")) return "temporary file"
        return null
    }

    companion object {
        val EXCLUDED_DIRS = setOf(
            ".git", ".svn", ".hg", "node_modules", "__MACOSX", ".gradle", "build", "dist",
            ".idea", ".vscode", ".dart_tool", "Pods", ".next", ".nuxt", "target", "venv",
            ".venv", "__pycache__", ".mypy_cache", ".pytest_cache", "bin", "obj", "xcuserdata",
            ".dSYM",
        )
        val EXCLUDED_FILES = setOf(".DS_Store", "Thumbs.db", "desktop.ini", ".localized")
        val DEFAULT_EXCLUDES = emptyList<String>()
    }
}

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

fun languageColor(ext: String): Long = when (ext) {
    "kt", "kts" -> 0xFF7F52FF
    "java" -> 0xFFB07219
    "py" -> 0xFF3572A5
    "js" -> 0xFFF1E05A
    "ts", "tsx" -> 0xFF3178C6
    "jsx" -> 0xFF61DAFB
    "go" -> 0xFF00ADD8
    "rs" -> 0xFFDEA584
    "rb" -> 0xFF701516
    "php" -> 0xFF4F5D95
    "swift" -> 0xFFF05138
    "c", "h" -> 0xFF555555
    "cpp", "cc", "hpp" -> 0xFFF34B7D
    "cs" -> 0xFF178600
    "html" -> 0xFFE34C26
    "css", "scss" -> 0xFF563D7C
    "json" -> 0xFF8BC34A
    "yml", "yaml" -> 0xFFCB171E
    "md", "markdown" -> 0xFF519ABA
    "xml" -> 0xFFF1662A
    "sh", "bash" -> 0xFF89E051
    "sql" -> 0xFFE38C00
    "dart" -> 0xFF00B4AB
    "lua" -> 0xFF000080
    "toml" -> 0xFF9C4221
    "png", "jpg", "jpeg", "webp", "gif", "svg", "ico" -> 0xFFB07219
    "zip", "apk", "jar" -> 0xFF9AA5B1
    else -> 0xFF8A94A8
}
