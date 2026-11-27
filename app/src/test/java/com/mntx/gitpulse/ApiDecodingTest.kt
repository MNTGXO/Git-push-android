package com.mntx.gitpulse

import com.mntx.gitpulse.data.Http
import com.mntx.gitpulse.data.model.CreateRepoRequest
import com.mntx.gitpulse.data.model.CreateTreeEntry
import com.mntx.gitpulse.data.model.CreateTreeRequest
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.UpdateRepoRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The REST API speaks snake_case; the models are camelCase. Every screenshot the project ever
 * produced used hand-built fixtures, so a missing `namingStrategy` was invisible until a real
 * device decoded a real response and the repo list died with
 * "Field 'fullName' is required for type ... but it was missing at path: $[0]".
 *
 * These tests decode *real GitHub wire shapes* so that can never regress silently.
 */
class ApiDecodingTest {

    /** Trimmed from an actual `GET /user/repos` element. */
    private val reposJson = """
        [
          {
            "id": 361234567,
            "name": "Shobanafilterbot-v2-mnbots",
            "full_name": "MNTGXO/Shobanafilterbot-v2-mnbots",
            "private": false,
            "owner": { "login": "MNTGXO", "id": 42, "avatar_url": "https://x/y.png", "type": "User" },
            "html_url": "https://github.com/MNTGXO/Shobanafilterbot-v2-mnbots",
            "description": "Filter bot",
            "fork": false,
            "default_branch": "main",
            "stargazers_count": 3,
            "watchers_count": 3,
            "forks_count": 0,
            "open_issues_count": 1,
            "size": 5571,
            "visibility": "public",
            "topics": ["bot"],
            "updated_at": "2026-11-27T10:00:00Z"
          }
        ]
    """.trimIndent()

    @Test
    fun `a real repo payload decodes into every camelCase field`() {
        val repos = Http.json.decodeFromString<List<GhRepo>>(reposJson)
        assertEquals(1, repos.size)
        val repo = repos.first()

        assertEquals("MNTGXO/Shobanafilterbot-v2-mnbots", repo.fullName)
        assertEquals("MNTGXO", repo.ownerLogin)
        assertEquals("main", repo.defaultBranch)
        assertEquals(3, repo.stargazersCount)
        assertEquals(3, repo.watchersCount)
        assertEquals(1, repo.openIssuesCount)
        assertEquals(5571L, repo.size)
        assertEquals("https://x/y.png", repo.owner?.avatarUrl)
        assertEquals(listOf("bot"), repo.topics)
        assertTrue(repo.updatedAt != null)
    }

    @Test
    fun `write payloads use the keys GitHub expects`() {
        val create = Http.json.encodeToString(
            CreateRepoRequest.serializer(),
            CreateRepoRequest(name = "x", autoInit = false, gitignoreTemplate = "Android"),
        )
        assertTrue("auto_init, not autoInit: $create", create.contains("\"auto_init\""))
        assertTrue("gitignore_template: $create", create.contains("\"gitignore_template\""))

        val patch = Http.json.encodeToString(
            UpdateRepoRequest.serializer(),
            UpdateRepoRequest(defaultBranch = "trunk"),
        )
        assertTrue("default_branch: $patch", patch.contains("\"default_branch\""))

        val tree = Http.json.encodeToString(
            CreateTreeRequest.serializer(),
            CreateTreeRequest(baseTree = "abc", tree = listOf(CreateTreeEntry.blob("a.txt", "def"))),
        )
        assertTrue("base_tree: $tree", tree.contains("\"base_tree\""))
        // Fields that are already single-word must stay untouched.
        assertTrue(tree.contains("\"path\":\"a.txt\""))
    }

    @Test
    fun `unknown fields never break a decode`() {
        val exotic = """[{"full_name":"a/b","name":"b","brand_new_github_field":{"nested":[1,2,3]}}]"""
        val repo = Http.json.decodeFromString<List<GhRepo>>(exotic).single()
        assertEquals("a/b", repo.fullName)
        assertNull(repo.defaultBranch)
    }

    @Test
    fun `a truly malformed payload still fails loudly`() {
        // The wrapper in GitHubApi turns this into an ApiException with the response attached;
        // here we only assert the strategy didn't paper over a genuinely missing required field.
        val broken = """[{"name":"b"}]"""
        val failed = runCatching { Http.json.decodeFromString<List<GhRepo>>(broken) }.isFailure
        assertTrue("a missing full_name must not silently become empty", failed)
    }
}
