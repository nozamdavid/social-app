package am.noz.caelum.core.emoji

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class EmojiToken(
    val start: Int,
    val end: Int,
    val query: String,
    val context: String,
)

data class EmojiReplacement(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

@Serializable
data class EmojiThirdPartyNotice(
    val name: String,
    val packages: List<String> = emptyList(),
    val license: String,
    val text: String,
)

data class EmojiSearchResult(
    val id: String,
    val name: String,
    val native: String,
    val shortcodes: List<String>,
)

data class EmojiCategory(
    val id: String,
    val emojis: List<EmojiSearchResult>,
)

/**
 * Port of `src/features/emojiSuggestions/{tokens.ts,search.ts}` including
 * Fuse.js-compatible Bitap fuzzy scoring and 160-char/12-word context tie-breaking.
 */
class EmojiSearchEngine(
    private val entries: List<Entry>,
    val categories: List<EmojiCategory>,
    private val emojiById: Map<String, EmojiSearchResult>,
) {
    data class Entry(
        val id: String,
        val displayName: String,
        val native: String,
        val shortcodes: List<String>,
        val normalizedShortcodes: List<String>,
        val shortcodeNorms: List<Double>,
        val name: String,
        val rawKeywords: List<String>,
        val keywords: List<String>,
        val keywordNorms: List<Double>,
        val words: Set<String>,
    )

    fun search(
        query: String,
        limit: Int = 8,
        context: String = "",
    ): List<EmojiSearchResult> {
        if (!QUERY_REGEX.matches(query) || query.length > 64 || limit < 1) return emptyList()
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()

        val contextWords = normalize(context)
            .split(NON_WORD_REGEX)
            .filter { it.length > 2 && it !in STOP_WORDS }
            .takeLast(12)
            .toSet()

        val candidates = HashMap<Entry, Double>(64)
        for (entry in entries) {
            val codes = entry.normalizedShortcodes
            val rank: Double? = when {
                codes.any { it == q } -> 0.0
                codes.any { it.startsWith(q) } -> 1.0
                entry.words.any { it.startsWith(q) } -> 1.1
                q.length >= 3 && codes.any { isAdjacentSwap(q, it) } -> 2.0
                entry.name.contains(q) -> 2.1
                else -> null
            }
            if (rank != null) {
                candidates[entry] = rank
            }
        }

        if (q.length >= 3) {
            val fuzzyResults = fuseSearch(q, limit = 64)
            for ((entry, score) in fuzzyResults) {
                if (!candidates.containsKey(entry)) {
                    candidates[entry] = 3.0 + score
                }
            }
        }

        return candidates.entries
            .map { (entry, rank) ->
                val contextScore = if (contextWords.isEmpty()) {
                    0
                } else {
                    contextWords.count { word -> word in entry.words }
                }
                RankedCandidate(entry = entry, rank = rank, contextScore = contextScore)
            }
            .sortedWith(
                Comparator { a, b ->
                    val floorCmp = floor(a.rank).compareTo(floor(b.rank))
                    if (floorCmp != 0) return@Comparator floorCmp
                    val ctxCmp = b.contextScore.compareTo(a.contextScore)
                    if (ctxCmp != 0) return@Comparator ctxCmp
                    val rankCmp = a.rank.compareTo(b.rank)
                    if (rankCmp != 0) return@Comparator rankCmp
                    a.entry.id.compareCompare(b.entry.id)
                }
            )
            .take(limit)
            .map {
                EmojiSearchResult(
                    id = it.entry.id,
                    name = it.entry.displayName,
                    native = it.entry.native,
                    shortcodes = it.entry.shortcodes,
                )
            }
    }

    private data class RankedCandidate(
        val entry: Entry,
        val rank: Double,
        val contextScore: Int,
    )

    private fun String.compareCompare(other: String): Int = this.compareTo(other)

    /**
     * Exact Bitap search matching Fuse.js 7.3.0 with:
     * keys = [{shortcodes, weight=4}, {name, weight=2}, {keywords, weight=1}],
     * threshold = 0.35, ignoreLocation = true.
     */
    private fun fuseSearch(query: String, limit: Int): List<Pair<Entry, Double>> {
        val pattern = query.lowercase().take(32)
        if (pattern.isEmpty()) return emptyList()
        val alphabet = createPatternAlphabet(pattern)
        val matched = ArrayList<Pair<Entry, Double>>()

        for (entry in entries) {
            val matchComponents = ArrayList<Triple<Double, Int, Double>>(4)

            // Key 0: shortcodes (array, weight = 4) - Fuse includes every matching element in score
            for (i in entry.shortcodes.indices) {
                val raw = entry.shortcodes[i]
                val bitap = bitapSearch(raw.lowercase(), pattern, alphabet, threshold = 0.35, ignoreLocation = true)
                if (bitap != null) {
                    matchComponents.add(Triple(bitap, 4, entry.shortcodeNorms[i]))
                }
            }

            // Key 1: name (weight = 2)
            val nameScore = bitapSearch(entry.displayName.lowercase(), pattern, alphabet, threshold = 0.35, ignoreLocation = true)
            if (nameScore != null) {
                val norm = fieldNorm(entry.displayName)
                matchComponents.add(Triple(nameScore, 2, norm))
            }

            // Key 2: keywords (array, weight = 1) - Fuse includes every matching element in score
            for (i in entry.rawKeywords.indices) {
                val kw = entry.rawKeywords[i]
                val bitap = bitapSearch(kw.lowercase(), pattern, alphabet, threshold = 0.35, ignoreLocation = true)
                if (bitap != null) {
                    matchComponents.add(Triple(bitap, 1, entry.keywordNorms[i]))
                }
            }

            if (matchComponents.isNotEmpty()) {
                var totalScore = 1.0
                for ((score, weight, norm) in matchComponents) {
                    val base = if (score == 0.0) 2.220446049250313E-16 else score
                    totalScore *= base.pow(weight * norm)
                }
                matched.add(entry to totalScore)
            }
        }

        matched.sortBy { it.second }
        return if (matched.size > limit) matched.subList(0, limit) else matched
    }

    fun getById(id: String): EmojiSearchResult? = emojiById[id]

    companion object {
        private val QUERY_REGEX = Regex("^[a-zA-Z0-9_+-]+$")
        private val WHITESPACE_UNDERSCORE_HYPHEN = Regex("[_-]+")
        private val PLUS_MINUS_NUMBER = Regex("^[+-]\\d+$")
        private val WHITESPACE_SPLIT = Regex("\\s+")
        private val NON_WORD_REGEX = Regex("[^\\p{L}\\p{N}]+")
        private val TOKEN_REGEX = Regex("(^|\\s|\\():([a-zA-Z0-9_+-]+):?")
        val FACET_PATTERN = Regex("(^|\\s|\\():([a-zA-Z0-9_+-]+):")

        private val STOP_WORDS = setOf(
            "the", "and", "for", "you", "with", "this", "that", "have",
            "was", "are", "our", "your", "just", "from", "about",
        )

        private val json = Json { ignoreUnknownKeys = true }

        fun fromAssets(context: Context): EmojiSearchEngine {
            return context.assets.open("emoji/emoji_mart_native.json").use { fromJsonStream(it) }
        }

        fun loadNoticesFromAssets(context: Context): List<EmojiThirdPartyNotice> {
            return context.assets.open("emoji/notices.json").bufferedReader().use { reader ->
                json.decodeFromString(reader.readText())
            }
        }

        fun fromJsonStream(stream: InputStream): EmojiSearchEngine {
            val text = stream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text).jsonObject
            val aliasesById = HashMap<String, MutableList<String>>()
            root["aliases"]?.jsonObject?.forEach { (alias, targetIdEl) ->
                val targetId = targetIdEl.jsonPrimitive.content
                aliasesById.getOrPut(targetId) { mutableListOf() }.add(alias)
            }

            val emojisObj = root["emojis"]?.jsonObject ?: JsonObject(emptyMap())
            val entries = ArrayList<Entry>(emojisObj.size)
            val byId = HashMap<String, EmojiSearchResult>(emojisObj.size)

            for ((_, el) in emojisObj) {
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val displayName = obj["name"]?.jsonPrimitive?.content ?: id
                val skins = obj["skins"]?.jsonArray ?: continue
                val nativeChar = skins.firstOrNull()?.jsonObject?.get("native")?.jsonPrimitive?.content ?: continue
                val keywordsRaw = obj["keywords"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                val shortcodes = listOf(id) + (aliasesById[id] ?: emptyList())
                val normalizedShortcodes = shortcodes.map { normalize(it) }
                val normalizedName = normalize(displayName)
                val normalizedKeywords = keywordsRaw.map { normalize(it) }
                val words = (normalizedShortcodes + normalizedName + normalizedKeywords)
                    .flatMap { it.split(WHITESPACE_SPLIT) }
                    .filter { it.isNotEmpty() }
                    .toSet()

                entries.add(
                    Entry(
                        id = id,
                        displayName = displayName,
                        native = nativeChar,
                        shortcodes = shortcodes,
                        normalizedShortcodes = normalizedShortcodes,
                        shortcodeNorms = shortcodes.map { fieldNorm(it) },
                        name = normalizedName,
                        rawKeywords = keywordsRaw,
                        keywords = normalizedKeywords,
                        keywordNorms = keywordsRaw.map { fieldNorm(it) },
                        words = words,
                    )
                )
                byId[id] = EmojiSearchResult(
                    id = id,
                    name = displayName,
                    native = nativeChar,
                    shortcodes = shortcodes,
                )
            }

            val categories = root["categories"]?.jsonArray?.mapNotNull { catEl ->
                val catObj = catEl.jsonObject
                val catId = catObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val list = catObj["emojis"]?.jsonArray?.mapNotNull { idEl ->
                    byId[idEl.jsonPrimitive.content]
                }.orEmpty()
                EmojiCategory(id = catId, emojis = list)
            }.orEmpty()

            return EmojiSearchEngine(entries = entries, categories = categories, emojiById = byId)
        }

        fun normalize(value: String): String {
            if (PLUS_MINUS_NUMBER.matches(value)) return value
            return value.lowercase().replace(WHITESPACE_UNDERSCORE_HYPHEN, " ").trim()
        }

        fun isAdjacentSwap(query: String, code: String): Boolean {
            if (query.length != code.length) return false
            for (i in 0 until query.length - 1) {
                if (query[i] == code[i]) continue
                return query[i] == code[i + 1] &&
                    query[i + 1] == code[i] &&
                    query.substring(i + 2) == code.substring(i + 2)
            }
            return false
        }

        fun getEmojiToken(text: String, start: Int, end: Int = start): EmojiToken? {
            if (start != end || start < 0 || start > text.length) return null
            for (match in TOKEN_REGEX.findAll(text)) {
                val leadLen = match.groupValues[1].length
                val tokenStart = match.range.first + leadLen
                val tokenEnd = match.range.last + 1
                if (start <= tokenStart + 1 || start > tokenEnd) continue
                val codeLen = match.groupValues[2].length
                val queryEnd = min(start, tokenStart + 1 + codeLen)
                val ctxStart = max(0, tokenStart - 160)
                return EmojiToken(
                    start = tokenStart,
                    end = tokenEnd,
                    query = text.substring(tokenStart + 1, queryEnd),
                    context = text.substring(ctxStart, tokenStart),
                )
            }
            return null
        }

        fun replaceEmojiToken(text: String, token: EmojiToken, emoji: String): EmojiReplacement {
            val suffix = text.substring(token.end)
            val insertion = emoji + if (suffix.isEmpty()) " " else ""
            val nextText = text.substring(0, token.start) + insertion + suffix
            val cursor = token.start + insertion.length
            return EmojiReplacement(text = nextText, selectionStart = cursor, selectionEnd = cursor)
        }

        fun getCursorAfterEdit(previous: String, next: String): Int {
            var prefix = 0
            while (prefix < previous.length && prefix < next.length && previous[prefix] == next[prefix]) {
                prefix++
            }
            var suffix = 0
            while (
                suffix < previous.length - prefix &&
                suffix < next.length - prefix &&
                previous[previous.length - 1 - suffix] == next[next.length - 1 - suffix]
            ) {
                suffix++
            }
            return next.length - suffix
        }

        private fun fieldNorm(value: String): Double {
            var numTokens = 0
            var inToken = false
            for (ch in value) {
                if (ch != ' ') {
                    if (!inToken) {
                        numTokens++
                        inToken = true
                    }
                } else {
                    inToken = false
                }
            }
            if (numTokens == 0) return 1.0
            val n = 1.0 / sqrt(numTokens.toDouble())
            return (n * 1000.0).roundToInt() / 1000.0
        }

        private fun createPatternAlphabet(pattern: String): HashMap<Char, Int> {
            val mask = HashMap<Char, Int>(pattern.length * 2)
            val len = pattern.length
            for (i in 0 until len) {
                val ch = pattern[i]
                mask[ch] = (mask[ch] ?: 0) or (1 shl (len - i - 1))
            }
            return mask
        }

        private fun bitapSearch(
            text: String,
            pattern: String,
            alphabet: Map<Char, Int>,
            threshold: Double,
            ignoreLocation: Boolean,
        ): Double? {
            if (text.isEmpty() || pattern.isEmpty()) return null
            val patternLen = pattern.length
            val textLen = text.length
            var currentThreshold = threshold

            if (text.indexOf(pattern) >= 0) {
                return 0.001
            }

            var bestLocation = -1
            var lastBitArr = IntArray(0)
            var finalScore = 1.0
            var binMax = patternLen + textLen
            val mask = 1 shl (patternLen - 1)

            fun calcScore(errors: Int): Double {
                return errors.toDouble() / patternLen.toDouble()
            }

            for (i in 0 until patternLen) {
                val errScore = calcScore(i)
                if (errScore > currentThreshold) break
                val start = 1
                val finish = if (ignoreLocation) textLen + patternLen else min(binMax, textLen) + patternLen
                val bitArr = IntArray(finish + 2)
                bitArr[finish + 1] = (1 shl i) - 1

                for (j in finish downTo start) {
                    val currentLocation = j - 1
                    val charMatch = if (currentLocation in 0 until textLen) {
                        alphabet[text[currentLocation]] ?: 0
                    } else {
                        0
                    }
                    bitArr[j] = ((bitArr[j + 1] shl 1) or 1) and charMatch
                    if (i > 0) {
                        bitArr[j] = bitArr[j] or
                            (((lastBitArr[j + 1] or lastBitArr[j]) shl 1) or 1 or lastBitArr[j + 1])
                    }
                    if ((bitArr[j] and mask) != 0) {
                        finalScore = errScore
                        if (finalScore <= currentThreshold) {
                            currentThreshold = finalScore
                            bestLocation = currentLocation
                            break
                        }
                    }
                }
                if (calcScore(i + 1) > currentThreshold) break
                lastBitArr = bitArr
                binMax = textLen + patternLen
            }

            return if (bestLocation >= 0) max(0.001, finalScore) else null
        }
    }
}
