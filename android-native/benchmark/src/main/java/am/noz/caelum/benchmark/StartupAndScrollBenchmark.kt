package am.noz.caelum.benchmark

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Side-by-side Macrobenchmark A/B suite comparing the native Compose app (`am.noz.caelum.native`)
 * against the React Native baseline (`am.noz.caelum`).
 */
@RunWith(AndroidJUnit4::class)
class StartupAndScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNative() = measureStartup(
        packageName = NATIVE_PACKAGE,
        startupMode = StartupMode.COLD,
    )

    @Test
    fun coldStartupReactNative() = measureStartup(
        packageName = RN_PACKAGE,
        startupMode = StartupMode.COLD,
    )

    @Test
    fun warmStartupNative() = measureStartup(
        packageName = NATIVE_PACKAGE,
        startupMode = StartupMode.WARM,
    )

    @Test
    fun warmStartupReactNative() = measureStartup(
        packageName = RN_PACKAGE,
        startupMode = StartupMode.WARM,
    )

    @Test
    fun feedScrollFrameTimingNative() = measureFeedScroll(NATIVE_PACKAGE)

    @Test
    fun feedScrollFrameTimingReactNative() = measureFeedScroll(RN_PACKAGE)

    private fun measureStartup(
        packageName: String,
        startupMode: StartupMode,
    ) {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = startupMode,
            iterations = STARTUP_ITERATIONS,
        ) {
            pressHome()
            startActivityAndWait(launchIntent(packageName))
        }
    }

    private fun measureFeedScroll(packageName: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val feedUrl = Uri.parse(requireNotNull(arguments.getString("feedUrl")) {
            "Supply feedUrl for the shared benchmark feed fixture"
        })
        require(feedUrl.scheme == "https" && feedUrl.host == "bsky.app" &&
            feedUrl.pathSegments.size == 4 &&
            feedUrl.pathSegments[0] == "profile" && feedUrl.pathSegments[2] == "feed"
        ) { "feedUrl must be an HTTPS bsky.app custom-feed link" }
        val firstPost = requireNotNull(arguments.getString("feedFirstPost")?.takeIf { it.isNotBlank() }) {
            "Supply feedFirstPost with the a unique visible phrase from the first fixture post"
        }
        val laterPost = requireNotNull(arguments.getString("feedLaterPost")?.takeIf { it.isNotBlank() }) {
            "Supply feedLaterPost with the a unique visible phrase from the later fixture post"
        }
        require(firstPost != laterPost) { "The fixture post texts must be distinct" }

        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                killProcess()
                pressHome()
                startActivityAndWait(launchIntent(packageName).setAction(Intent.ACTION_VIEW).setData(feedUrl))
                check(device.wait({ it.hasPostMarker(packageName, firstPost) }, FEED_TIMEOUT_MS)) {
                    "$packageName did not load the first fixture post at $feedUrl"
                }
                device.waitForIdle()
                check(device.hasPostMarker(packageName, firstPost) && !device.hasPostMarker(packageName, laterPost)) {
                    "$packageName must start with the first fixture post visible and the later post offscreen"
                }
            },
        ) {
            swipeFeed()
            check(device.wait({ it.hasPostMarker(packageName, laterPost) && !it.hasPostMarker(packageName, firstPost) }, SCROLL_TIMEOUT_MS)) {
                "$packageName did not scroll from the first to the later fixture post"
            }

        }
    }

    private fun UiDevice.hasPostMarker(packageName: String, marker: String): Boolean {
        // A post body can be a TextView or part of a merged accessibility description.
        return hasObject(By.pkg(packageName).textContains(marker)) ||
            hasObject(By.pkg(packageName).descContains(marker))
    }

    private fun MacrobenchmarkScope.swipeFeed() {
        val centerX = device.displayWidth / 2
        val bottomY = (device.displayHeight * 0.8f).toInt()
        val topY = (device.displayHeight * 0.3f).toInt()
        check(device.swipe(centerX, bottomY, centerX, topY, SWIPE_STEPS)) {
            "Could not inject the feed swipe"
        }
        device.waitForIdle()
    }

    private fun launchIntent(packageName: String): Intent =
        Intent()
            .setComponent(ComponentName(packageName, MAIN_ACTIVITY_CLASS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    companion object {
        const val RN_PACKAGE = "am.noz.caelum"
        const val NATIVE_PACKAGE = "am.noz.caelum.native"
        const val MAIN_ACTIVITY_CLASS = "am.noz.caelum.MainActivity"

        private const val STARTUP_ITERATIONS = 3
        private const val SCROLL_ITERATIONS = 5
        private const val SWIPE_STEPS = 40
        private const val FEED_TIMEOUT_MS = 20_000L
        private const val SCROLL_TIMEOUT_MS = 3_000L
    }
}
