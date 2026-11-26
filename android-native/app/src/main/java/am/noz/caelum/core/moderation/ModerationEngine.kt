package am.noz.caelum.core.moderation

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.Label
import am.noz.caelum.core.model.LabelValueDefinition
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.ListViewBasic
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

enum class ModerationContext {
    profileList,
    profileView,
    avatar,
    banner,
    displayName,
    contentList,
    contentView,
    contentMedia,
}

enum class ModerationBehaviorValue {
    blur,
    alert,
    inform,
}

enum class LabelTarget {
    account,
    profile,
    content,
}

enum class LabelPreference(val wire: String) {
    ignore("ignore"),
    warn("warn"),
    hide("hide");

    companion object {
        fun fromWire(value: String?): LabelPreference? = when (value) {
            "ignore" -> ignore
            "warn" -> warn
            "hide" -> hide
            else -> null
        }
    }
}

data class ModerationPrefs(
    val adultContentEnabled: Boolean = false,
    val labels: Map<String, LabelPreference> = DEFAULT_LABEL_SETTINGS,
    val labelers: List<LabelerPref> = emptyList(),
    val mutedWords: List<MutedWord> = emptyList(),
    val hiddenPosts: List<String> = emptyList(),
)

data class LabelerPref(
    val did: String,
    val labels: Map<String, LabelPreference> = emptyMap(),
)

data class InterpretedLabelDef(
    val identifier: String,
    val configurable: Boolean,
    val defaultSetting: LabelPreference,
    val flags: Set<String>,
    val severity: String,
    val blurs: String,
    val behaviors: Map<LabelTarget, Map<ModerationContext, ModerationBehaviorValue>>,
    val definedBy: String? = null,
    val title: String? = null,
    val description: String? = null,
)

data class ModerationOpts(
    val userDid: String? = null,
    val prefs: ModerationPrefs = ModerationPrefs(),
    val labelDefs: Map<String, List<InterpretedLabelDef>> = emptyMap(),
    val labelerProfiles: Map<String, ProfileView> = emptyMap(),
)

sealed interface ModerationCauseSource {
    data object User : ModerationCauseSource
    data class List(val list: ListViewBasic) : ModerationCauseSource
    data class Labeler(val did: String) : ModerationCauseSource
}

sealed interface ModerationCause {
    val type: String
    val source: ModerationCauseSource
    val priority: Int
    var downgraded: Boolean

    data class Blocking(
        override val source: ModerationCauseSource,
        override val priority: Int = 3,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "blocking"
    }

    data class BlockedBy(
        override val source: ModerationCauseSource = ModerationCauseSource.User,
        override val priority: Int = 4,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "blocked-by"
    }

    data class BlockOther(
        override val source: ModerationCauseSource = ModerationCauseSource.User,
        override val priority: Int = 4,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "block-other"
    }

    data class Muted(
        override val source: ModerationCauseSource,
        override val priority: Int = 6,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "muted"
    }

    data class MuteWord(
        override val source: ModerationCauseSource = ModerationCauseSource.User,
        override val priority: Int = 6,
        override var downgraded: Boolean = false,
        val matches: List<String>,
    ) : ModerationCause {
        override val type: String = "mute-word"
    }

    data class Hidden(
        override val source: ModerationCauseSource = ModerationCauseSource.User,
        override val priority: Int = 6,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "hidden"
    }

    data class LabelCause(
        override val source: ModerationCauseSource,
        val label: Label,
        val labelDef: InterpretedLabelDef,
        val target: LabelTarget,
        val setting: LabelPreference,
        val behavior: Map<ModerationContext, ModerationBehaviorValue>,
        val noOverride: Boolean,
        override val priority: Int,
        override var downgraded: Boolean = false,
    ) : ModerationCause {
        override val type: String = "label"
    }
}

class ModerationUI {
    var noOverride: Boolean = false
    val filters: MutableList<ModerationCause> = mutableListOf()
    val blurs: MutableList<ModerationCause> = mutableListOf()
    val alerts: MutableList<ModerationCause> = mutableListOf()
    val informs: MutableList<ModerationCause> = mutableListOf()

    val filter: Boolean get() = filters.isNotEmpty()
    val blur: Boolean get() = blurs.isNotEmpty()
    val alert: Boolean get() = alerts.isNotEmpty()
    val inform: Boolean get() = informs.isNotEmpty()

    companion object {
        fun merge(vararg uis: ModerationUI?): ModerationUI {
            val merged = ModerationUI()
            for (ui in uis) {
                if (ui == null) continue
                merged.noOverride = merged.noOverride || ui.noOverride
                merged.filters.addAll(ui.filters)
                merged.blurs.addAll(ui.blurs)
                merged.alerts.addAll(ui.alerts)
                merged.informs.addAll(ui.informs)
            }
            return merged
        }
    }
}

