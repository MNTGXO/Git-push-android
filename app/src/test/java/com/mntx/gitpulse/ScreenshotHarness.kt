package com.mntx.gitpulse

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

/**
 * Headless screenshot capture that does **not** use Compose's semantics idling.
 *
 * `SemanticsNodeInteraction.captureToImage()` calls `waitForIdle()`, and under Robolectric the
 * Compose frame clock never reports idle — every attempt (including a bare `Box { Text(...) }`)
 * ends in `ComposeTimeoutException`. Drawing the activity's content view to a bitmap instead
 * renders exactly what a user would see, with no idling involved.
 */
abstract class ScreenshotHarness {

    @get:Rule
    val rule: AndroidComposeTestRule<androidx.activity.compose.ComponentActivity, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>()

    protected fun renderToBitmap(): Bitmap {
        val activity = rule.activity
        var root = activity.findViewById<ViewGroup>(android.R.id.content)
        // Pump a composition frame so the content view exists and has drawn at least once.
        repeat(2) {
            rule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
        if (root.width == 0 || root.height == 0 || (root.childCount > 0 && root.getChildAt(0).width == 0)) {
            val dm = activity.resources.displayMetrics
            val w = View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
            root.measure(w, h)
            root.layout(0, 0, dm.widthPixels, dm.heightPixels)
            rule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
        return root.drawToBitmap()
    }

    protected fun save(name: String): File {
        val bitmap = renderToBitmap()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("captured -> ${file.absolutePath} (${file.name}, ${bitmap.width}x${bitmap.height}, ${file.length()} bytes)")
        check(bitmap.width > 100 && bitmap.height > 100) { "bitmap for $name was empty" }
        check(file.length() > 2_000) { "screenshot $name is suspiciously small" }
        return file
    }
}
