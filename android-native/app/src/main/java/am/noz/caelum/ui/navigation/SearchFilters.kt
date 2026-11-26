package am.noz.caelum.ui.navigation

import android.net.Uri
import kotlinx.serialization.Serializable

/** Structured post-search filters shared by search links and the native form. */
@Serializable
data class SearchFilters(
    val from: String = "",
    val author: String = "",
    val mentions: String = "",
    val domain: String = "",
    val url: String = "",
    val tag: String = "",
    val lang: String = "",
    val since: String = "",
    val until: String = "",
) {
    val postOnly: Boolean
        get() = copy(lang = "") != SearchFilters()

    val count: Int
        get() = parameters().count { (_, value) -> value.isNotBlank() }

    fun clean(): SearchFilters = SearchFilters(
        from = from.trim().takeIf { it == "me" }.orEmpty(),
        author = author.trim().removePrefix("@"),
        mentions = mentions.trim().removePrefix("@"),
        domain = domain.trim(),
        url = url.trim(),
        tag = tag.split(Regex("\\s+")).map { it.removePrefix("#") }.filter { it.isNotBlank() }.joinToString(" "),
        lang = lang.trim(),
        since = since.trim(),
        until = until.trim(),
    )

    fun shareUrl(query: String): String = Uri.parse("https://bsky.app/search").buildUpon()
        .apply {
            if (query.isNotBlank()) appendQueryParameter("q", query)
            for ((key, value) in parameters()) {
                if (value.isNotBlank()) appendQueryParameter(key, value)
            }
        }
        .build()
        .toString()

    private fun parameters() = listOf(
        "from" to from,
        "author" to author,
        "mentions" to mentions,
        "domain" to domain,
        "url" to url,
        "tag" to tag,
        "lang" to lang,
        "since" to since,
        "until" to until,
    )

    companion object {
        fun fromUri(uri: Uri): SearchFilters {
            fun read(key: String) = uri.getQueryParameter(key)?.takeUnless { it == "undefined" }.orEmpty()
            return SearchFilters(
                from = read("from"),
                author = read("author"),
                mentions = read("mentions"),
                domain = read("domain"),
                url = read("url"),
                tag = read("tag"),
                lang = read("lang"),
                since = read("since"),
                until = read("until"),
            ).clean()
        }
    }
}