val DEFAULT_LABEL_SETTINGS = mapOf(
    "porn" to LabelPreference.hide,
    "sexual" to LabelPreference.warn,
    "nudity" to LabelPreference.ignore,
    "graphic-media" to LabelPreference.warn,
)

private val CUSTOM_LABEL_VALUE_RE = Regex("^[a-z-]+$")

private val BLOCK_BEHAVIOR = mapOf(
    ModerationContext.profileList to ModerationBehaviorValue.blur,
    ModerationContext.profileView to ModerationBehaviorValue.alert,
    ModerationContext.avatar to ModerationBehaviorValue.blur,
    ModerationContext.banner to ModerationBehaviorValue.blur,
    ModerationContext.contentList to ModerationBehaviorValue.blur,
    ModerationContext.contentView to ModerationBehaviorValue.blur,
)

private val MUTE_BEHAVIOR = mapOf(
    ModerationContext.profileList to ModerationBehaviorValue.inform,
    ModerationContext.profileView to ModerationBehaviorValue.alert,
    ModerationContext.contentList to ModerationBehaviorValue.blur,
    ModerationContext.contentView to ModerationBehaviorValue.inform,
)

private val MUTEWORD_BEHAVIOR = mapOf(
    ModerationContext.contentList to ModerationBehaviorValue.blur,
    ModerationContext.contentView to ModerationBehaviorValue.blur,
)

private val HIDE_BEHAVIOR = mapOf(
    ModerationContext.contentList to ModerationBehaviorValue.blur,
    ModerationContext.contentView to ModerationBehaviorValue.blur,
)

private val KNOWN_LABELS: Map<String, InterpretedLabelDef> = buildMap {
    val contentHideAndWarnBehaviors = mapOf(
        LabelTarget.account to mapOf(
            ModerationContext.profileList to ModerationBehaviorValue.blur,
            ModerationContext.profileView to ModerationBehaviorValue.blur,
            ModerationContext.avatar to ModerationBehaviorValue.blur,
            ModerationContext.banner to ModerationBehaviorValue.blur,
            ModerationContext.displayName to ModerationBehaviorValue.blur,
            ModerationContext.contentList to ModerationBehaviorValue.blur,
            ModerationContext.contentView to ModerationBehaviorValue.blur,
        ),
        LabelTarget.profile to mapOf(
            ModerationContext.avatar to ModerationBehaviorValue.blur,
            ModerationContext.banner to ModerationBehaviorValue.blur,
            ModerationContext.displayName to ModerationBehaviorValue.blur,
        ),
        LabelTarget.content to mapOf(
            ModerationContext.contentList to ModerationBehaviorValue.blur,
            ModerationContext.contentView to ModerationBehaviorValue.blur,
        ),
    )
    put(
        "!hide",
        InterpretedLabelDef(
            identifier = "!hide",
            configurable = false,
            defaultSetting = LabelPreference.hide,
            flags = setOf("no-override", "no-self"),
            severity = "alert",
            blurs = "content",
            behaviors = contentHideAndWarnBehaviors,
        ),
    )
    put(
        "!warn",
        InterpretedLabelDef(
            identifier = "!warn",
            configurable = false,
            defaultSetting = LabelPreference.warn,
            flags = setOf("no-self"),
            severity = "none",
            blurs = "content",
            behaviors = mapOf(
                LabelTarget.account to mapOf(
                    ModerationContext.profileList to ModerationBehaviorValue.blur,
                    ModerationContext.profileView to ModerationBehaviorValue.blur,
                    ModerationContext.avatar to ModerationBehaviorValue.blur,
                    ModerationContext.banner to ModerationBehaviorValue.blur,
                    ModerationContext.contentList to ModerationBehaviorValue.blur,
                    ModerationContext.contentView to ModerationBehaviorValue.blur,
                ),
                LabelTarget.profile to contentHideAndWarnBehaviors[LabelTarget.profile]!!,
                LabelTarget.content to contentHideAndWarnBehaviors[LabelTarget.content]!!,
            ),
        ),
    )
    put(
        "!no-unauthenticated",
        InterpretedLabelDef(
            identifier = "!no-unauthenticated",
            configurable = false,
            defaultSetting = LabelPreference.hide,
            flags = setOf("no-override", "unauthed"),
            severity = "none",
            blurs = "content",
            behaviors = contentHideAndWarnBehaviors,
        ),
    )
    val mediaBehaviors = mapOf(
        LabelTarget.account to mapOf(
            ModerationContext.avatar to ModerationBehaviorValue.blur,
            ModerationContext.banner to ModerationBehaviorValue.blur,
        ),
        LabelTarget.profile to mapOf(
            ModerationContext.avatar to ModerationBehaviorValue.blur,
            ModerationContext.banner to ModerationBehaviorValue.blur,
        ),
        LabelTarget.content to mapOf(
            ModerationContext.contentMedia to ModerationBehaviorValue.blur,
        ),
    )
    put(
        "porn",
        InterpretedLabelDef(
            identifier = "porn",
            configurable = true,
            defaultSetting = LabelPreference.hide,
            flags = setOf("adult"),
            severity = "none",
            blurs = "media",
            behaviors = mediaBehaviors,
        ),
    )
    put(
        "sexual",
        InterpretedLabelDef(
            identifier = "sexual",
            configurable = true,
            defaultSetting = LabelPreference.warn,
            flags = setOf("adult"),
            severity = "none",
            blurs = "media",
            behaviors = mediaBehaviors,
        ),
    )
    put(
        "nudity",
        InterpretedLabelDef(
            identifier = "nudity",
            configurable = true,
            defaultSetting = LabelPreference.ignore,
            flags = emptySet(),
            severity = "none",
            blurs = "media",
            behaviors = mediaBehaviors,
        ),
    )
    put(
        "graphic-media",
        InterpretedLabelDef(
            identifier = "graphic-media",
            configurable = true,
            defaultSetting = LabelPreference.warn,
            flags = setOf("adult"),
            severity = "none",
            blurs = "media",
            behaviors = mediaBehaviors,
        ),
    )
    put(
        "gore",
        InterpretedLabelDef(
            identifier = "gore",
            configurable = true,
            defaultSetting = LabelPreference.warn,
            flags = setOf("adult"),
            severity = "none",
            blurs = "media",
            behaviors = mediaBehaviors,
        ),
    )
}

