package am.noz.caelum.core.richtext

import java.net.URI
import kotlin.math.max
import kotlin.math.min

/**
 * AT Protocol UTF-8 RichText engine matching `@bsky/sdk/richtext`.
 * Never substitutes Kotlin UTF-16 string indices for protocol UTF-8 byte offsets.
 */
object RichTextEngine {

    private val MENTION_REGEX = Regex("(^|\\s|\\()(@)([a-zA-Z0-9.-]+)(\\b)")
    private val URL_REGEX = Regex(
        "(^|\\s|\\()((https?://\\S+)|(([a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z0-9]+)+)\\S*))",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val TRAILING_PUNCT_REGEX = Regex("\\p{P}+$")
    private val TAG_REGEX = Regex(
        "(^|\\s)[#＃]((?!\\ufe0f)[^\\s\\u00AD\\u2060\\u200A\\u200B\\u200C\\u200D\\u20e2]*[^\\d\\s\\p{P}\\u00AD\\u2060\\u200A\\u200B\\u200C\\u200D\\u20e2][^\\s\\u00AD\\u2060\\u200A\\u200B\\u200C\\u200D\\u20e2]*)?"
    )
    private val CASHTAG_REGEX = Regex(
        "(^|\\s|\\()\\$([A-Za-z][A-Za-z0-9]{0,4})(?=\\s|$|[.,;:!?)\"'’])"
    )
    private val CLEAN_NEWLINES_REGEX = Regex(
        "[\\r\\n]([\\u00AD\\u2060\\u200D\\u200C\\u200B\\s]*[\\r\\n]){2,}"
    )

    class Utf8Mapper(val text: String) {
        val utf8Bytes: ByteArray = text.toByteArray(Charsets.UTF_8)
        val utf8Length: Int get() = utf8Bytes.size
        private val utf16ToUtf8: IntArray = buildMap(text)

        fun utf16ToUtf8(utf16Index: Int): Int {
            val clamped = utf16Index.coerceIn(0, text.length)
            return utf16ToUtf8[clamped]
        }

        fun utf8ToUtf16(byteOffset: Int): Int {
            val clamped = byteOffset.coerceIn(0, utf8Length)
            var lo = 0
            var hi = text.length
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (utf16ToUtf8[mid] <= clamped) {
                    lo = mid
                } else {
                    hi = mid - 1
                }
            }
            return lo
        }

        fun sliceBytes(byteStart: Int, byteEnd: Int): String {
            val s = byteStart.coerceIn(0, utf8Length)
            val e = byteEnd.coerceIn(s, utf8Length)
            return String(utf8Bytes, s, e - s, Charsets.UTF_8)
        }

        private companion object {
            fun buildMap(text: String): IntArray {
                val arr = IntArray(text.length + 1)
                var bytes = 0
                var i = 0
                while (i < text.length) {
                    arr[i] = bytes
                    val ch = text[i]
                    if (ch.code < 0x80) {
                        bytes += 1
                        i += 1
                    } else if (ch.code < 0x800) {
                        bytes += 2
                        i += 1
                    } else if (Character.isHighSurrogate(ch) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                        arr[i + 1] = bytes
                        bytes += 4
                        i += 2
                    } else {
                        bytes += 3
                        i += 1
                    }
                }
                arr[text.length] = bytes
                return arr
            }
        }
    }

