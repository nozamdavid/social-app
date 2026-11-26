package am.noz.caelum.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline Profiles and Startup Profiles for the native Caelum app.
 *
 * Pass `-Pandroid.testInstrumentationRunnerArguments.targetAppId=am.noz.caelum` (or omit for
 * `am.noz.caelum.native`) when invoking the generator against a specific application ID.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackage = InstrumentationRegistry.getArguments()
            .getString("targetAppId")
            ?: DEFAULT_NATIVE_PACKAGE

        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait(launchIntent(targetPackage))
            device.waitForIdle()

            // Enter public guest browsing when starting on the unauthenticated welcome screen.
            device.wait(Until.findObject(By.text("Explore public feeds")), 3_000L)?.click()
            device.waitForIdle()
            SystemClock.sleep(2_000L)
            device.waitForIdle()

            scrollFeedDownAndUp()

            // Exercise the Search tab transition.
            val searchTab = device.wait(Until.findObject(By.descContains("Search")), 2_000L)
                ?: device.findObject(By.text("Search"))
            searchTab?.click()
            device.waitForIdle()

            // Return to Home if needed so the drawer toggle is reachable, then open the drawer.
            val drawerButton = findDrawerButton() ?: run {
                val homeTab = device.findObject(By.descContains("Home"))
                    ?: device.findObject(By.text("Home"))
                if (homeTab != null) {
                    homeTab.click()
                } else {
                    device.pressBack()
                }
                device.waitForIdle()
                findDrawerButton(timeoutMs = 2_000L)
            }
            drawerButton?.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.scrollFeedDownAndUp() {
        val centerX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.8f).toInt()
        val topY = (device.displayHeight * 0.25f).toInt()

        repeat(3) {
            device.swipe(centerX, bottomY, centerX, topY, SWIPE_STEPS)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(centerX, topY, centerX, bottomY, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.findDrawerButton(timeoutMs: Long = 0L) =
        if (timeoutMs > 0L) {
            device.wait(Until.findObject(By.desc("Open drawer menu")), timeoutMs)
                ?: device.findObject(By.descContains("drawer"))
        } else {
            device.findObject(By.desc("Open drawer menu"))
                ?: device.findObject(By.descContains("drawer"))
        }

    private fun launchIntent(packageName: String): Intent =
        Intent()
            .setComponent(ComponentName(packageName, MAIN_ACTIVITY_CLASS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    companion object {
        private const val DEFAULT_NATIVE_PACKAGE = "am.noz.caelum.native"
        private const val MAIN_ACTIVITY_CLASS = "am.noz.caelum.MainActivity"
        private const val SWIPE_STEPS = 30
    }
}