class ModerationDecision {
    var did: String = ""
    var isMe: Boolean = false
    val causes: MutableList<ModerationCause> = mutableListOf()

    val blocked: Boolean get() = blockCause != null
    val muted: Boolean get() = muteCause != null
    val blockCause: ModerationCause?
        get() = causes.firstOrNull {
            it is ModerationCause.Blocking || it is ModerationCause.BlockedBy || it is ModerationCause.BlockOther
        }
    val muteCause: ModerationCause?
        get() = causes.firstOrNull { it is ModerationCause.Muted }
    val labelCauses: List<ModerationCause.LabelCause>
        get() = causes.filterIsInstance<ModerationCause.LabelCause>()

    fun downgrade(): ModerationDecision {
        for (cause in causes) {
            cause.downgraded = true
        }
        return this
    }

    fun ui(context: ModerationContext): ModerationUI {
        val ui = ModerationUI()
        for (cause in causes) {
            when (cause) {
                is ModerationCause.Blocking,
                is ModerationCause.BlockedBy,
                is ModerationCause.BlockOther,
                -> {
                    if (isMe) continue
                    if (context == ModerationContext.profileList || context == ModerationContext.contentList) {
                        ui.filters.add(cause)
                    }
                    if (!cause.downgraded) {
                        when (BLOCK_BEHAVIOR[context]) {
                            ModerationBehaviorValue.blur -> {
                                ui.noOverride = true
                                ui.blurs.add(cause)
                            }
                            ModerationBehaviorValue.alert -> ui.alerts.add(cause)
                            ModerationBehaviorValue.inform -> ui.informs.add(cause)
                            null -> Unit
                        }
                    }
                }
                is ModerationCause.Muted -> {
                    if (isMe) continue
                    if (context == ModerationContext.profileList || context == ModerationContext.contentList) {
                        ui.filters.add(cause)
                    }
                    if (!cause.downgraded) {
                        when (MUTE_BEHAVIOR[context]) {
                            ModerationBehaviorValue.blur -> ui.blurs.add(cause)
                            ModerationBehaviorValue.alert -> ui.alerts.add(cause)
                            ModerationBehaviorValue.inform -> ui.informs.add(cause)
                            null -> Unit
                        }
                    }
                }
                is ModerationCause.MuteWord -> {
                    if (isMe) continue
                    if (context == ModerationContext.contentList) {
                        ui.filters.add(cause)
                    }
                    if (!cause.downgraded) {
                        when (MUTEWORD_BEHAVIOR[context]) {
                            ModerationBehaviorValue.blur -> ui.blurs.add(cause)
                            ModerationBehaviorValue.alert -> ui.alerts.add(cause)
                            ModerationBehaviorValue.inform -> ui.informs.add(cause)
                            null -> Unit
                        }
                    }
                }
                is ModerationCause.Hidden -> {
                    if (context == ModerationContext.profileList || context == ModerationContext.contentList) {
                        ui.filters.add(cause)
                    }
                    if (!cause.downgraded) {
                        when (HIDE_BEHAVIOR[context]) {
                            ModerationBehaviorValue.blur -> ui.blurs.add(cause)
                            ModerationBehaviorValue.alert -> ui.alerts.add(cause)
                            ModerationBehaviorValue.inform -> ui.informs.add(cause)
                            null -> Unit
                        }
                    }
                }
                is ModerationCause.LabelCause -> {
                    if (context == ModerationContext.profileList && cause.target == LabelTarget.account) {
                        if (cause.setting == LabelPreference.hide && !isMe) {
                            ui.filters.add(cause)
                        }
                    } else if (context == ModerationContext.contentList &&
                        (cause.target == LabelTarget.account || cause.target == LabelTarget.content)
                    ) {
                        if (cause.setting == LabelPreference.hide && !isMe) {
                            ui.filters.add(cause)
                        }
                    }
                    if (!cause.downgraded) {
                        when (cause.behavior[context]) {
                            ModerationBehaviorValue.blur -> {
                                ui.blurs.add(cause)
                                if (cause.noOverride && !isMe) {
                                    ui.noOverride = true
                                }
                            }
                            ModerationBehaviorValue.alert -> ui.alerts.add(cause)
                            ModerationBehaviorValue.inform -> ui.informs.add(cause)
                            null -> Unit
                        }
                    }
                }
            }
        }
        ui.filters.sortBy { it.priority }
        ui.blurs.sortBy { it.priority }
        return ui
    }

