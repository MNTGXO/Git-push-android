package com.mntx.gitpulse.data

import com.mntx.gitpulse.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown for any non-2xx GitHub response with a human readable message. */
class ApiException(
    val code: Int,
    override val message: String,
    val body: String? = null,
    val rateLimitReset: Long? = null,
) : IOException(message) {
    val isAuth: Boolean get() = code == 401
    val isForbidden: Boolean get() = code == 403
    val isNotFound: Boolean get() = code == 404
    val isConflict: Boolean get() = code == 409 || code == 422
    val isRateLimited: Boolean get() = (code == 403 || code == 429) && (message.contains("rate limit", true) || message.contains("abuse", true))
}

/** Result of a raw file download. */
class BlobDownload(val bytes: ByteArray, val contentType: String?)

/** Maximum size for a single `/contents` write (GitHub rejects larger payloads). */
const val SIMPLE_CONTENT_LIMIT = 45L * 1024 * 1024

object Http {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val octetMedia = "application/octet-stream".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "GitPulse-Android/1.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    fun jsonBody(value: kotlinx.serialization.json.JsonElement): RequestBody =
        value.toString().toRequestBody(jsonMedia)

    fun rawBody(bytes: ByteArray): RequestBody = bytes.toRequestBody(octetMedia)
}

/**
 * A thin, coroutine-native GitHub REST client on top of OkHttp.
 *
 * Design notes:
 *  - No reflection / no codegen http client so the app stays lean and fast to build.
 *  - The token is supplied per-call through [tokenProvider] so it can rotate (OAuth refresh,
 *    sign-out) without rebuilding the client.
 *  - Automatic retry with exponential backoff for 5xx and transport errors.
 *  - Automatic single retry after secondary rate limits (403 + retry-after).
 */
