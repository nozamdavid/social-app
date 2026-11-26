package am.noz.caelum.ui.composer

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.model.recordCid
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

private const val POST_COLLECTION = "app.bsky.feed.post"
private const val THREADGATE_COLLECTION = "app.bsky.feed.threadgate"
private const val POSTGATE_COLLECTION = "app.bsky.feed.postgate"
private const val TID_ALPHABET = "234567abcdefghijklmnopqrstuvwxyz"
private val postTimestampFormatter = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
private val lastTidMicros = AtomicLong()
private val tidClockId = Random.nextInt(1024)

internal fun composerPostWrites(
    did: String,
    posts: List<JsonObject>,
    parent: StrongRef?,
    root: StrongRef?,
    threadgateMode: String,
    allowQuotes: Boolean,
    now: Instant = Instant.now(),
    nextRkey: () -> String = ::nextPostTid,
): JsonArray = buildJsonArray {
    var parentRef = parent
    var rootRef = root ?: parent
    posts.forEachIndexed { index, fields ->
        val rkey = nextRkey()
        val uri = "at://$did/$POST_COLLECTION/$rkey"
        val createdAt = postTimestampFormatter.format(now.plusMillis(index.toLong()))
        val post = buildJsonObject {
            fields.forEach { (key, value) -> put(key, value) }
            put("\$type", POST_COLLECTION)
            put("createdAt", createdAt)
            parentRef?.let { previous ->
                put("reply", buildJsonObject {
                    put("root", AtProtoJson.encodeToJsonElement(checkNotNull(rootRef)))
                    put("parent", AtProtoJson.encodeToJsonElement(previous))
                })
            }
        }
        add(createWrite(POST_COLLECTION, rkey, post))

        if (index == 0 && parent == null && threadgateMode != "anyone") {
            val rule = when (threadgateMode) {
                "following" -> "followingRule"
                "followers" -> "followerRule"
                "mentioned" -> "mentionRule"
                else -> null
            }
            val gate = buildJsonObject {
                put("\$type", THREADGATE_COLLECTION)
                put("post", uri)
                put("createdAt", createdAt)
                put("allow", buildJsonArray {
                    rule?.let { add(buildJsonObject { put("\$type", "$THREADGATE_COLLECTION#$it") }) }
                })
            }
            add(createWrite(THREADGATE_COLLECTION, rkey, gate))
        }
        if (!allowQuotes) {
            val gate = buildJsonObject {
                put("\$type", POSTGATE_COLLECTION)
                put("post", uri)
                put("createdAt", createdAt)
                put("embeddingRules", buildJsonArray {
                    add(buildJsonObject { put("\$type", "$POSTGATE_COLLECTION#disableRule") })
                })
            }
            add(createWrite(POSTGATE_COLLECTION, rkey, gate))
        }

        if (index < posts.lastIndex) {
            val ref = StrongRef(uri, recordCid(post))
            if (rootRef == null) rootRef = ref
            parentRef = ref
        }
    }
}

private fun createWrite(collection: String, rkey: String, value: JsonObject) = buildJsonObject {
    put("\$type", "com.atproto.repo.applyWrites#create")
    put("collection", collection)
    put("rkey", rkey)
    put("value", value)
}

private fun nextPostTid(): String {
    val now = Instant.now()
    val micros = now.epochSecond * 1_000_000 + now.nano / 1_000
    val timestamp = lastTidMicros.updateAndGet { previous -> maxOf(micros, previous + 1) }
    val tid = (timestamp shl 10) or tidClockId.toLong()
    return buildString(13) {
        for (shift in 60 downTo 0 step 5) append(TID_ALPHABET[((tid ushr shift) and 31).toInt()])
    }
}