    fun addHidden(hidden: Boolean) {
        if (hidden) causes.add(ModerationCause.Hidden())
    }

    fun addMutedWord(matches: List<String>?) {
        if (!matches.isNullOrEmpty()) causes.add(ModerationCause.MuteWord(matches = matches))
    }

    fun addBlocking(blocking: Boolean) {
        if (blocking) causes.add(ModerationCause.Blocking(source = ModerationCauseSource.User))
    }

    fun addBlockingByList(list: ListViewBasic?) {
        if (list != null) causes.add(ModerationCause.Blocking(source = ModerationCauseSource.List(list)))
    }

    fun addBlockedBy(blockedBy: Boolean) {
        if (blockedBy) causes.add(ModerationCause.BlockedBy())
    }

    fun addMuted(muted: Boolean) {
        if (muted) causes.add(ModerationCause.Muted(source = ModerationCauseSource.User))
    }

    fun addMutedByList(list: ListViewBasic?) {
        if (list != null) causes.add(ModerationCause.Muted(source = ModerationCauseSource.List(list)))
    }

    fun addLabel(target: LabelTarget, label: Label, opts: ModerationOpts) {
        val knownDef = KNOWN_LABELS[label.`val`]
        val labelDef = if (CUSTOM_LABEL_VALUE_RE.matches(label.`val`)) {
            opts.labelDefs[label.src]?.firstOrNull { it.identifier == label.`val` } ?: knownDef
        } else {
            knownDef
        } ?: return

        val isSelf = label.src == did
        val labeler = if (isSelf) null else opts.prefs.labelers.firstOrNull { it.did == label.src }
        if (!isSelf && labeler == null) return
        if (isSelf && "no-self" in labelDef.flags) return

        var labelPref = labelDef.defaultSetting
        if (!labelDef.configurable) {
            labelPref = labelDef.defaultSetting
        } else if ("adult" in labelDef.flags && !opts.prefs.adultContentEnabled) {
            labelPref = LabelPreference.hide
        } else {
            labelPref = labeler?.labels?.get(labelDef.identifier)
                ?: opts.prefs.labels[labelDef.identifier]
                ?: labelDef.defaultSetting
        }

        if (labelPref == LabelPreference.ignore) return
        if ("unauthed" in labelDef.flags && !opts.userDid.isNullOrEmpty()) return

        val behavior = labelDef.behaviors[target].orEmpty()
        val severity = measureSeverity(behavior)
        val priority = when {
            "no-override" in labelDef.flags || ("adult" in labelDef.flags && !opts.prefs.adultContentEnabled) -> 1
            labelPref == LabelPreference.hide -> 2
            severity == 0 -> 5
            severity == 1 -> 7
            else -> 8
        }
        val noOverride = "no-override" in labelDef.flags || ("adult" in labelDef.flags && !opts.prefs.adultContentEnabled)

        causes.add(
            ModerationCause.LabelCause(
                source = if (isSelf || labeler == null) ModerationCauseSource.User else ModerationCauseSource.Labeler(labeler.did),
                label = label,
                labelDef = labelDef,
                target = target,
                setting = labelPref,
                behavior = behavior,
                noOverride = noOverride,
                priority = priority,
            )
        )
    }

