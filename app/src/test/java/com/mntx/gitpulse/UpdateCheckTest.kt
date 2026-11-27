package com.mntx.gitpulse

import com.mntx.gitpulse.update.UpdateViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater decides "is this newer?" from the release asset name and the installed label.
 * Getting the comparison wrong either nags users forever or silently strands them on an old build.
 */
class UpdateCheckTest {

    @Test
    fun `parses the build stamp out of a published apk name`() {
        assertEquals("20261127" + "0942", UpdateViewModel.stampOf("GitPulse-20261127-0942-8ebe86c.apk"))
    }

    @Test
    fun `a later stamp compares newer`() {
        val installed = UpdateViewModel.stampOf("GitPulse-20261127-0942-abc.apk")!!
        val remote = UpdateViewModel.stampOf("GitPulse-20261128-1015-def.apk")!!
        assertTrue("a build from the next day must win: $installed vs $remote", remote > installed)
    }

    @Test
    fun `a same-or-older stamp does not nag`() {
        val installed = UpdateViewModel.stampOf("GitPulse-20261127-1200-abc.apk")!!
        assertEquals(false, UpdateViewModel.stampOf("GitPulse-20261127-0942-def.apk")!! > installed)
        assertEquals(false, UpdateViewModel.stampOf("GitPulse-20261127-1200-def.apk")!! > installed)
    }

    @Test
    fun `semver-only labels carry no stamp`() {
        // A bare "1.1.0" / "1.0.0-debug" has nothing to order. The updater used to answer
        // "unknown => published release wins", which meant an install carrying such a label was
        // offered an update on every launch, forever, even when it was the newest build.
        assertNull(UpdateViewModel.stampOf("1.1.0"))
        assertNull(UpdateViewModel.stampOf("1.0.0-debug"))
        assertNull(UpdateViewModel.stampOf(null))
    }

    @Test
    fun `an unstamped release name is never treated as newer`() {
        // Cannot rank what has no date: refusing beats nagging with an unverifiable download.
        assertEquals(false, UpdateViewModel.isNewerByVersionCode("GitPulse.apk", "1.1.0"))
        assertEquals(false, UpdateViewModel.isNewerByVersionCode("app-release.apk", "1.1.0"))
        assertEquals(false, UpdateViewModel.isNewerByVersionCode(null, "1.1.0"))
    }

    @Test
    fun `an unstamped install is offered the stamped release`() {
        // A local build predating stamping has nothing to compare; pointing it at the published
        // APK is correct. This is the only branch where "newer" may be assumed.
        assertTrue(UpdateViewModel.isNewerByVersionCode("GitPulse-20261127-1639-abc.apk", "1.1.0"))
        // ...but an already-stamped install must not be nagged by an unrankable pairing.
        assertEquals(
            false,
            UpdateViewModel.isNewerByVersionCode("GitPulse-20261127-1639-abc.apk", "1.1.0+20261127-1639"),
        )
    }

    @Test
    fun `builds are stamped so the comparison has real input`() {
        // Regression guard for the actual defect: APP_VERSION_LABEL used to be the constant
        // "1.1.0", so stampOf() always returned null and every check claimed an update.
        val label = BuildConfig.APP_VERSION_LABEL
        assertNotNull(
            "APP_VERSION_LABEL must carry yyyyMMdd-HHmm, got: $label",
            UpdateViewModel.stampOf(label),
        )
        assertTrue(
            "the release file and the installed label must be comparable, got: $label",
            label.contains("-"),
        )
    }

    @Test
    fun `the version code is a large, monotonic number`() {
        // Android refuses an install whose versionCode does not increase. The old scheme was
        // 1000 + GITHUB_RUN_NUMBER, which restarts at 1 for a fresh checkout and is 0 locally.
        assertTrue(
            "versionCode should encode the build date: ${BuildConfig.VERSION_CODE}",
            BuildConfig.VERSION_CODE > 100_000_000,
        )
    }
}