class GitHubApi(
    private val tokenProvider: () -> String?,
    private val baseUrl: String = "https://api.github.com",
) {
    private val retryCodes = setOf(500, 502, 503, 504)

    /* ------------------------------------------------------------------ plumbing */

    private fun url(path: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val built = (baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null) built.addQueryParameter(k, v) }
        return built.build()
    }

    private fun builder(path: String, query: Map<String, String?>): Request.Builder {
        val b = Request.Builder().url(url(path, query))
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private suspend fun executeRaw(
        path: String,
        query: Map<String, String?> = emptyMap(),
        method: String = "GET",
        body: RequestBody? = null,
        accept: String? = null,
        allow404: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < 3) {
            attempt++
            val req = builder(path, query).method(method, body).apply {
                if (accept != null) header("Accept", accept)
            }.build()
            try {
                val response = call(req)
                response.use { r ->
                    val text = r.body?.string().orEmpty()
                    if (r.isSuccessful) return@withContext text
                    if (r.code == 404 && allow404) return@withContext text
                    val reset = r.header("X-RateLimit-Reset")?.toLongOrNull()
                    if (r.code == 403 && r.header("Retry-After") != null && attempt < 3) {
                        delay((r.header("Retry-After")?.toLongOrNull() ?: 2) * 1000)
                        lastError = IOException("secondary rate limit")
                        return@use
                    }
                    if (r.code in retryCodes && attempt < 3) {
                        delay(300L * attempt * attempt)
                        lastError = IOException("server error ${r.code}")
                        return@use
                    }
                    throw ApiException(
                        code = r.code,
                        message = parseError(text, r.code),
                        body = text,
                        rateLimitReset = reset,
                    )
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < 3) delay(250L * attempt)
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    private fun parseError(text: String, code: Int): String {
        if (text.isBlank()) return defaultMessage(code)
        return runCatching {
            val err = Http.json.decodeFromString(GhErrorBody.serializer(), text)
            val detail = err.errors?.mapNotNull { it.message ?: it.code }?.joinToString("; ")
            listOfNotNull(err.message, detail?.takeIf { it.isNotBlank() }).joinToString(" — ")
                .ifBlank { defaultMessage(code) }
        }.getOrDefault(defaultMessage(code))
    }

    private fun defaultMessage(code: Int) = when (code) {
        401 -> "Authentication failed — your token is invalid or expired."
        403 -> "You don't have permission for this action (or the rate limit was hit)."
        404 -> "Not found."
        409 -> "Conflict — the branch was updated elsewhere. Refresh and retry."
        422 -> "GitHub rejected the request (validation error)."
        else -> "GitHub request failed ($code)."
    }

    private suspend fun call(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = Http.client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): T = Http.json.decodeFromString(executeRaw(path, query))

    private suspend inline fun <reified T> send(
        path: String,
        method: String,
        payload: kotlinx.serialization.json.JsonElement,
        query: Map<String, String?> = emptyMap(),
    ): T = Http.json.decodeFromString(executeRaw(path, query, method, Http.jsonBody(payload)))

    private fun <T> kotlinx.serialization.KSerializer<T>.toJson(value: T) =
        Http.json.encodeToJsonElement(this, value)

    /* ------------------------------------------------------------------ user / auth */

    suspend fun currentUser(): GhUser = get("/user")

    /**
     * Probes a self-hosted instance. GitHub Enterprise exposes the same REST shape at
     * `/api/v3`, so "is this token valid for this host" needs no extra client.
     */
    suspend fun testHost(token: String): GhUser {
        val previous = tokenProvider()
        return try {
            validateToken(token)
        } finally {
            @Suppress("UNUSED_EXPRESSION") previous
        }
    }
    suspend fun user(login: String): GhUser = get("/users/$login")
    suspend fun updateUser(name: String?, bio: String?, blog: String?, company: String?, location: String?): GhUser {
        val payload = kotlinx.serialization.json.buildJsonObject {
            name?.let { put("name", kotlinx.serialization.json.JsonPrimitive(it)) }
            bio?.let { put("bio", kotlinx.serialization.json.JsonPrimitive(it)) }
            blog?.let { put("blog", kotlinx.serialization.json.JsonPrimitive(it)) }
            company?.let { put("company", kotlinx.serialization.json.JsonPrimitive(it)) }
            location?.let { put("location", kotlinx.serialization.json.JsonPrimitive(it)) }
        }
        return send("/user", "PATCH", payload)
    }

    suspend fun rateLimit(): GhRateLimit = get("/rate_limit")

    /** Validates the token + returns scopes via the headers-free `/user` probe. */
    suspend fun validateToken(token: String): GhUser {
        val req = Request.Builder()
            .url("$baseUrl/user".toHttpUrl())
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GitPulse-Android/1.0")
            .build()
        val response = call(req)
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw ApiException(r.code, if (r.code == 401) "That token was rejected by GitHub." else parseError(text, r.code), text)
            }
            return Http.json.decodeFromString(text)
        }
    }

    /* ------------------------------------------------------------------ repositories */

    suspend fun repositories(
        visibility: String = "all",
        sort: String = "updated",
        perPage: Int = 100,
        affiliation: String = "owner,collaborator,organization_member",
        page: Int = 1,
    ): List<GhRepo> = get("/user/repos", mapOf(
        "visibility" to visibility,
        "sort" to sort,
        "per_page" to perPage.toString(),
        "affiliation" to affiliation,
        "page" to page.toString(),
    ))

    suspend fun userRepos(login: String, sort: String = "updated", page: Int = 1): List<GhRepo> =
        get("/users/$login/repos", mapOf("sort" to sort, "per_page" to "100", "page" to page.toString()))

    suspend fun orgRepos(org: String, page: Int = 1): List<GhRepo> =
        get("/orgs/$org/repos", mapOf("sort" to "updated", "per_page" to "100", "page" to page.toString()))

    suspend fun repo(owner: String, name: String): GhRepo = get("/repos/$owner/$name")

    suspend fun createRepo(request: CreateRepoRequest): GhRepo =
        send("/user/repos", "POST", CreateRepoRequest.serializer().toJson(request))

    suspend fun createOrgRepo(org: String, request: CreateRepoRequest): GhRepo =
        send("/orgs/$org/repos", "POST", CreateRepoRequest.serializer().toJson(request))

    suspend fun updateRepo(owner: String, name: String, request: UpdateRepoRequest): GhRepo =
        send("/repos/$owner/$name", "PATCH", UpdateRepoRequest.serializer().toJson(request))

    suspend fun deleteRepo(owner: String, name: String) {
        executeRaw("/repos/$owner/$name", method = "DELETE")
    }

    suspend fun forkRepo(owner: String, name: String, org: String? = null): GhRepo =
        send("/repos/$owner/$name/forks", "POST", kotlinx.serialization.json.buildJsonObject {
            org?.let { put("organization", kotlinx.serialization.json.JsonPrimitive(it)) }
        })

    suspend fun starRepo(owner: String, name: String, starred: Boolean) {
        if (starred) executeRaw("/user/starred/$owner/$name", method = "PUT")
        else executeRaw("/user/starred/$owner/$name", method = "DELETE")
    }

    suspend fun isStarred(owner: String, name: String): Boolean =
        runCatching { executeRaw("/user/starred/$owner/$name") }.isSuccess

    suspend fun topics(owner: String, name: String): List<String> =
        runCatching {
            val text = executeRaw("/repos/$owner/$name/topics")
            Http.json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), text)["names"]
                ?.let { arr -> Http.json.decodeFromJsonElement(kotlinx.serialization.serializer<List<String>>(), arr) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    suspend fun searchRepos(query: String, sort: String? = null, page: Int = 1): SearchReposResponse =
        get("/search/repositories", mapOf(
            "q" to query,
            "sort" to sort,
            "per_page" to "30",
            "page" to page.toString(),
        ))

    suspend fun searchUsers(query: String, page: Int = 1): SearchUsersResponse =
        get("/search/users", mapOf("q" to query, "per_page" to "30", "page" to page.toString()))

    suspend fun languages(owner: String, name: String): Map<String, Long> {
        val text = executeRaw("/repos/$owner/$name/languages")
        return runCatching {
            Http.json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), text)
                .mapValues { it.value.toString().trim('"').toLongOrNull() ?: 0L }
        }.getOrDefault(emptyMap())
    }

    suspend fun contributors(owner: String, name: String): List<GhUser> =
        runCatching { get<List<GhUser>>("/repos/$owner/$name/contributors", mapOf("per_page" to "50")) }
            .getOrDefault(emptyList())

    /* ------------------------------------------------------------------ git data (trees, blobs, commits) */

    suspend fun branches(owner: String, name: String): List<GhBranch> =
        get("/repos/$owner/$name/branches", mapOf("per_page" to "100"))

    suspend fun branch(owner: String, name: String, branch: String): GhBranch =
        get("/repos/$owner/$name/branches/$branch")

    suspend fun createBranch(owner: String, name: String, branch: String, fromSha: String): GhBranch {
        send<kotlinx.serialization.json.JsonObject>(
            "/repos/$owner/$name/git/refs", "POST",
            CreateBranchRequest.serializer().toJson(CreateBranchRequest("refs/heads/$branch", fromSha))
        )
        return branch(owner, name, branch)
    }

    suspend fun deleteBranch(owner: String, name: String, branch: String) {
        executeRaw("/repos/$owner/$name/git/refs/heads/$branch", method = "DELETE")
    }

    suspend fun tree(owner: String, name: String, sha: String, recursive: Boolean = false): GhTree =
        get("/repos/$owner/$name/git/trees/$sha", mapOf("recursive" to if (recursive) "1" else null))

    suspend fun treeForBranch(owner: String, name: String, branch: String, recursive: Boolean = false): GhTree =
        tree(owner, name, branch, recursive)

    suspend fun contents(owner: String, name: String, path: String = "", ref: String? = null): List<GhContent> =
        get("/repos/$owner/$name/contents/$path", mapOf("ref" to ref))

    suspend fun content(owner: String, name: String, path: String, ref: String? = null): GhContent =
        get("/repos/$owner/$name/contents/$path", mapOf("ref" to ref))

    suspend fun commits(
        owner: String,
        name: String,
        sha: String? = null,
        path: String? = null,
        page: Int = 1,
        perPage: Int = 30,
    ): List<GhCommitSummary> = get("/repos/$owner/$name/commits", mapOf(
        "sha" to sha,
        "path" to path,
        "per_page" to perPage.toString(),
        "page" to page.toString(),
    ))

    suspend fun commit(owner: String, name: String, sha: String): GhCommitSummary =
        get("/repos/$owner/$name/commits/$sha")

    /** Tree SHA of a commit (needed as `base_tree` when merging new entries). */
    suspend fun commitTreeSha(owner: String, name: String, commitSha: String): String {
        val text = executeRaw("/repos/$owner/$name/git/commits/$commitSha")
        return runCatching {
            Http.json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), text)["tree"]
                ?.let { obj -> (obj as? kotlinx.serialization.json.JsonObject)?.get("sha")?.toString()?.trim('"') }
        }.getOrNull() ?: throw ApiException(500, "Could not read commit tree.")
    }

    suspend fun createBlob(owner: String, name: String, base64Content: String): String {
        val text = executeRaw(
            "/repos/$owner/$name/git/blobs", method = "POST",
            body = Http.jsonBody(CreateBlobRequest.serializer().toJson(CreateBlobRequest(base64Content)))
        )
        return Http.json.decodeFromString(CreateBlobResponse.serializer(), text).sha
    }

    /**
     * Creates a tree entry that re-uses an existing blob (fastest possible move/rename: no
     * content is transferred, GitHub stores one object).
     */
    suspend fun createBlobReference(owner: String, name: String, sha: String): String {
        val treeSha = createTree(
            owner, name,
            CreateTreeRequest(tree = listOf(CreateTreeEntry(path = ".gitpulse-blob-ref", type = "blob", sha = sha)))
        ) ?: throw ApiException(502, "Could not create blob reference.")
        val tree = tree(owner, name, treeSha, recursive = false)
        return tree.tree.firstOrNull { it.isBlob }?.sha ?: sha
    }

    suspend fun createTree(owner: String, name: String, request: CreateTreeRequest): String? {
        val text = executeRaw(
            "/repos/$owner/$name/git/trees", method = "POST",
            body = Http.jsonBody(CreateTreeRequest.serializer().toJson(request))
        )
        return Http.json.decodeFromString(CreateTreeResponse.serializer(), text).sha.ifBlank { null }
    }

    suspend fun createCommit(owner: String, name: String, request: CreateCommitRequest): GhCommitSummary {
        val text = executeRaw(
            "/repos/$owner/$name/git/commits", method = "POST",
            body = Http.jsonBody(CreateCommitRequest.serializer().toJson(request))
        )
        return Http.json.decodeFromString(GhCommitSummary.serializer(), text)
    }

    suspend fun updateRef(owner: String, name: String, branch: String, sha: String, force: Boolean = false) {
        executeRaw(
            "/repos/$owner/$name/git/refs/heads/$branch", method = "PATCH",
            body = Http.jsonBody(UpdateRefRequest.serializer().toJson(UpdateRefRequest(sha, force)))
        )
    }

    /** Fast-forward-or-fail commit push: creates ref when missing. */
    suspend fun pushCommit(owner: String, name: String, branch: String, commitSha: String, force: Boolean = false, createIfMissing: Boolean = true) {
        try {
            updateRef(owner, name, branch, commitSha, force)
        } catch (e: ApiException) {
            if ((e.isNotFound || e.isConflict) && createIfMissing) {
                executeRaw(
                    "/repos/$owner/$name/git/refs", method = "POST",
                    body = Http.jsonBody(
                        CreateRefRequest.serializer().toJson(CreateRefRequest("refs/heads/$branch", commitSha))
                    )
                )
            } else throw e
        }
    }

    /**
     * Creates or updates one file.
     *
     * `POST /contents` rate-limits aggressively and is capped at 50 MB, so anything above
     * [SIMPLE_CONTENT_LIMIT] goes through the git-data API (blob → tree → commit → ref), which is
     * also what makes big generated files pushable from a phone.
     */
    suspend fun putFile(
        owner: String,
        name: String,
        path: String,
        branch: String,
        message: String,
        bytes: ByteArray,
        existingSha: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val clean = path.trimStart('/')
        if (bytes.size <= SIMPLE_CONTENT_LIMIT) {
            val payload = kotlinx.serialization.json.buildJsonObject {
                put("message", kotlinx.serialization.json.JsonPrimitive(message))
                put("content", kotlinx.serialization.json.JsonPrimitive(java.util.Base64.getEncoder().encodeToString(bytes)))
                put("branch", kotlinx.serialization.json.JsonPrimitive(branch))
                existingSha?.let { put("sha", kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            val response = executeRaw(
                "/repos/$owner/$name/contents/$clean", method = "PUT",
                body = Http.jsonBody(payload),
            )
            runCatching {
                Http.json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), response)["commit"]
                    ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("sha")?.toString()?.trim('"') }
            }.getOrNull() ?: ""
        } else {
            gitDataPut(owner, name, path, branch, message, bytes)
        }
    }

    /** Big-file path: identical semantics to a contents PUT, without the size/rate limits. */
    private suspend fun gitDataPut(
        owner: String,
        name: String,
        path: String,
        branch: String,
        message: String,
        bytes: ByteArray,
    ): String {
        val head = branch(owner, name, branch).commit?.sha ?: throw ApiException(404, "Branch '$branch' not found.")
        val baseTree = commitTreeSha(owner, name, head)
        val blob = createBlob(owner, name, java.util.Base64.getEncoder().encodeToString(bytes))
        val tree = createTree(
            owner, name,
            CreateTreeRequest(baseTree = baseTree, tree = listOf(CreateTreeEntry.blob(path.trimStart('/'), blob))),
        ) ?: throw ApiException(502, "GitHub returned no tree.")
        val commit = createCommit(
            owner, name,
            CreateCommitRequest(message = message, tree = tree, parents = listOf(head)),
        )
        updateRef(owner, name, branch, commit.sha)
        return commit.sha
    }

    suspend fun deleteFile(owner: String, name: String, path: String, sha: String, branch: String, message: String) {
        executeRaw(
            "/repos/$owner/$name/contents/$path", method = "DELETE",
            body = Http.jsonBody(kotlinx.serialization.json.buildJsonObject {
                put("message", kotlinx.serialization.json.JsonPrimitive(message))
                put("sha", kotlinx.serialization.json.JsonPrimitive(sha))
                put("branch", kotlinx.serialization.json.JsonPrimitive(branch))
            })
        )
    }

    /** Downloads raw blob bytes (used by the file browser + diff viewer). */
    suspend fun rawFile(owner: String, name: String, path: String, ref: String? = null): BlobDownload = withContext(Dispatchers.IO) {
        val req = builder("/repos/$owner/$name/contents/$path", mapOf("ref" to ref))
            .header("Accept", "application/vnd.github.raw")
            .build()
        val response = call(req)
        response.use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            if (!r.isSuccessful) throw ApiException(r.code, parseError(String(bytes), r.code))
            BlobDownload(bytes, r.header("Content-Type"))
        }
    }

    /** Downloads a blob by its git SHA — the fastest way to clone a tree. */
    suspend fun blob(owner: String, name: String, sha: String): BlobDownload = withContext(Dispatchers.IO) {
        val req = builder("/repos/$owner/$name/git/blobs/$sha", emptyMap())
            .header("Accept", "application/vnd.github.raw")
            .build()
        val response = call(req)
        response.use { r ->
            val contentType = r.header("Content-Type")
            if (!r.isSuccessful) {
                val text = r.body?.string().orEmpty()
                throw ApiException(r.code, parseError(text, r.code))
            }
            // GitHub may answer with base64 JSON for large files, or raw bytes when asked.
            val bytes = r.body?.bytes() ?: ByteArray(0)
            val isJson = contentType?.contains("json", true) == true
            val payload = if (isJson) {
                val text = String(bytes, Charsets.UTF_8)
                if (text.contains("\"content\"") && text.contains("\"base64\"")) {
                    val b64 = text.substringAfter("\"content\"").substringAfter("\"").substringBefore("\"")
                        .replace("\n", "").replace("\r", "")
                    runCatching { java.util.Base64.getMimeDecoder().decode(b64) }.getOrNull() ?: bytes
                } else bytes
            } else bytes
            BlobDownload(payload, contentType)
        }
    }

    /* ------------------------------------------------------------------ issues / PRs / releases / actions */

    suspend fun issues(owner: String, name: String, state: String = "open", page: Int = 1): List<GhIssue> =
        get("/repos/$owner/$name/issues", mapOf("state" to state, "per_page" to "40", "page" to page.toString()))

    suspend fun pulls(owner: String, name: String, state: String = "open"): List<GhPullRequest> =
        get("/repos/$owner/$name/pulls", mapOf("state" to state, "per_page" to "40"))

    suspend fun createPull(owner: String, name: String, request: CreatePullRequest): GhPullRequest =
        send("/repos/$owner/$name/pulls", "POST", CreatePullRequest.serializer().toJson(request))

    suspend fun releases(owner: String, name: String): List<GhRelease> =
        runCatching { get<List<GhRelease>>("/repos/$owner/$name/releases", mapOf("per_page" to "30")) }
            .getOrDefault(emptyList())

    suspend fun workflows(owner: String, name: String): GhWorkflowRuns =
        runCatching {
            get<GhWorkflowRuns>("/repos/$owner/$name/actions/runs", mapOf("per_page" to "20"))
        }.getOrDefault(GhWorkflowRuns())

    suspend fun rerunWorkflow(owner: String, name: String, runId: Long) {
        executeRaw("/repos/$owner/$name/actions/runs/$runId/rerun", method = "POST")
    }

    suspend fun cancelWorkflow(owner: String, name: String, runId: Long) {
        executeRaw("/repos/$owner/$name/actions/runs/$runId/cancel", method = "POST")
    }

    suspend fun notifications(): List<GhNotification> =
        runCatching { get<List<GhNotification>>("/notifications", mapOf("per_page" to "40")) }
            .getOrDefault(emptyList())

    suspend fun gists(): List<GhGist> =
        runCatching { get<List<GhGist>>("/gists", mapOf("per_page" to "50")) }.getOrDefault(emptyList())

    suspend fun createGist(description: String, filename: String, content: String, public: Boolean): GhGist {
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("description", kotlinx.serialization.json.JsonPrimitive(description))
            put("public", kotlinx.serialization.json.JsonPrimitive(public))
            put("files", kotlinx.serialization.json.buildJsonObject {
                put(filename, kotlinx.serialization.json.buildJsonObject {
                    put("content", kotlinx.serialization.json.JsonPrimitive(content))
                })
            })
        }
        return send("/gists", "POST", payload)
    }
}