    private fun measureSeverity(behavior: Map<ModerationContext, ModerationBehaviorValue>): Int {
        if (behavior[ModerationContext.profileView] == ModerationBehaviorValue.blur ||
            behavior[ModerationContext.contentView] == ModerationBehaviorValue.blur
        ) {
            return 0 // High
        }
        if (behavior[ModerationContext.contentList] == ModerationBehaviorValue.blur ||
            behavior[ModerationContext.contentMedia] == ModerationBehaviorValue.blur
        ) {
            return 1 // Medium
        }
        return 2 // Low
    }

    companion object {
        fun merge(vararg decisions: ModerationDecision?): ModerationDecision {
            val nonNull = decisions.filterNotNull()
            val merged = ModerationDecision()
            nonNull.firstOrNull()?.let {
                merged.did = it.did
                merged.isMe = it.isMe
            }
            for (d in nonNull) {
                merged.causes.addAll(d.causes)
            }
            return merged
        }
    }
}

object ModerationEngine {
    private val LANGUAGE_EXCEPTIONS = setOf("ja", "zh", "ko", "th", "vi")
    private val LEADING_TRAILING_PUNCT = Regex("(?:^\\p{P}+|\\p{P}+$)")
    private val PUNCT_OR_SPACE = Regex("[\\s\\p{P}]")
    private val PUNCT = Regex("\\p{P}+")
    private val SPACE = Regex("\\s")
    private val WORD_BOUNDARY = Regex("[\\s\\n\\t\\r\\f\\v]+")

    fun interpretLabelValueDefinition(def: LabelValueDefinition, definedBy: String): InterpretedLabelDef {
        val alertOrInform = when (def.severity) {
            "alert" -> ModerationBehaviorValue.alert
            "inform" -> ModerationBehaviorValue.inform
            else -> null
        }
        val account = mutableMapOf<ModerationContext, ModerationBehaviorValue>()
        val profile = mutableMapOf<ModerationContext, ModerationBehaviorValue>()
        val content = mutableMapOf<ModerationContext, ModerationBehaviorValue>()

        when (def.blurs) {
            "content" -> {
                alertOrInform?.let {
                    account[ModerationContext.profileList] = it
                    account[ModerationContext.profileView] = it
                    profile[ModerationContext.profileList] = it
                    profile[ModerationContext.profileView] = it
                }
                account[ModerationContext.contentList] = ModerationBehaviorValue.blur
                (if (def.adultOnly == true) ModerationBehaviorValue.blur else alertOrInform)?.let {
                    account[ModerationContext.contentView] = it
                    content[ModerationContext.contentView] = it
                }
                content[ModerationContext.contentList] = ModerationBehaviorValue.blur
            }
            "media" -> {
                alertOrInform?.let {
                    account[ModerationContext.profileList] = it
                    account[ModerationContext.profileView] = it
                    profile[ModerationContext.profileList] = it
                    profile[ModerationContext.profileView] = it
                }
                account[ModerationContext.avatar] = ModerationBehaviorValue.blur
                account[ModerationContext.banner] = ModerationBehaviorValue.blur
                profile[ModerationContext.avatar] = ModerationBehaviorValue.blur
                profile[ModerationContext.banner] = ModerationBehaviorValue.blur
                content[ModerationContext.contentMedia] = ModerationBehaviorValue.blur
            }
            "none" -> {
                alertOrInform?.let {
                    account[ModerationContext.profileList] = it
                    account[ModerationContext.profileView] = it
                    account[ModerationContext.contentList] = it
                    account[ModerationContext.contentView] = it
                    profile[ModerationContext.profileList] = it
                    profile[ModerationContext.profileView] = it
                    content[ModerationContext.contentList] = it
                    content[ModerationContext.contentView] = it
                }
            }
        }
        val defaultSetting = when (def.defaultSetting) {
            "hide" -> LabelPreference.hide
            "ignore" -> LabelPreference.ignore
            else -> LabelPreference.warn
        }
        val flags = buildSet {
            add("no-self")
            if (def.adultOnly == true) add("adult")
        }
        val locale = def.locales.firstOrNull { it.lang.startsWith("en") } ?: def.locales.firstOrNull()
        return InterpretedLabelDef(
            identifier = def.identifier,
            configurable = true,
            defaultSetting = defaultSetting,
            flags = flags,
            severity = def.severity,
            blurs = def.blurs,
            behaviors = mapOf(
                LabelTarget.account to account,
                LabelTarget.profile to profile,
                LabelTarget.content to content,
            ),
            definedBy = definedBy,
            title = locale?.name,
            description = locale?.description,
        )
    }

    fun moderateProfile(profile: ProfileViewBasic, opts: ModerationOpts): ModerationDecision {
        return ModerationDecision.merge(
            decideAccount(profile.did, profile.viewer?.muted == true, profile.viewer?.mutedByList, !profile.viewer?.blocking.isNullOrEmpty(), profile.viewer?.blockingByList, profile.viewer?.blockedBy == true, profile.labels, opts),
            decideProfile(profile.did, profile.labels, opts),
        )
    }

