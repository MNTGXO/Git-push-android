package com.mntx.gitpulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.ui.theme.GitPulseTheme
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.util.MotionFlags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bisects the screenshot pipeline: renders the smallest possible Compose tree and captures it.
 * If this fails, the problem is the harness (Robolectric/graphics), not a product screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xhdpi")
class RenderProbeTest {

    @get:Rule
    val compose = createComposeRule()

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
        val settings = AppSettings()
        compose.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(LocalPulse.current.background)) {
                    Text("probe")
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        println("probe bitmap ${bitmap.width}x${bitmap.height}")
        check(bitmap.width > 100) { "capture produced an empty bitmap" }
    }

    @Test
    fun logoRenders() {
        val settings = AppSettings()
        compose.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(LocalPulse.current.background)) {
                    com.mntx.gitpulse.ui.components.PulseLogo(size = 64.dp)
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        check(bitmap.width > 100)
    }

    @Test
    fun signInRenders() {
        val settings = AppSettings()
        compose.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(LocalPulse.current.background)) {
                    com.mntx.gitpulse.ui.auth.SignInScreen(loading = false, error = null, onSignIn = {})
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        check(bitmap.width > 100)
    }
}
