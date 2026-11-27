package com.mntx.gitpulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.ui.theme.GitPulseTheme
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.util.MotionFlags
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bisects the screenshot pipeline: the smallest possible Compose tree, then one real screen.
 * A failure here means the harness is broken, not the product.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xhdpi")
class RenderProbeTest : ScreenshotHarness() {

    private val settings = AppSettings()

    @Before
    fun freeze() {
        MotionFlags.freezeInfinite = true
        MotionFlags.instantEntrances = true
    }

    @After
    fun thaw() {
        MotionFlags.freezeInfinite = false
        MotionFlags.instantEntrances = false
    }

    @Test
    fun plainBoxRenders() {
        rule.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(LocalPulse.current.background)) {
                    Text("probe")
                }
            }
        }
        save("probe-plain")
    }

    @Test
    fun signInRenders() {
        rule.setContent {
            GitPulseTheme(settings = settings) {
                com.mntx.gitpulse.ui.auth.SignInScreen(loading = false, error = null, onSignIn = {})
            }
        }
        save("probe-signin")
    }
}
