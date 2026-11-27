package com.mntx.gitpulse.sync

import com.mntx.gitpulse.data.ApiException
import com.mntx.gitpulse.data.GitHubApi
import com.mntx.gitpulse.data.model.BlobDownload
import com.mntx.gitpulse.data.model.CommitAuthor
import com.mntx.gitpulse.data.model.CreateCommitRequest
import com.mntx.gitpulse.data.model.CreateTreeEntry
import com.mntx.gitpulse.data.model.CreateTreeRequest
import com.mntx.gitpulse.data.model.GhTreeEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/* ============================================================================================
 *  SYNC ENGINE
 *  -----------
 *  Push:   local folder ──diff against remote tree──▶ blobs ──▶ tree ──▶ commit ──▶ ref
 *          Files that exist remotely but not locally are *deleted* from the repository
 *          (mirror semantics), controlled per-run by [PushOptions.deleteMissing].
 *
 *  Pull:   remote tree ──▶ blobs (parallel, progress) ──▶ local folder, with conflict
 *          handling (overwrite / skip / keep-both) and zip + share at the end.
 * ========================================================================================== */

sealed interface SyncEvent {
    data class Phase(val label: String) : SyncEvent
    data class Progress(val current: Int, val total: Int, val detail: String = "") : SyncEvent {
        val fraction: Float get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Log(val message: String, val level: LogLevel = LogLevel.INFO) : SyncEvent
    data class Success(val message: String, val commitSha: String? = null, val details: String = "") : SyncEvent
    data class Failure(val message: String, val cause: Throwable? = null) : SyncEvent
    data object Cancelled : SyncEvent
}

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class PushOptions(
    val owner: String,
    val repo: String,
    val branch: String,
    val message: String,
    val deleteMissing: Boolean = true,
    val authorName: String = "",
    val authorEmail: String = "",
    val force: Boolean = false,
    val dryRun: Boolean = false,
    val commitAll: Boolean = false,
)

data class PushResult(
    val commitSha: String,
    val treeSha: String,
    val added: Int,
    val modified: Int,
    val deleted: Int,
    val unchanged: Int,
    val pushedBytes: Long,
)

data class PullOptions(
    val owner: String,
    val repo: String,
    val ref: String,
    val destination: java.io.File,
    val maxConcurrent: Int = 4,
    val onConflict: ConflictPolicy = ConflictPolicy.OVERWRITE,
    val includeHidden: Boolean = true,
)

enum class ConflictPolicy { OVERWRITE, SKIP_EXISTING, KEEP_BOTH }

data class PullResult(val files: Int, val bytes: Long, val dir: java.io.File)

/** Progress of a diff computation (hashing can take a while on big folders). */
data class DiffProgress(val hashed: Int, val total: Int)

class SyncEngine(
    private val api: GitHubApi,
    private val hashCache: HashCache = HashCache(),
) {

    @Volatile private var optionsOwner: String = ""
    @Volatile private var optionsRepo: String = ""

    private val iso: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    /* ------------------------------------------------------------------ read remote state */

    suspend fun remoteTree(owner: String, repo: String, ref: String): Map<String, RemoteBlob> =
        withContext(Dispatchers.IO) {
            val branchSha = api.branch(owner, repo, ref).commit?.sha
                ?: throw ApiException(404, "Branch '$ref' not found in $owner/$repo.")
            val tree = api.tree(owner, repo, branchSha, recursive = true)
            tree.tree.filter { it.isBlob }.associate {
                it.path to RemoteBlob(it.path, it.sha, it.size ?: 0)
            }
        }

    suspend fun headCommit(owner: String, repo: String, ref: String): String =
        api.branch(owner, repo, ref).commit?.sha
            ?: throw ApiException(404, "Branch '$ref' not found.")

    /* ------------------------------------------------------------------ diff */

    fun prepareDiff(
        manifest: LocalManifest,
        remote: Map<String, RemoteBlob>,
        filters: SyncFilters = SyncFilters(),
        hashingParallelism: Int = 4,
    ): Flow<DiffSummary> = flow {
        val candidates = manifest.files.filter { filters.shouldSkip(it.path, it.size) == null }
        val total = candidates.size
        val hashedFiles = java.util.Collections.synchronizedList(ArrayList<LocalFile>(candidates.size))
        val skipped = java.util.Collections.synchronizedList(manifest.files.mapNotNull { f ->
            filters.shouldSkip(f.path, f.size)?.let { SkippedFile(f.path, it) }
        }.toMutableList())
        val semaphore = Semaphore(hashingParallelism.coerceAtLeast(1))
        val progress = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            for (file in candidates) {
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        ensureActive()
                        val cacheKey = "${manifest.rootName}/${file.path}"
                        val sha = hashCache.get(cacheKey, file.size, file.lastModified) ?: runCatching {
                            file.open().use { GitHashing.walk(it) }.first
                        }.getOrNull()
                        if (sha != null) {
                            hashCache.put(cacheKey, file.size, sha)
                            hashedFiles += VerifiedLocalFile(file, sha)
                        } else {
                            skipped += SkippedFile(file.path, "unreadable")
                        }
                        progress.incrementAndGet()
                    }
                }
            }
        }

        val verified = LocalManifest(
            rootName = manifest.rootName,
            files = hashedFiles.toList(),
            emptyDirs = manifest.emptyDirs,
        )
        emit(DiffEngine.diff(verified, remote, filters).copy(skipped = skipped.distinctBy { it.path }))
    }.flowOn(Dispatchers.Default)

    /* ------------------------------------------------------------------ push */

    fun push(
        manifest: LocalManifest,
        options: PushOptions,
        filters: SyncFilters = SyncFilters(),
        progressConsumer: (DiffProgress) -> Unit = {},
    ): Flow<SyncEvent> = flow {
        try {
            emit(SyncEvent.Phase("Reading repository ${options.owner}/${options.repo}"))
            var remote: Map<String, RemoteBlob> = emptyMap()
            var remoteBranchExists = true
            val headSha = try {
                headCommit(options.owner, options.repo, options.branch)
            } catch (e: ApiException) {
                if (e.isNotFound) {
                    remoteBranchExists = false
                    val defaultSha = api.branch(options.owner, options.repo, "HEAD").commit?.sha
                        ?: api.branch(options.owner, options.repo, "main").commit?.sha
                        ?: api.branch(options.owner, options.repo, "master").commit?.sha
                        ?: throw ApiException(404, "Could not resolve a starting commit for ${options.owner}/${options.repo}.")
                    defaultSha
                } else throw e
            }

            if (remoteBranchExists) {
                emit(SyncEvent.Phase("Comparing ${manifest.files.size} local files with GitHub"))
                remote = remoteTree(options.owner, options.repo, options.branch)
            } else {
                emit(SyncEvent.Log("Branch '${options.branch}' is new — it will be created from the default branch."))
            }

            emit(SyncEvent.Phase("Hashing files locally (git SHA-1)"))
            val verifiedFiles = ArrayList<LocalFile>(manifest.files.size)
            val total = manifest.files.size
            val skipped = mutableListOf<SkippedFile>()
            var processed = 0
            val sem = Semaphore(4)
            val cache = HashMap<String, String>()
            coroutineScope {
                for (file in manifest.files) {
                    filters.shouldSkip(file.path, file.size)?.let { reason ->
                        skipped += SkippedFile(file.path, reason)
                        return@let
                    } ?: Unit
                    if (filters.shouldSkip(file.path, file.size) != null) continue
                    kotlinx.coroutines.launch(Dispatchers.IO) {
                        sem.withPermit {
                            ensureActive()
                            val cacheKey = "${options.owner}/${options.repo}:${file.path}"
                            val sha = hashCache.get(cacheKey, file.size, file.lastModified) ?: runCatching {
                                file.open().use { GitHashing.walk(it) }.first
                            }.getOrNull()
                            if (sha != null) {
                                hashCache.put(cacheKey, file.size, sha)
                                synchronized(verifiedFiles) { verifiedFiles += VerifiedLocalFile(file, sha) }
                                synchronized(cache) { cache[file.path] = sha }
                            } else {
                                synchronized(skipped) { skipped += SkippedFile(file.path, "unreadable") }
                            }
                            val done = synchronized(cache) { ++processed }
                            progressConsumer(DiffProgress(done, total))
                            if (done % 10 == 0 || done == total) {
                                emit(SyncEvent.Progress(done, total, file.name))
                            }
                        }
                    }
                }
            }

            val verified = LocalManifest(manifest.rootName, verifiedFiles, manifest.emptyDirs)
            val diff = DiffEngine.diff(verified, remote, filters).copy(
                skipped = skipped.distinctBy { it.path }
            )

            emit(SyncEvent.Log(
                "Plan: ${diff.added.size} new · ${diff.modified.size} changed · " +
                    "${diff.deleted.size} deleted · ${diff.unchanged} unchanged",
                LogLevel.INFO
            ))

            if (options.dryRun) {
                emit(SyncEvent.Success("Dry run — nothing was pushed.", details = diff.asMarkdown()))
                return@flow
            }
            if (diff.isEmpty && options.deleteMissing) {
                emit(SyncEvent.Success("Already up to date — nothing to push.", details = diff.asMarkdown()))
                return@flow
            }
            if (diff.isEmpty) {
                emit(SyncEvent.Success("Already up to date — nothing to push.", details = diff.asMarkdown()))
                return@flow
            }

            /* ---- 1. upload blobs (parallel) ---- */
            val uploads = diff.added + diff.modified
            emit(SyncEvent.Phase("Uploading ${uploads.size} blob(s) · ${humanSize(diff.uploadBytes)}"))
            val blobShas = HashMap<String, String>(uploads.size * 2)
            var uploaded = 0
            val uploadSem = Semaphore(3)
            coroutineScope {
                for (change in uploads) {
                    kotlinx.coroutines.launch(Dispatchers.IO) {
                        uploadSem.withPermit {
                            ensureActive()
                            val local = change.local ?: return@withPermit
                            val sha = change.localSha ?: run {
                                val hashed = local.open().use { GitHashing.hashAndEncode(it, local.size) }
                                api.createBlob(options.owner, options.repo, hashed.base64)
                            }
                            synchronized(blobShas) {
                                blobShas[local.path] = sha
                                uploaded++
                            }
                            emit(SyncEvent.Progress(uploaded, uploads.size, local.name))
                        }
                    }
                }
            }

            /* ---- 2. build the new tree ---- */
            emit(SyncEvent.Phase("Building tree"))
            val entries = ArrayList<CreateTreeEntry>(uploads.size + diff.deleted.size)
            blobShas.forEach { (path, sha) -> entries += CreateTreeEntry.blob(path, sha) }
            if (options.deleteMissing) {
                // `sha = null` removes the path from the base tree (GitHub git-data semantics).
                diff.deleted.forEach { entries += CreateTreeEntry(path = it.path, mode = "100644", type = "blob", sha = null) }
            }
            val baseTreeSha = if (remoteBranchExists) {
                remoteTreeSha(options.owner, options.repo, options.branch)
            } else null
            val newTreeSha = api.createTree(
                options.owner, options.repo,
                CreateTreeRequest(baseTree = baseTreeSha, tree = entries)
            )

            /* ---- 3. commit ---- */
            emit(SyncEvent.Phase("Creating commit"))
            val author = CommitAuthor(
                name = options.authorName.ifBlank { "GitPulse" },
                email = options.authorEmail.ifBlank { "gitpulse@users.noreply.github.com" },
                date = iso.format(Instant.now()),
            )
            val message = buildCommitMessage(options.message, diff)
            val commit = api.createCommit(
                options.owner, options.repo,
                CreateCommitRequest(
                    message = message,
                    tree = newTreeSha,
                    parents = if (remoteBranchExists) listOf(headSha) else emptyList(),
                    author = author,
                )
            )

            /* ---- 4. move the branch ---- */
            emit(SyncEvent.Phase("Updating branch ${options.branch}"))
            api.pushCommit(
                owner = options.owner,
                name = options.repo,
                branch = options.branch,
                commitSha = commit.sha,
                force = options.force,
                createIfMissing = true,
            )

            val result = PushResult(
                commitSha = commit.sha,
                treeSha = newTreeSha,
                added = diff.added.size,
                modified = diff.modified.size,
                deleted = if (options.deleteMissing) diff.deleted.size else 0,
                unchanged = diff.unchanged,
                pushedBytes = diff.uploadBytes,
            )
            emit(SyncEvent.Success(
                message = "Pushed ${result.added + result.modified} file(s) to ${options.branch} · ${result.deleted} deleted",
                commitSha = result.commitSha,
                details = buildString {
                    appendLine("Commit `" + result.commitSha.take(7) + "` on `" + options.branch + "`")
                    appendLine()
                    append(diff.asMarkdown())
                },
            ))
        } catch (e: CancellationException) {
            emit(SyncEvent.Cancelled)
            throw e
        } catch (e: Exception) {
            emit(SyncEvent.Failure(friendly(e), e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun remoteTreeSha(owner: String, repo: String, branch: String): String =
        api.commitTreeSha(owner, repo, headCommit(owner, repo, branch))

    private fun buildCommitMessage(userMessage: String, diff: DiffSummary): String = buildString {
        appendLine(userMessage.ifBlank { "chore: sync from GitPulse" })
        appendLine()
        appendLine("GitPulse sync — ${diff.added.size} added, ${diff.modified.size} modified, ${diff.deleted.size} deleted")
        appendLine()
        append("Generated with GitPulse · https://github.com/MNTGXO/Git-push-android")
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> when {
            e.isRateLimited -> "GitHub rate limit reached. Try again in a few minutes."
            e.isAuth -> "Your GitHub token expired or was revoked. Sign in again."
            e.isConflict -> "The branch moved while pushing. Pull the latest changes and retry."
            else -> e.message ?: "GitHub error"
        }
        is java.io.IOException -> e.message ?: "Network error"
        else -> e.message ?: e.javaClass.simpleName
    }

    /* ------------------------------------------------------------------ pull / clone */

    fun pull(options: PullOptions): Flow<SyncEvent> = flow {
        optionsOwner = options.owner
        optionsRepo = options.repo
        try {
            emit(SyncEvent.Phase("Reading ${options.owner}/${options.repo}@${options.ref}"))
            val sha = headCommit(options.owner, options.repo, options.ref)
            val tree = api.tree(options.owner, options.repo, sha, recursive = true)
            val blobs = tree.tree.filter { it.isBlob }
            if (blobs.isEmpty()) {
                emit(SyncEvent.Success("Repository is empty.", commitSha = sha))
                return@flow
            }
            options.destination.mkdirs()
            emit(SyncEvent.Phase("Downloading ${blobs.size} files · ${humanSize(blobs.sumOf { it.size ?: 0 })}"))

            val written = java.util.concurrent.atomic.AtomicInteger(0)
            val bytesOut = java.util.concurrent.atomic.AtomicLong(0)
            val sem = Semaphore(options.maxConcurrent)
            coroutineScope {
                for (blob in blobs) {
                    kotlinx.coroutines.launch(Dispatchers.IO) {
                        sem.withPermit {
                            ensureActive()
                            val target = safeTarget(options.destination, blob.path) ?: run {
                                emit(SyncEvent.Log("Skipped unsafe path ${blob.path}", LogLevel.WARN))
                                return@withPermit
                            }
                            if (!options.includeHidden && blob.path.split('/').any { it.startsWith(".") }) return@withPermit
                            when (options.onConflict) {
                                ConflictPolicy.SKIP_EXISTING -> if (target.exists()) return@withPermit
                                ConflictPolicy.KEEP_BOTH -> if (target.exists()) {
                                    val renamed = uniqueName(target)
                                    download(blob, renamed)
                                    written.incrementAndGet()
                                    return@withPermit
                                }
                                else -> Unit
                            }
                            target.parentFile?.mkdirs()
                            val n = download(blob, target)
                            bytesOut.addAndGet(n)
                            val count = written.incrementAndGet()
                            if (count % 6 == 0 || count == blobs.size) {
                                emit(SyncEvent.Progress(count, blobs.size, blob.path.substringAfterLast('/')))
                            }
                        }
                    }
                }
            }
            emit(SyncEvent.Progress(blobs.size, blobs.size))
            emit(SyncEvent.Success(
                "Downloaded ${written.get()} file(s) · ${humanSize(bytesOut.get())}",
                commitSha = sha,
                details = "Saved to ${options.destination.absolutePath}",
            ))
        } catch (e: CancellationException) {
            emit(SyncEvent.Cancelled); throw e
        } catch (e: Exception) {
            emit(SyncEvent.Failure(friendly(e), e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun download(blob: GhTreeEntry, target: java.io.File): Long {
        val download = api.blob(optionsOwner, optionsRepo, blob.sha)
        target.outputStream().use { it.write(download.bytes) }
        return download.bytes.size.toLong()
    }

    private fun safeTarget(root: java.io.File, path: String): java.io.File? {
        val cleaned = path.trimStart('/').replace("..", "").replace("\\", "/")
        if (cleaned.isBlank()) return null
        val target = java.io.File(root, cleaned)
        val canonicalRoot = root.canonicalPath
        val canonicalTarget = target.canonicalPath
        return if (canonicalTarget.startsWith(canonicalRoot)) target else null
    }

    private fun uniqueName(target: java.io.File): java.io.File {
        val base = target.nameWithoutExtension
        val ext = target.extension
        var i = 1
        while (true) {
            val candidate = java.io.File(
                target.parentFile,
                if (ext.isBlank()) "$base (pull $i)" else "$base (pull $i).$ext"
            )
            if (!candidate.exists()) return candidate
            i++
        }
    }
}

/** A LocalFile whose git blob SHA was already computed. */
class VerifiedLocalFile(delegate: LocalFile, val vcsSha: String) : LocalFile(
    path = delegate.path,
    size = delegate.size,
    lastModified = delegate.lastModified,
    opener = { delegate.open() },
) {
    val vcsShaValue: String get() = vcsSha
}

/** Convenience accessor used by DiffEngine (works for VerifiedLocalFile, null otherwise). */
val LocalFile.vcsSha: String? get() = (this as? VerifiedLocalFile)?.vcsSha
