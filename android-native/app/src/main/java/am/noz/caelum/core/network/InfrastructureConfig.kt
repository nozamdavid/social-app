package am.noz.caelum.core.network

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder

@Serializable
data class InfrastructureSettings(
    val useLinkRedirects: Boolean = true,
    val appviewHostname: String = "",
    val imageCdnHostname: String = "",
    val plcDirectoryHostname: String = "",
)

data class HostPreset(
    val name: String,
    val hostname: String,
)

object InfrastructureConfig {
    val DEFAULT = InfrastructureSettings()

    val APPVIEW_PRESETS = listOf(
        HostPreset("Bluesky", ""),
        HostPreset("Eurosky", "api.eurosky.network"),
        HostPreset("Blacksky", "api.blacksky.community"),
    )

    val IMAGE_CDN_PRESETS = listOf(
        HostPreset("Bluesky", ""),
        HostPreset("Luna", "luna.danielmorrisey.com"),
    )

    val PLC_DIRECTORY_PRESETS = listOf(
        HostPreset("plc.directory", ""),
        HostPreset("Eurosky", "plc.eurosky.network"),
        HostPreset("Wafflehouse.dev", "plc.wafflehouse.dev"),
    )

    private val DNS_LABEL_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val INVALID_CHARS_REGEX = Regex("[\\s:/\\\\@?#%]")
    private val CDN_IMG_PREFIX_REGEX = Regex("^https://cdn\\.bsky\\.app/img/[a-z_]+/plain/")
    private val APPVIEW_PATH_REGEX = Regex("^/xrpc/app\\.bsky\\.(?!video\\.)[A-Za-z0-9.]+$")
    private val PREFS_PATH_REGEX = Regex("^/xrpc/app\\.bsky\\.actor\\.(getPreferences|putPreferences)$")

    fun plcDirectoryOrigin(hostname: String): String =
        "https://${hostname.ifEmpty { "plc.directory" }}"

    fun appviewCacheBuster(version: String, hostname: String): String =
        if (hostname.isNotEmpty()) "$version;appview=$hostname" else version

    fun normalizeHostname(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val authority = trimmed
            .replaceFirst(Regex("^https://", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("/$"), "")
        if (authority.isEmpty()) throw IllegalArgumentException("Invalid hostname")
        if (INVALID_CHARS_REGEX.containsMatchIn(authority)) {
            throw IllegalArgumentException("Invalid hostname")
        }
        val parsed = try {
            URI("https://$authority")
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid hostname")
        }
        if (parsed.port != -1 || !parsed.rawUserInfo.isNullOrEmpty() || !parsed.rawPath.isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid hostname")
        }
        val host = (parsed.host ?: throw IllegalArgumentException("Invalid hostname"))
            .lowercase()
            .removeSuffix(".")
        if (host.length > 253 || !host.contains('.')) {
            throw IllegalArgumentException("Invalid hostname")
        }
        val labels = host.split('.')
        if (!labels.all { DNS_LABEL_REGEX.matches(it) }) {
            throw IllegalArgumentException("Invalid hostname")
        }
        return host
    }

    fun normalizeAppview(input: String): String {
        val hostname = normalizeHostname(input)
        return if (hostname == "api.bsky.app" || hostname == "public.api.bsky.app") "" else hostname
    }

    fun normalizeImageCdn(input: String): String {
        val hostname = normalizeHostname(input)
        return if (hostname == "cdn.bsky.app") "" else hostname
    }

    fun normalizePlcDirectory(input: String): String {
        val hostname = normalizeHostname(input)
        return if (hostname == "plc.directory") "" else hostname
    }

    fun rewriteImageUrl(uri: String, hostname: String): String {
        if (hostname.isEmpty() || !CDN_IMG_PREFIX_REGEX.containsMatchIn(uri)) {
            return uri
        }
        return "https://$hostname${uri.substring("https://cdn.bsky.app".length)}"
    }

    fun isAppviewRequest(path: String): Boolean {
        val pathname = path.substringBefore('?')
        return APPVIEW_PATH_REGEX.matches(pathname) && !PREFS_PATH_REGEX.matches(pathname)
    }

    fun createProxiedUrl(url: String, useLinkRedirects: Boolean): String {
        if (!useLinkRedirects) return url
        if (!url.startsWith("https://") && !url.startsWith("http://")) return url
        return "https://go.bsky.app/redirect?u=${URLEncoder.encode(url, "UTF-8")}"
    }
}