    fun moderateProfile(profile: ProfileView, opts: ModerationOpts): ModerationDecision {
        return ModerationDecision.merge(
            decideAccount(profile.did, profile.viewer?.muted == true, profile.viewer?.mutedByList, !profile.viewer?.blocking.isNullOrEmpty(), profile.viewer?.blockingByList, profile.viewer?.blockedBy == true, profile.labels, opts),
            decideProfile(profile.did, profile.labels, opts),
        )
    }

    fun moderateProfile(profile: ProfileViewDetailed, opts: ModerationOpts): ModerationDecision {
        return ModerationDecision.merge(
            decideAccount(profile.did, profile.viewer?.muted == true, profile.viewer?.mutedByList, !profile.viewer?.blocking.isNullOrEmpty(), profile.viewer?.blockingByList, profile.viewer?.blockedBy == true, profile.labels, opts),
            decideProfile(profile.did, profile.labels, opts),
        )
    }

    fun moderatePost(post: PostView, opts: ModerationOpts): ModerationDecision {
        val subjectDecision = ModerationDecision().apply {
            did = post.author.did
            isMe = post.author.did == opts.userDid
            for (label in post.labels) {
                addLabel(LabelTarget.content, label, opts)
            }
            addHidden(checkHiddenPost(post, opts.prefs.hiddenPosts))
            if (!isMe) {
                addMutedWord(matchAllMuteWords(post, opts.prefs.mutedWords))
            }
        }
        val embedDecision = decideEmbed(post.embed, opts)?.downgrade()
        val accountDecision = decideAccount(
            post.author.did,
            post.author.viewer?.muted == true,
            post.author.viewer?.mutedByList,
            !post.author.viewer?.blocking.isNullOrEmpty(),
            post.author.viewer?.blockingByList,
            post.author.viewer?.blockedBy == true,
            post.author.labels,
            opts,
        )
        val profileDecision = decideProfile(post.author.did, post.author.labels, opts)
        return ModerationDecision.merge(subjectDecision, embedDecision, accountDecision, profileDecision)
    }

    private fun decideAccount(
        did: String,
        muted: Boolean,
        mutedByList: ListViewBasic?,
        blocking: Boolean,
        blockingByList: ListViewBasic?,
        blockedBy: Boolean,
        labels: List<Label>,
        opts: ModerationOpts,
    ): ModerationDecision = ModerationDecision().apply {
        this.did = did
        this.isMe = did == opts.userDid
        if (muted) {
            if (mutedByList != null) addMutedByList(mutedByList) else addMuted(true)
        }
        if (blocking) {
            if (blockingByList != null) addBlockingByList(blockingByList) else addBlocking(true)
        }
        addBlockedBy(blockedBy)
        for (label in labels) {
            if (!label.uri.endsWith("/app.bsky.actor.profile/self") || label.`val` == "!no-unauthenticated") {
                addLabel(LabelTarget.account, label, opts)
            }
        }
    }

    private fun decideProfile(did: String, labels: List<Label>, opts: ModerationOpts): ModerationDecision =
        ModerationDecision().apply {
            this.did = did
            this.isMe = did == opts.userDid
            for (label in labels) {
                if (label.uri.endsWith("/app.bsky.actor.profile/self")) {
                    addLabel(LabelTarget.profile, label, opts)
                }
            }
        }

    private fun decideEmbed(embed: JsonObject?, opts: ModerationOpts): ModerationDecision? {
        if (embed == null) return null
        val type = embed["\$type"]?.jsonPrimitive?.contentOrNull ?: return null
        val recordObj: JsonObject? = when (type) {
            "app.bsky.embed.record#view" -> embed["record"]?.jsonObject
            "app.bsky.embed.recordWithMedia#view" -> embed["record"]?.jsonObject?.get("record")?.jsonObject
            else -> null
        }
        if (recordObj == null) return null
        val recType = recordObj["\$type"]?.jsonPrimitive?.contentOrNull ?: return null
        return when (recType) {
            "app.bsky.embed.record#viewRecord" -> decideQuotedPost(recordObj, opts)
            "app.bsky.embed.record#viewBlocked" -> decideBlockedQuotedPost(recordObj, opts)
            else -> null
        }
    }

