package am.noz.caelum.core.richtext

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ByteSlice(
    val byteStart: Int,
    val byteEnd: Int,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("\$type")
sealed interface FacetFeature {
    @Serializable
    @SerialName("app.bsky.richtext.facet#mention")
    data class Mention(
        val did: String,
    ) : FacetFeature

    @Serializable
    @SerialName("app.bsky.richtext.facet#link")
    data class Link(
        val uri: String,
    ) : FacetFeature

    @Serializable
    @SerialName("app.bsky.richtext.facet#tag")
    data class Tag(
        val tag: String,
    ) : FacetFeature
}

@Serializable
data class RichTextFacet(
    val index: ByteSlice,
    @Serializable(with = FacetFeaturesSerializer::class)
    val features: List<FacetFeature>,
)

private object FacetFeaturesSerializer :
    JsonTransformingSerializer<List<FacetFeature>>(ListSerializer(FacetFeature.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val features = element as? JsonArray ?: return element
        return JsonArray(features.map { feature ->
            // Older Caelum builds wrote Kotlin's default discriminator into AT Protocol records.
            if (feature is JsonObject && "\$type" !in feature && "type" in feature) {
                JsonObject(feature - "type" + ("\$type" to feature.getValue("type")))
            } else {
                feature
            }
        })
    }
}

data class RichTextSegment(
    val text: String,
    val facet: RichTextFacet? = null,
) {
    val mention: FacetFeature.Mention?
        get() = facet?.features?.filterIsInstance<FacetFeature.Mention>()?.firstOrNull()
    val link: FacetFeature.Link?
        get() = facet?.features?.filterIsInstance<FacetFeature.Link>()?.firstOrNull()
    val tag: FacetFeature.Tag?
        get() = facet?.features?.filterIsInstance<FacetFeature.Tag>()?.firstOrNull()
}
