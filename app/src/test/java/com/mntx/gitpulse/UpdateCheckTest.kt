package com.mntx.gitpulse

import com.mntx.gitpulse.update.UpdateViewModel
import org.junit.Assert.assertEquals
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
        // Local builds report "1.1.0" / "1.0.0-debug"; there is nothing to order, so the check
        // must say "unknown" rather than invent one (the VM then offers the release).
        assertNull(UpdateViewModel.stampOf("1.1.0"))
        assertNull(UpdateViewModel.stampOf("1.0.0-debug"))
        assertNull(UpdateViewModel.stampOf(null))
    }
}