    private fun decideQuotedPost(recordObj: JsonObject, opts: ModerationOpts): ModerationDecision? {
        val authorJson = recordObj["author"] as? JsonObject ?: return null
        val author = runCatching {
            AtProtoJson.decodeFromJsonElement(ProfileViewBasic.serializer(), authorJson)
        }.getOrNull() ?: return null
        val labels = (recordObj["labels"] as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement(Label.serializer(), it) }.getOrNull()
        }.orEmpty()
        val acc = ModerationDecision().apply {
            did = author.did
            isMe = author.did == opts.userDid
            for (label in labels) {
                addLabel(LabelTarget.content, label, opts)
            }
        }
        val accountDec = decideAccount(
            author.did,
            author.viewer?.muted == true,
            author.viewer?.mutedByList,
            !author.viewer?.blocking.isNullOrEmpty(),
            author.viewer?.blockingByList,
            author.viewer?.blockedBy == true,
            author.labels,
            opts,
        )
        val profileDec = decideProfile(author.did, author.labels, opts)
        return ModerationDecision.merge(acc, accountDec, profileDec)
    }

    private fun decideBlockedQuotedPost(recordObj: JsonObject, opts: ModerationOpts): ModerationDecision? {
        val authorJson = recordObj["author"]?.jsonObject ?: return null
        val did = authorJson["did"]?.jsonPrimitive?.contentOrNull ?: return null
        val viewer = authorJson["viewer"]?.jsonObject
        return ModerationDecision().apply {
            this.did = did
            this.isMe = did == opts.userDid
            if (viewer?.get("muted")?.jsonPrimitive?.contentOrNull == "true") {
                addMuted(true)
            }
            if (!viewer?.get("blocking")?.jsonPrimitive?.contentOrNull.isNullOrEmpty()) {
                addBlocking(true)
            }
            if (viewer?.get("blockedBy")?.jsonPrimitive?.contentOrNull == "true") {
                addBlockedBy(true)
            }
        }
    }

    private fun checkHiddenPost(post: PostView, hiddenPosts: List<String>): Boolean {
        if (hiddenPosts.isEmpty()) return false
        if (post.uri in hiddenPosts) return true
        val embed = post.embed ?: return false
        val type = embed["\$type"]?.jsonPrimitive?.contentOrNull
        val recordObj = when (type) {
            "app.bsky.embed.record#view" -> embed["record"]?.jsonObject
            "app.bsky.embed.recordWithMedia#view" -> embed["record"]?.jsonObject?.get("record")?.jsonObject
            else -> null
        }
        if (recordObj?.get("\$type")?.jsonPrimitive?.contentOrNull == "app.bsky.embed.record#viewRecord") {
            val quotedUri = recordObj["uri"]?.jsonPrimitive?.contentOrNull
            if (quotedUri != null && quotedUri in hiddenPosts) return true
        }
        return false
    }

    private fun matchAllMuteWords(post: PostView, mutedWords: List<MutedWord>): List<String>? {
        if (mutedWords.isEmpty()) return null
        val record = post.record
        val text = record["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val langs = (record["langs"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val tags = (record["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val facets = record["facets"] as? JsonArray
        val isFollowing = !post.author.viewer?.following.isNullOrEmpty()

        matchMuteWords(mutedWords, text, facets, tags, langs, isFollowing)?.let { return it }

        // Own images / gallery alt text
        val recEmbed = record["embed"]?.jsonObject
        matchRecordEmbedAltText(mutedWords, recEmbed, langs, isFollowing)?.let { return it }

        // Link card & quoted post
        val embed = post.embed
        if (embed != null) {
            val eType = embed["\$type"]?.jsonPrimitive?.contentOrNull
            if (eType == "app.bsky.embed.external#view") {
                val ext = embed["external"]?.jsonObject
                val extText = "${ext?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()} ${ext?.get("description")?.jsonPrimitive?.contentOrNull.orEmpty()}"
                matchMuteWords(mutedWords, extText, null, emptyList(), emptyList(), isFollowing)?.let { return it }
            }
            val quotedRecordObj = when (eType) {
                "app.bsky.embed.record#view" -> embed["record"]?.jsonObject
                "app.bsky.embed.recordWithMedia#view" -> embed["record"]?.jsonObject?.get("record")?.jsonObject
                else -> null
            }
            if (quotedRecordObj?.get("\$type")?.jsonPrimitive?.contentOrNull == "app.bsky.embed.record#viewRecord") {
                val qFollowing = !quotedRecordObj["author"]?.jsonObject?.get("viewer")?.jsonObject?.get("following")?.jsonPrimitive?.contentOrNull.isNullOrEmpty()
                val qValue = quotedRecordObj["value"]?.jsonObject
                if (qValue != null) {
                    val qText = qValue["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val qLangs = (qValue["langs"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                    val qTags = (qValue["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                    val qFacets = qValue["facets"] as? JsonArray
                    matchMuteWords(mutedWords, qText, qFacets, qTags, qLangs, qFollowing)?.let { return it }
                    matchRecordEmbedAltText(mutedWords, qValue["embed"]?.jsonObject, qLangs, qFollowing)?.let { return it }
                }
            }
        }
        return null
    }

    private fun matchRecordEmbedAltText(
        mutedWords: List<MutedWord>,
        embed: JsonObject?,
        langs: List<String>,
        isFollowing: Boolean,
    ): List<String>? {
        if (embed == null) return null
        val type = embed["\$type"]?.jsonPrimitive?.contentOrNull ?: return null
        when (type) {
            "app.bsky.embed.images" -> {
                val images = embed["images"] as? JsonArray ?: return null
                for (img in images) {
                    val alt = img.jsonObject["alt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    matchMuteWords(mutedWords, alt, null, emptyList(), langs, isFollowing)?.let { return it }
                }
            }
            "app.bsky.embed.gallery" -> {
                val items = embed["items"] as? JsonArray ?: return null
                for (item in items) {
                    val alt = item.jsonObject["alt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    matchMuteWords(mutedWords, alt, null, emptyList(), langs, isFollowing)?.let { return it }
                }
            }
            "app.bsky.embed.external" -> {
                val ext = embed["external"]?.jsonObject
                val extText = "${ext?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()} ${ext?.get("description")?.jsonPrimitive?.contentOrNull.orEmpty()}"
                matchMuteWords(mutedWords, extText, null, emptyList(), emptyList(), isFollowing)?.let { return it }
            }
            "app.bsky.embed.recordWithMedia" -> {
                matchRecordEmbedAltText(mutedWords, embed["media"]?.jsonObject, emptyList(), isFollowing)?.let { return it }
            }
        }
        return null
    }

    fun matchMuteWords(
        mutedWords: List<MutedWord>,
        text: String,
        facets: JsonArray?,
        outlineTags: List<String>,
        languages: List<String>,
        authorIsFollowing: Boolean,
        nowIso: String = Instant.now().toString(),
    ): List<String>? {
        if (mutedWords.isEmpty()) return null
        val postText = text.lowercase()
        val exception = (languages.firstOrNull() ?: "") in LANGUAGE_EXCEPTIONS
        val tags = buildList {
            for (t in outlineTags) add(t.lowercase())
            if (facets != null) {
                for (f in facets) {
                    val feats = f.jsonObject["features"] as? JsonArray ?: continue
                    for (feat in feats) {
                        val obj = feat.jsonObject
                        if (obj["\$type"]?.jsonPrimitive?.contentOrNull == "app.bsky.richtext.facet#tag") {
                            obj["tag"]?.jsonPrimitive?.contentOrNull?.let { add(it.lowercase()) }
                        }
                    }
                }
            }
        }
        val matches = mutableListOf<String>()
        var words: List<String>? = null
        val trimmedCache = mutableMapOf<Int, String>()
        val variantsCache = mutableMapOf<Int, List<String>?>()

        outer@ for (muteWord in mutedWords) {
            val wordLower = muteWord.value.lowercase()
            if (!muteWord.expiresAt.isNullOrEmpty() && muteWord.expiresAt < nowIso) continue
            if (muteWord.actorTarget == "exclude-following" && authorIsFollowing) continue

            if (wordLower in tags) {
                matches.add(muteWord.value)
                continue
            }
            if ("content" !in muteWord.targets) continue

            if ((wordLower.length == 1 || exception) && postText.contains(wordLower)) {
                matches.add(muteWord.value)
                continue
            }
            if (wordLower.length > postText.length) continue
            if (wordLower == postText) {
                matches.add(muteWord.value)
                continue
            }
            if (PUNCT_OR_SPACE.containsMatchIn(wordLower) && postText.contains(wordLower)) {
                matches.add(muteWord.value)
                continue
            }

            if (words == null) {
                words = postText.split(WORD_BOUNDARY)
            }
            for (i in words.indices) {
                val w = words[i]
                if (w == wordLower) {
                    matches.add(muteWord.value)
                    continue@outer
                }
                val trimmed = trimmedCache.getOrPut(i) { w.replace(LEADING_TRAILING_PUNCT, "") }
                if (wordLower == trimmed) {
                    matches.add(muteWord.value)
                    continue@outer
                }
                if (wordLower.length > trimmed.length) continue

                val variants = variantsCache.getOrPut(i) {
                    if (PUNCT.containsMatchIn(trimmed)) {
                        if (trimmed.contains('/')) return@getOrPut null
                        val spaced = trimmed.replace(PUNCT, " ")
                        buildList {
                            add(spaced)
                            add(spaced.replace(SPACE, ""))
                            addAll(trimmed.split(PUNCT))
                        }
                    } else {
                        null
                    }
                }
                if (trimmed.contains('/')) continue
                if (variants != null && wordLower in variants) {
                    matches.add(muteWord.value)
                    continue@outer
                }
            }
        }
        return matches.takeIf { it.isNotEmpty() }
    }
}