    /**
     * Counts Unicode extended grapheme clusters (UAX #29), treating ZWJ sequences,
     * skin-tone modifiers, regional indicator flag pairs, and combining marks accurately.
     */
    fun graphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0
        var i = 0
        val n = text.length
        while (i < n) {
            count++
            i = nextGraphemeBoundary(text, i)
        }
        return count
    }

    private fun nextGraphemeBoundary(text: String, start: Int): Int {
        val n = text.length
        if (start >= n) return n
        var pos = start + Character.charCount(text.codePointAt(start))
        var prevCp = text.codePointAt(start)

        // Regional indicator pair (flags like 🇺🇸, 🏳️‍🌈)
        if (isRegionalIndicator(prevCp) && pos < n) {
            val nextCp = text.codePointAt(pos)
            if (isRegionalIndicator(nextCp)) {
                pos += Character.charCount(nextCp)
                prevCp = nextCp
            }
        }

        while (pos < n) {
            val cp = text.codePointAt(pos)
            val cpLen = Character.charCount(cp)
            when {
                cp == 0x200D -> {
                    // ZWJ joins next emoji / base character
                    pos += cpLen
                    if (pos < n) {
                        val afterZwj = text.codePointAt(pos)
                        pos += Character.charCount(afterZwj)
                        prevCp = afterZwj
                    } else {
                        prevCp = cp
                    }
                }
                prevCp == '\r'.code && cp == '\n'.code -> {
                    pos += cpLen
                    return pos
                }
                isExtendOrZwjSkip(cp) || isEmojiModifier(cp) -> {
                    pos += cpLen
                    prevCp = cp
                }
                else -> break
            }
        }
        return pos
    }

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    private fun isEmojiModifier(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

    private fun isExtendOrZwjSkip(cp: Int): Boolean {
        if (cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3) return true
        if (cp in 0xE0020..0xE007F) return true // Tag characters (e.g., flag subregions)
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    fun isValidDomain(host: String, tlds: Set<String>): Boolean {
        val lower = host.lowercase()
        val lastDot = lower.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == lower.length - 1) return false
        val tld = lower.substring(lastDot + 1)
        return tlds.contains(tld)
    }

    fun detectFacetsWithoutResolution(
        text: String,
        tlds: Set<String>,
    ): List<RichTextFacet> {
        if (text.isEmpty()) return emptyList()
        val mapper = Utf8Mapper(text)
        val facets = mutableListOf<RichTextFacet>()

        // 1. Mentions
        for (match in MENTION_REGEX.findAll(text)) {
            val handle = match.groupValues[3]
            if (!isValidDomain(handle, tlds) && !handle.endsWith(".test", ignoreCase = true)) {
                continue
            }
            val atStart = match.groups[2]!!.range.first
            val end = atStart + 1 + handle.length
            facets.add(
                RichTextFacet(
                    index = ByteSlice(
                        byteStart = mapper.utf16ToUtf8(atStart),
                        byteEnd = mapper.utf16ToUtf8(end),
                    ),
                    features = listOf(FacetFeature.Mention(did = handle)),
                )
            )
        }

        // 2. Links
        for (match in URL_REGEX.findAll(text)) {
            val fullUrlGroup = match.groups[2] ?: continue
            var uri = fullUrlGroup.value
            val start = fullUrlGroup.range.first
            var end = start + uri.length

            if (!uri.startsWith("http", ignoreCase = true)) {
                val domain = match.groups[5]?.value ?: continue
                if (!isValidDomain(domain, tlds)) continue
                uri = "https://$uri"
            }

            if (uri.isNotEmpty() && uri.last() in charArrayOf('.', ',', ';', ':', '!', '?')) {
                uri = uri.dropLast(1)
                end--
            }
            if (uri.endsWith(")") && !uri.contains("(")) {
                uri = uri.dropLast(1)
                end--
            }
            if (end > start) {
                facets.add(
                    RichTextFacet(
                        index = ByteSlice(
                            byteStart = mapper.utf16ToUtf8(start),
                            byteEnd = mapper.utf16ToUtf8(end),
                        ),
                        features = listOf(FacetFeature.Link(uri = uri)),
                    )
                )
            }
        }

        // 3. Hashtags
        for (match in TAG_REGEX.findAll(text)) {
            val leading = match.groupValues[1]
            var tag = match.groupValues[2]
            if (tag.isEmpty()) continue
            tag = tag.trim().replace(TRAILING_PUNCT_REGEX, "")
            if (tag.isEmpty() || (tag.length > 64 && graphemeLength(tag) > 64)) continue
            val index = match.range.first + leading.length
            facets.add(
                RichTextFacet(
                    index = ByteSlice(
                        byteStart = mapper.utf16ToUtf8(index),
                        byteEnd = mapper.utf16ToUtf8(index + 1 + tag.length),
                    ),
                    features = listOf(FacetFeature.Tag(tag = tag)),
                )
            )
        }

        // 4. Cashtags
        for (match in CASHTAG_REGEX.findAll(text)) {
            val leading = match.groupValues[1]
            val rawTicker = match.groupValues[2]
            if (rawTicker.isEmpty()) continue
            val ticker = rawTicker.uppercase()
            val index = match.range.first + leading.length
            facets.add(
                RichTextFacet(
                    index = ByteSlice(
                        byteStart = mapper.utf16ToUtf8(index),
                        byteEnd = mapper.utf16ToUtf8(index + 1 + ticker.length),
                    ),
                    features = listOf(FacetFeature.Tag(tag = "$$ticker")),
                )
            )
        }

        return facets.sortedBy { it.index.byteStart }
    }

    suspend fun detectFacets(
        text: String,
        tlds: Set<String>,
        resolveHandle: suspend (String) -> String?,
    ): List<RichTextFacet> {
        val detected = detectFacetsWithoutResolution(text, tlds)
        val resolved = mutableListOf<RichTextFacet>()
        for (facet in detected) {
            val mention = facet.features.filterIsInstance<FacetFeature.Mention>().firstOrNull()
            if (mention != null && !mention.did.startsWith("did:")) {
                val did = resolveHandle(mention.did)
                if (!did.isNullOrBlank()) {
                    resolved.add(
                        facet.copy(
                            features = facet.features.map {
                                if (it is FacetFeature.Mention) FacetFeature.Mention(did) else it
                            }
                        )
                    )
                }
            } else {
                resolved.add(facet)
            }
        }
        return resolved
    }

    fun toShortUrl(url: String): String {
        return try {
            val parsed = URI(url)
            val scheme = parsed.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return url
            val host = parsed.rawAuthority ?: return url
            val rawPath = parsed.rawPath.orEmpty()
            val pathPart = (if (rawPath == "/") "" else rawPath) +
                (parsed.rawQuery?.let { "?$it" } ?: "") +
                (parsed.rawFragment?.let { "#$it" } ?: "")
            if (pathPart.length > 15) {
                host + pathPart.substring(0, 13) + "..."
            } else {
                host + pathPart
            }
        } catch (_: Exception) {
            url
        }
    }

    fun shortenLinks(
        text: String,
        facets: List<RichTextFacet>,
    ): Pair<String, List<RichTextFacet>> {
        if (facets.isEmpty()) return text to facets
        var currentBytes = text.toByteArray(Charsets.UTF_8)
        val mutableFacets = facets.map {
            it.copy(index = it.index.copy())
        }.toMutableList()

        for (i in mutableFacets.indices) {
            val facet = mutableFacets[i]
            val isLink = facet.features.any { it is FacetFeature.Link }
            if (!isLink) continue

            val origStart = facet.index.byteStart.coerceIn(0, currentBytes.size)
            val origEnd = facet.index.byteEnd.coerceIn(origStart, currentBytes.size)
            val url = String(currentBytes, origStart, origEnd - origStart, Charsets.UTF_8)
            val shortenedBytes = toShortUrl(url).toByteArray(Charsets.UTF_8)
            val delta = shortenedBytes.size - (origEnd - origStart)

            val updatedBytes = ByteArray(currentBytes.size + delta)
            System.arraycopy(currentBytes, 0, updatedBytes, 0, origStart)
            System.arraycopy(shortenedBytes, 0, updatedBytes, origStart, shortenedBytes.size)
            System.arraycopy(currentBytes, origEnd, updatedBytes, origStart + shortenedBytes.size, currentBytes.size - origEnd)
            currentBytes = updatedBytes

            mutableFacets[i] = facet.copy(
                index = ByteSlice(byteStart = origStart, byteEnd = origStart + shortenedBytes.size)
            )

            for (j in mutableFacets.indices) {
                if (j == i) continue
                val other = mutableFacets[j]
                if (other.index.byteStart >= origEnd) {
                    mutableFacets[j] = other.copy(
                        index = ByteSlice(
                            byteStart = max(0, other.index.byteStart + delta),
                            byteEnd = max(0, other.index.byteEnd + delta),
                        )
                    )
                } else if (other.index.byteEnd > origStart) {
                    mutableFacets[j] = other.copy(
                        index = ByteSlice(
                            byteStart = min(other.index.byteStart, origStart),
                            byteEnd = max(origStart, other.index.byteEnd + delta),
                        )
                    )
                }
            }
        }
        return String(currentBytes, Charsets.UTF_8) to mutableFacets
    }

    fun cleanNewlines(text: String): String {
        return CLEAN_NEWLINES_REGEX.replace(text, "\n\n")
    }

    fun segments(text: String, facets: List<RichTextFacet>?): List<RichTextSegment> {
        if (text.isEmpty()) return emptyList()
        val sorted = facets.orEmpty()
            .filter { it.index.byteStart <= it.index.byteEnd }
            .sortedBy { it.index.byteStart }
        if (sorted.isEmpty()) return listOf(RichTextSegment(text))

        val mapper = Utf8Mapper(text)
        val result = mutableListOf<RichTextSegment>()
        var byteCursor = 0

        for (facet in sorted) {
            val start = facet.index.byteStart.coerceIn(0, mapper.utf8Length)
            val end = facet.index.byteEnd.coerceIn(start, mapper.utf8Length)
            if (byteCursor > start) {
                // Overlapping facet; skip just like @bsky/sdk
                continue
            }
            if (byteCursor < start) {
                result.add(RichTextSegment(mapper.sliceBytes(byteCursor, start)))
            }
            val subtext = mapper.sliceBytes(start, end)
            if (subtext.trim().isEmpty()) {
                result.add(RichTextSegment(subtext))
            } else {
                result.add(RichTextSegment(subtext, facet))
            }
            byteCursor = end
        }
        if (byteCursor < mapper.utf8Length) {
            result.add(RichTextSegment(mapper.sliceBytes(byteCursor, mapper.utf8Length)))
        }
        return result
    }
}
