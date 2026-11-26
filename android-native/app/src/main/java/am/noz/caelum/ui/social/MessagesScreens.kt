package am.noz.caelum.ui.social

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LinkMismatchWarningDialog
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.ShellIcons
import am.noz.caelum.ui.components.VerificationBadge
import am.noz.caelum.ui.components.bskyVector
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.post.QuotedRecordCard
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private data class ChatReactionItem(
    val value: String,
    val senderDid: String,
)

private data class ChatMessageItem(
    val id: String,
    val rev: String,
    val senderDid: String,
    val sentAt: String,
    val text: String,
    val facets: List<RichTextFacet>,
    val embed: JsonObject?,
    val reactions: List<ChatReactionItem>,
    val isDeleted: Boolean,
)

private val QUICK_REACTION_EMOJIS = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

private val ChatInboxIcon = bskyVector(
    "Inbox_Stroke2_Corner2_Rounded",
    "M19 14h-2.417a5 5 0 0 1-9.166 0H5v4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4Zm0-8a1 1 0 0 0-.898-.995L18 5H6a1 1 0 0 0-1 1v6h3.126a1 1 0 0 1 .969.751 3.001 3.001 0 0 0 5.81 0l.056-.16a1 1 0 0 1 .913-.591H19V6Zm2 12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3h12l.154.004A3 3 0 0 1 21 6v12Z",
    pathFillType = PathFillType.EvenOdd,
)

private val ChatSettingsIcon = bskyVector(
    "SettingsGear2_Stroke2_Corner0_Rounded",
    "M11.1 2a1 1 0 0 0-.832.445L8.851 4.57 6.6 4.05a1 1 0 0 0-.932.268l-1.35 1.35a1 1 0 0 0-.267.932l.52 2.251-2.126 1.417A1 1 0 0 0 2 11.1v1.8a1 1 0 0 0 .445.832l2.125 1.417-.52 2.251a1 1 0 0 0 .268.932l1.35 1.35a1 1 0 0 0 .932.267l2.251-.52 1.417 2.126A1 1 0 0 0 11.1 22h1.8a1 1 0 0 0 .832-.445l1.417-2.125 2.251.52a1 1 0 0 0 .932-.268l1.35-1.35a1 1 0 0 0 .267-.932l-.52-2.251 2.126-1.417A1 1 0 0 0 22 12.9v-1.8a1 1 0 0 0-.445-.832L19.43 8.851l.52-2.251a1 1 0 0 0-.268-.932l-1.35-1.35a1 1 0 0 0-.932-.267l-2.251.52-1.417-2.126A1 1 0 0 0 12.9 2h-1.8Zm-.968 4.255L11.635 4h.73l1.503 2.255a1 1 0 0 0 1.057.42l2.385-.551.566.566-.55 2.385a1 1 0 0 0 .42 1.057L20 11.635v.73l-2.255 1.503a1 1 0 0 0-.42 1.057l.551 2.385-.566.566-2.385-.55a1 1 0 0 0-1.057.42L12.365 20h-.73l-1.503-2.255a1 1 0 0 0-1.057-.42l-2.385.551-.566-.566.55-2.385a1 1 0 0 0-.42-1.057L4 12.365v-.73l2.255-1.503a1 1 0 0 0 .42-1.057L6.123 6.69l.566-.566 2.385.55a1 1 0 0 0 1.057-.42ZM8 12a4 4 0 1 1 8 0 4 4 0 0 1-8 0Zm4-2a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z",
    pathFillType = PathFillType.EvenOdd,
)

private val NewChatIcon = bskyVector(
    "MessagePlus_Stroke2_Corner0_Rounded",
    "M19.55 12a7.55 7.55 0 1 0-14.33 3.329c.142.287.193.623.125.957l-.618 3.007 3.131-.585.12-.018c.277-.029.552.022.793.137A7.55 7.55 0 0 0 19.55 12Zm2 0a9.55 9.55 0 0 1-13.48 8.703l-3.741.7a1.476 1.476 0 0 1-1.717-1.746l.741-3.608A9.55 9.55 0 1 1 21.55 12ZM11 14.85V13H9.15a1 1 0 1 1 0-2H11V9.15a1 1 0 0 1 2 0V11h1.85a1 1 0 0 1 0 2H13v1.85a1 1 0 0 1-2 0Z",
    pathFillType = PathFillType.EvenOdd,
)

private fun parseChatMessage(obj: JsonObject): ChatMessageItem? {
    val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return null
    val rev = (obj["rev"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val type = (obj["\$type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val senderDid = (obj["sender"] as? JsonObject)?.get("did")?.jsonPrimitive?.contentOrNull.orEmpty()
    val sentAt = (obj["sentAt"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val isDeleted = type == "chat.bsky.convo.defs#deletedMessageView"
    val text = if (isDeleted) "" else (obj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val facets = (obj["facets"] as? JsonArray)?.mapNotNull {
        runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
    }.orEmpty()
    val embed = obj["embed"] as? JsonObject
    val reactions = (obj["reactions"] as? JsonArray)?.mapNotNull { r ->
        val rObj = r as? JsonObject ?: return@mapNotNull null
        val value = (rObj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val rSender = (rObj["sender"] as? JsonObject)?.get("did")?.jsonPrimitive?.contentOrNull.orEmpty()
        ChatReactionItem(value = value, senderDid = rSender)
    }.orEmpty()
    return ChatMessageItem(
        id = id,
        rev = rev,
        senderDid = senderDid,
        sentAt = sentAt,
        text = text,
        facets = facets,
        embed = embed,
        reactions = reactions,
        isDeleted = isDeleted,
    )
}

private fun formatChatErrorMessage(ex: Throwable): String {
    if (ex is XrpcException) {
        return when {
            ex.isChatScopeOrPermissionError ->
                "Direct messages require chat permission. If you signed in with an App Password, enable “Allow access to your direct messages”."
            ex.isInvalidToken || ex.isExpiredToken || ex.statusCode == 401 ->
                "Unable to authenticate with chat (${ex.error ?: "HTTP ${ex.statusCode}"}). Please try again or re-sign in."
            else -> ex.message.ifBlank { "Unable to load messages (HTTP ${ex.statusCode})" }
        }
    }
    return ex.message?.takeIf { it.isNotBlank() } ?: "Unable to load messages"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessagesListScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    markAllReadEvents: Flow<String> = emptyFlow(),
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val listState = screenCache.rememberCachedLazyListState("messages_list")

    val cachedConvos = remember(session.currentDid) { screenCache.messagesConvos }
    val cachedRequestConvos = remember(session.currentDid) { screenCache.messagesRequestConvos }
    var convos by remember(session.currentDid) { mutableStateOf(cachedConvos ?: emptyList()) }
    var cursor by remember(session.currentDid) { mutableStateOf(screenCache.messagesCursor) }
    var requestCount by remember(session.currentDid) { mutableStateOf(cachedRequestConvos.orEmpty().count { it.unreadCount > 0 }) }
    var isLoading by remember(session.currentDid) { mutableStateOf(cachedConvos == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showChatOptions by remember { mutableStateOf(false) }
    var convoMenu by remember(session.currentDid) { mutableStateOf<ConvoView?>(null) }
    var leaveConvo by remember(session.currentDid) { mutableStateOf<ConvoView?>(null) }
    val actionFailed = tr("Something went wrong")

    fun updateCachedConvos(updated: List<ConvoView>) {
        convos = updated
        screenCache.messagesConvos = updated
    }

    fun handleMarkAllRead(status: String) {
        if (status == "request") {
            requestCount = 0
            screenCache.messagesRequestConvos = screenCache.messagesRequestConvos?.map { it.copy(unreadCount = 0) }
        } else {
            updateCachedConvos(convos.map { it.copy(unreadCount = 0) })
        }
    }

    LaunchedEffect(markAllReadEvents, session.currentDid) {
        markAllReadEvents.collect { handleMarkAllRead(it) }
    }

    fun markAllRead(status: String) {
        scope.launch {
            runCatching { container.api.updateAllReadConvos(status) }
                .onSuccess {
                    handleMarkAllRead(status)
                    container.sessionManager.refreshUnreadCounts()
                }
                .onFailure { Toast.makeText(context, actionFailed, Toast.LENGTH_SHORT).show() }
        }
    }

    suspend fun loadConvos(refresh: Boolean = false, loadMore: Boolean = false) {
        if (!session.hasSession) return
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        isLoading = !refresh && !loadMore && convos.isEmpty()
        isRefreshing = refresh
        isLoadingMore = loadMore
        errorMsg = null

        runCatching {
            container.sessionManager.ensurePdsResolvedForCurrentAccount()
            val activeResp = container.api.listConvos(
                status = "accepted",
                cursor = if (loadMore) cursor else null,
            )
            if (!loadMore) {
                val reqResp = runCatching { container.api.listConvos(status = "request") }.getOrNull()
                if (reqResp != null) {
                    requestCount = reqResp.convos.count { it.unreadCount > 0 }
                    screenCache.messagesRequestConvos = reqResp.convos
                }
            }
            activeResp
        }.onSuccess { resp ->
            val updated = if (loadMore) {
                (convos + resp.convos).distinctBy { it.id }
            } else {
                resp.convos
            }
            updateCachedConvos(updated)
            cursor = resp.cursor
            screenCache.messagesCursor = resp.cursor
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
        }.onFailure { ex ->
            errorMsg = formatChatErrorMessage(ex)
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(session.currentDid) {
        if (session.hasSession && screenCache.messagesConvos == null) {
            loadConvos(refresh = false)
        }
    }

    val shouldLoadMore by remember(listState, convos.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            convos.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= convos.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadConvos(loadMore = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("messages_drawer_button"),
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = tr("Open drawer menu"))
                    }
                },
                title = {
                    Text(
                        text = tr("Chats"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    val requestLabel = tr("Chat requests")
                    if (requestCount > 0) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = requestLabel) { onNavigate(AppRoute.MessagesInbox) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier.background(palette.primary50.toComposeColor(), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(ChatInboxIcon, contentDescription = requestLabel, tint = palette.primary700.toComposeColor())
                                Text(requestCount.toString(), color = palette.primary700.toComposeColor(), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        IconButton(onClick = { onNavigate(AppRoute.MessagesInbox) }) {
                            Icon(ChatInboxIcon, contentDescription = requestLabel)
                        }
                    }
                    IconButton(onClick = { showChatOptions = true }) {
                        Icon(ChatSettingsIcon, contentDescription = tr("Chat options"))
                    }
                },
            )
        },
        floatingActionButton = {
            if (session.hasSession) {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = palette.primary500.toComposeColor(),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("messages_new_chat_fab"),
                ) {
                    Icon(NewChatIcon, contentDescription = tr("New chat"))
                }
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (!session.hasSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sign in to view your messages.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.contrast600.toComposeColor(),
                )
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadConvos(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null && convos.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { loadConvos(refresh = true) } }) {
                            Text("Try again")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("messages_list"),
                        contentPadding = PaddingValues(bottom = 84.dp),
                    ) {
                        if (convos.isEmpty()) {
                            item(key = "empty_convos") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "No conversations yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.contrast800.toComposeColor(),
                                    )
                                    Text(
                                        text = "Tap the button below to start a new chat.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.contrast600.toComposeColor(),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Button(onClick = { showNewChatDialog = true }) {
                                        Text("Start a chat")
                                    }
                                }
                            }
                        } else {
                            items(items = convos, key = { it.id }) { convo ->
                                ConversationIdentity(
                                    convo = convo,
                                    currentDid = session.currentDid,
                                    moderation = session.moderationOpts,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (convo.unreadCount > 0) palette.primary25.toComposeColor()
                                            else palette.contrast0.toComposeColor(),
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (convo.directPeer(session.currentDid)?.handle == "missing.invalid") {
                                                    convoMenu = convo
                                                } else {
                                                    onNavigate(AppRoute.MessagesConversation(convo.id))
                                                }
                                            },
                                            onLongClickLabel = tr("Open conversation options"),
                                            onLongClick = {
                                                haptics.longPress()
                                                convoMenu = convo
                                            },
                                        )
                                        .testTag("chat_row_${convo.id}")
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                            }
                        }

                        if (isLoadingMore) {
                            item(key = "convos_footer_loader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatSearchDialog(
            currentDid = session.currentDid,
            onDismiss = { showNewChatDialog = false },
            onSelectConvo = { convoId ->
                showNewChatDialog = false
                onNavigate(AppRoute.MessagesConversation(convoId))
            },
        )
    }

    if (showChatOptions) {
        ChatActionsSheet(
            onDismiss = { showChatOptions = false },
            actions = listOf(
                ChatMenuAction(Icons.Filled.Check, tr("Mark all chats as read")) { markAllRead("accepted") },
                ChatMenuAction(ChatInboxIcon, tr("Mark all requests as read")) { markAllRead("request") },
                ChatMenuAction(ChatSettingsIcon, tr("Chat settings")) { onNavigate(AppRoute.MessagesSettings) },
            ),
        )
    }

    convoMenu?.let { selected ->
        val convo = convos.firstOrNull { it.id == selected.id } ?: selected
        ChatActionsSheet(
            title = conversationName(convo, session.currentDid, session.moderationOpts),
            onDismiss = { convoMenu = null },
            actions = buildList {
                if (convo.unreadCount > 0) {
                    add(ChatMenuAction(Icons.Filled.Check, tr("Mark as read")) {
                        scope.launch {
                            runCatching { container.api.markConvoRead(convo.id, convo.lastMessage?.stringValue("id")) }
                                .onSuccess {
                                    updateCachedConvos(convos.map { if (it.id == convo.id) it.copy(unreadCount = 0) else it })
                                    container.sessionManager.refreshUnreadCounts()
                                }
                                .onFailure { Toast.makeText(context, actionFailed, Toast.LENGTH_SHORT).show() }
                        }
                    })
                }
                add(ChatMenuAction(
                    icon = if (convo.muted) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    label = if (convo.muted) tr("Unmute conversation") else tr("Mute conversation"),
                ) {
                    scope.launch {
                        runCatching {
                            if (convo.muted) container.api.unmuteConvo(convo.id) else container.api.muteConvo(convo.id)
                        }.onSuccess {
                            updateCachedConvos(convos.map { if (it.id == convo.id) it.copy(muted = !convo.muted) else it })
                        }.onFailure { Toast.makeText(context, actionFailed, Toast.LENGTH_SHORT).show() }
                    }
                })
                add(ChatMenuAction(ChatSettingsIcon, if (convo.isGroupConvo()) tr("Group chat settings") else tr("Chat settings")) {
                    onNavigate(AppRoute.MessagesConversationSettings(convo.id))
                })
                add(ChatMenuAction(Icons.AutoMirrored.Filled.ExitToApp, tr("Leave conversation"), destructive = true) {
                    leaveConvo = convo
                })
            },
        )
    }

    leaveConvo?.let { convo ->
        LeaveConversationDialog(onDismiss = { leaveConvo = null }) {
            scope.launch {
                runCatching { container.api.leaveConvo(convo.id) }.onSuccess {
                    updateCachedConvos(convos.filterNot { it.id == convo.id })
                    container.sessionManager.refreshUnreadCounts()
                }.onFailure { Toast.makeText(context, actionFailed, Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

private data class ChatMenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val run: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatActionsSheet(
    onDismiss: () -> Unit,
    actions: List<ChatMenuAction>,
    title: String? = null,
) {
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var closing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        actions.forEach { action ->
            val color = if (action.destructive) palette.negative500.toComposeColor() else palette.contrast1000.toComposeColor()
            ListItem(
                headlineContent = { Text(action.label, color = color) },
                leadingContent = { Icon(action.icon, contentDescription = null, tint = color) },
                modifier = Modifier.clickable(enabled = !closing) {
                    closing = true
                    scope.launch {
                        state.hide()
                        onDismiss()
                        action.run()
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LeaveConversationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val palette = LocalAlfPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Leave conversation")) },
        text = { Text(tr("Are you sure you want to leave this conversation?")) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(tr("Leave"), color = palette.negative500.toComposeColor())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun NewChatSearchDialog(
    currentDid: String?,
    onDismiss: () -> Unit,
    onSelectConvo: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250L)
        runCatching {
            container.api.searchActorsTypeahead(q, limit = 12)
        }.onSuccess { actors ->
            results = actors.filter { it.did != currentDid }
            isSearching = false
        }.onFailure {
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("New message") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        errorMsg = null
                    },
                    label = { Text("Search people") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.negative500.toComposeColor(),
                    )
                }
                if (isSearching || isCreating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(items = results, key = { it.did }) { actor ->
                            val chatDisallowed = actor.associated?.chat?.allowIncoming == "none"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCreating && !chatDisallowed) {
                                        val myDid = currentDid ?: return@clickable
                                        scope.launch {
                                            isCreating = true
                                            errorMsg = null
                                            runCatching {
                                                container.api.getConvoForMembers(listOf(myDid, actor.did))
                                            }.onSuccess { convo ->
                                                isCreating = false
                                                onSelectConvo(convo.id)
                                            }.onFailure { ex ->
                                                isCreating = false
                                                errorMsg = ex.message ?: "Unable to start chat with @${actor.handle}"
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CaelumAvatar(url = actor.avatar, contentDescription = actor.displayName ?: actor.handle, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    DisplayNameRow(profile = actor, showHandle = true)
                                    if (chatDisallowed) {
                                        Text(
                                            text = "Cannot be messaged",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.contrast500.toComposeColor(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (query.isNotBlank()) {
                    Text(
                        text = "No matching accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Close")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesInboxScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    val cachedRequests = remember(session.currentDid) { screenCache.messagesRequestConvos }
    var requests by remember(session.currentDid) { mutableStateOf(cachedRequests ?: emptyList()) }
    var cursor by remember(session.currentDid) { mutableStateOf<String?>(null) }
    var isLoading by remember(session.currentDid) { mutableStateOf(cachedRequests == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember(session.currentDid) { mutableStateOf(false) }
    var busyConvoId by remember { mutableStateOf<String?>(null) }
    var markingRead by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val markReadError = tr("Failed to mark all requests as read")

    suspend fun loadRequests(refresh: Boolean = false, loadMore: Boolean = false) {
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        isLoading = !refresh && !loadMore && requests.isEmpty()
        isRefreshing = refresh
        isLoadingMore = loadMore
        errorMsg = null
        runCatching {
            container.sessionManager.ensurePdsResolvedForCurrentAccount()
            container.api.listConvos(
                status = "request",
                cursor = if (loadMore) cursor else null,
            )
        }.onSuccess { resp ->
            val merged = if (loadMore) {
                (requests + resp.convos).distinctBy { it.id }
            } else {
                resp.convos.distinctBy { it.id }
            }
            requests = merged
            screenCache.messagesRequestConvos = merged
            cursor = resp.cursor
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            errorMsg = formatChatErrorMessage(ex)
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(session.currentDid) {
        loadRequests()
    }

    val shouldLoadMoreRequests by remember(listState, requests.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            requests.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= requests.size - 4
        }
    }
    LaunchedEffect(shouldLoadMoreRequests) {
        if (shouldLoadMoreRequests) {
            loadRequests(loadMore = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                title = {
                    Text(
                        text = tr("Chat requests"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (requests.any { it.unreadCount > 0 }) {
                        TextButton(
                            enabled = !markingRead,
                            onClick = {
                                markingRead = true
                                scope.launch {
                                    runCatching { container.api.updateAllReadConvos("request") }.onSuccess {
                                        requests = requests.map { it.copy(unreadCount = 0) }
                                        screenCache.messagesRequestConvos = requests
                                        container.sessionManager.refreshUnreadCounts()
                                    }.onFailure { Toast.makeText(context, markReadError, Toast.LENGTH_SHORT).show() }
                                    markingRead = false
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Mark all as read"))
                        }
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadRequests(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null && requests.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { loadRequests(refresh = true) } }) {
                            Text("Try again")
                        }
                    }
                }
                requests.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tr("No chat requests"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.contrast600.toComposeColor(),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        items(items = requests, key = { it.id }) { convo ->
                            val peer = convo.directPeer(session.currentDid)
                            val isBusy = busyConvoId == convo.id

                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(
                                        if (convo.unreadCount > 0) palette.primary25.toComposeColor()
                                        else palette.contrast0.toComposeColor(),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ConversationIdentity(
                                    convo = convo,
                                    currentDid = session.currentDid,
                                    moderation = session.moderationOpts,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onNavigate(AppRoute.MessagesConversation(convo.id)) },
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                busyConvoId = convo.id
                                                runCatching {
                                                    container.api.acceptConvo(convo.id)
                                                    container.sessionManager.refreshUnreadCounts()
                                                }.onSuccess {
                                                    requests = requests.filterNot { it.id == convo.id }
                                                    screenCache.messagesRequestConvos = requests
                                                    screenCache.messagesConvos = null
                                                    busyConvoId = null
                                                    onNavigate(AppRoute.MessagesConversation(convo.id))
                                                }.onFailure {
                                                    busyConvoId = null
                                                }
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(tr("Accept"))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                busyConvoId = convo.id
                                                runCatching {
                                                    container.api.leaveConvo(convo.id)
                                                    container.sessionManager.refreshUnreadCounts()
                                                }.onSuccess {
                                                    requests = requests.filterNot { it.id == convo.id }
                                                    screenCache.messagesRequestConvos = requests
                                                }
                                                busyConvoId = null
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(tr("Reject"))
                                    }
                                    if (peer != null) {
                                        OutlinedButton(
                                            onClick = {
                                                val myDid = session.currentDid ?: return@OutlinedButton
                                                scope.launch {
                                                    busyConvoId = convo.id
                                                    runCatching {
                                                        container.api.blockActor(myDid, peer.did)
                                                        container.api.leaveConvo(convo.id)
                                                        container.sessionManager.refreshUnreadCounts()
                                                    }.onSuccess {
                                                        requests = requests.filterNot { it.id == convo.id }
                                                        screenCache.messagesRequestConvos = requests
                                                    }
                                                    busyConvoId = null
                                                }
                                            },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = palette.negative500.toComposeColor(),
                                            ),
                                        ) {
                                            Text(tr("Block"))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                        if (isLoadingMore) {
                            item(key = "requests_loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessagesConversationScreen(
    convoId: String,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    sharedPost: StrongRef? = null,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var convo by remember(convoId, session.currentDid) { mutableStateOf<ConvoView?>(null) }
    var peerProfile by remember(convoId, session.currentDid) { mutableStateOf<ProfileViewDetailed?>(null) }
    var relatedProfiles by remember(convoId, session.currentDid) { mutableStateOf(emptyMap<String, ProfileViewBasic>()) }
    var messages by remember(convoId, session.currentDid) { mutableStateOf<List<ChatMessageItem>>(emptyList()) }
    var cursor by remember(convoId, session.currentDid) { mutableStateOf<String?>(null) }
    var isLoading by remember(convoId, session.currentDid) { mutableStateOf(true) }
    var isLoadingOlder by remember(convoId, session.currentDid) { mutableStateOf(false) }
    var isSending by remember(convoId, session.currentDid) { mutableStateOf(false) }
    var composerText by rememberSaveable(convoId, session.currentDid) { mutableStateOf("") }
    var includeSharedPost by rememberSaveable(convoId, session.currentDid, sharedPost?.uri, sharedPost?.cid) {
        mutableStateOf(sharedPost != null)
    }
    val attachedPost = sharedPost?.takeIf { includeSharedPost }
    var attachedPreview by remember(attachedPost) { mutableStateOf<PostView?>(null) }
    val sendError = tr("Something went wrong. Please try again.")
    var errorMsg by remember(convoId, session.currentDid) { mutableStateOf<String?>(null) }
    var selectedMessageForActions by remember(convoId, session.currentDid) { mutableStateOf<ChatMessageItem?>(null) }
    var pendingExternalMismatch by remember(convoId, session.currentDid) { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun markConvoRead(latestMsgId: String?) {
        runCatching {
            container.api.markConvoRead(convoId, latestMsgId)
            screenCache.messagesConvos = screenCache.messagesConvos?.map {
                if (it.id == convoId) it.copy(unreadCount = 0) else it
            }
            screenCache.messagesRequestConvos = screenCache.messagesRequestConvos?.map {
                if (it.id == convoId) it.copy(unreadCount = 0) else it
            }
            container.sessionManager.refreshUnreadCounts()
        }
    }

    suspend fun loadInitial() {
        isLoading = true
        errorMsg = null
        runCatching {
            container.sessionManager.ensurePdsResolvedForCurrentAccount()
            val loadedConvo = container.api.getConvo(convoId)
            convo = loadedConvo
            val peer = loadedConvo.directPeer(session.currentDid)
            if (peer != null) {
                runCatching { container.api.getProfile(peer.did) }.getOrNull()?.let {
                    peerProfile = it
                }
            }
            val msgResp = container.api.getMessages(convoId)
            relatedProfiles = (loadedConvo.members + msgResp.relatedProfiles).associateBy { it.did }
            val parsed = msgResp.messages.mapNotNull { parseChatMessage(it) }
            parsed to msgResp.cursor
        }.onSuccess { (parsed, nextCursor) ->
            messages = parsed
            cursor = nextCursor
            isLoading = false
            markConvoRead(parsed.firstOrNull()?.id)
        }.onFailure { ex ->
            errorMsg = formatChatErrorMessage(ex)
            isLoading = false
        }
    }

    suspend fun pollLatest() {
        runCatching {
            val msgResp = container.api.getMessages(convoId)
            relatedProfiles = relatedProfiles + msgResp.relatedProfiles.associateBy { it.did }
            val latestParsed = msgResp.messages.mapNotNull { parseChatMessage(it) }
            val byId = LinkedHashMap<String, ChatMessageItem>()
            for (m in latestParsed) byId[m.id] = m
            for (m in messages) {
                if (!byId.containsKey(m.id)) byId[m.id] = m
            }
            val merged = byId.values.toList()
            val newestBefore = messages.firstOrNull()?.id
            val newestAfter = merged.firstOrNull()?.id
            messages = merged
            if (newestAfter != null && newestAfter != newestBefore) {
                markConvoRead(newestAfter)
            }
        }
    }

    suspend fun loadOlder() {
        val nextCursor = cursor
        if (isLoadingOlder || nextCursor.isNullOrBlank()) return
        isLoadingOlder = true
        runCatching {
            container.api.getMessages(convoId, cursor = nextCursor)
        }.onSuccess { resp ->
            relatedProfiles = relatedProfiles + resp.relatedProfiles.associateBy { it.did }
            val older = resp.messages.mapNotNull { parseChatMessage(it) }
            val existingIds = messages.mapTo(HashSet()) { it.id }
            messages = messages + older.filterNot { it.id in existingIds }
            cursor = resp.cursor
            isLoadingOlder = false
        }.onFailure {
            isLoadingOlder = false
        }
    }

    LaunchedEffect(convoId, session.currentDid) {
        loadInitial()
        while (isActive) {
            delay(5_000L)
            pollLatest()
        }
    }

    LaunchedEffect(attachedPost) {
        if (attachedPost != null) {
            attachedPreview = runCatching { container.api.getPosts(listOf(attachedPost.uri)) }.getOrNull()
                ?.firstOrNull { it.uri == attachedPost.uri && it.cid == attachedPost.cid }
        }
    }

    val shouldLoadOlder by remember(listState, messages.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= messages.size - 4
        }
    }
    LaunchedEffect(shouldLoadOlder) {
        if (shouldLoadOlder) {
            loadOlder()
        }
    }

    val activeConvo = convo
    val isGroup = activeConvo?.isGroupConvo() == true
    val peerBasic = activeConvo?.directPeer(session.currentDid)
    val peerHandle = peerProfile?.handle ?: peerBasic?.handle.orEmpty()
    val conversationTitle = if (activeConvo != null) conversationName(activeConvo, session.currentDid, session.moderationOpts) else tr("Chat")
    val peerAvatar = peerProfile?.avatar ?: peerBasic?.avatar
    val peerPronouns = peerProfile?.pronouns?.takeIf { it.isNotBlank() }
        ?: peerBasic?.pronouns?.takeIf { it.isNotBlank() }
    val peerVerification = peerProfile?.verification ?: peerBasic?.verification
    val peerDid = peerProfile?.did ?: peerBasic?.did
    val peerModeration = peerProfile?.let { ModerationEngine.moderateProfile(it, session.moderationOpts) }
        ?: peerBasic?.let { ModerationEngine.moderateProfile(it, session.moderationOpts) }
    val canWrite = activeConvo != null && activeConvo.status != "request" && !activeConvo.isLockedGroup() &&
        activeConvo.members.any { it.did == session.currentDid } && (isGroup || peerDid != null) &&
        peerModeration?.blocked != true && peerHandle != "missing.invalid"
    val canSend = canWrite && (composerText.isNotBlank() || attachedPost != null) && !isSending

    fun sendCurrentMessage() {
        val raw = composerText.trim()
        val senderDid = session.currentDid ?: return
        val activation = session.activation
        if (!canSend) return
        isSending = true
        scope.launch {
            val result = runCatching {
                withContext(container.sessionManager.bindAccount(senderDid, activation)) {
                    val pdsOrigin = container.sessionManager.currentAuth()?.pdsUrl ?: "https://bsky.social"
                    val facets = RichTextEngine.detectFacets(raw, container.tlds) { handle ->
                        container.identityResolver.resolveHandle(handle, pdsOrigin)
                    }
                    val msgBody = buildJsonObject {
                        put("text", JsonPrimitive(raw))
                        if (facets.isNotEmpty()) {
                            put("facets", AtProtoJson.encodeToJsonElement(ListSerializer(RichTextFacet.serializer()), facets))
                        }
                        attachedPost?.let { post ->
                            put("embed", buildJsonObject {
                                put("\$type", JsonPrimitive("app.bsky.embed.record"))
                                put("record", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), post))
                            })
                        }
                    }
                    container.api.sendMessage(convoId, msgBody)
                }
            }
            if (result.exceptionOrNull() is CancellationException) result.getOrThrow()
            if (container.sessionManager.state.value.activation != activation) return@launch
            result.onSuccess { sentObj ->
                composerText = ""
                includeSharedPost = false
                haptics.confirm()
                val parsed = parseChatMessage(sentObj) ?: (sentObj["message"] as? JsonObject)?.let(::parseChatMessage)
                if (parsed != null) {
                    messages = listOf(parsed) + messages.filterNot { it.id == parsed.id }
                    markConvoRead(parsed.id)
                } else {
                    pollLatest()
                }
                isSending = false
            }.onFailure { ex ->
                isSending = false
                Toast.makeText(context, ex.message ?: sendError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleReaction(msg: ChatMessageItem, emoji: String) {
        if (!canWrite) return
        val myDid = session.currentDid ?: return
        val alreadyReacted = msg.reactions.any { it.value == emoji && it.senderDid == myDid }
        scope.launch {
            runCatching {
                if (alreadyReacted) {
                    container.api.removeMessageReaction(convoId, msg.id, emoji)
                } else {
                    container.api.addMessageReaction(convoId, msg.id, emoji)
                }
            }.onSuccess { res ->
                haptics.tap()
                val updated = (res["message"] as? JsonObject)?.let(::parseChatMessage)
                if (updated != null) {
                    messages = messages.map { if (it.id == updated.id) updated else it }
                } else {
                    pollLatest()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = (isGroup && activeConvo.groupProperty("lockStatus") != "locked-permanently") || peerDid != null,
                                onClickLabel = if (isGroup) tr("Open group chat settings") else tr("View profile"),
                            ) {
                                if (isGroup) {
                                    onNavigate(AppRoute.MessagesConversationSettings(convoId))
                                } else if (peerDid != null) {
                                    onNavigate(AppRoute.Profile(peerBasic?.chatHandle()?.removePrefix("@") ?: peerDid))
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isGroup) {
                            ConversationAvatar(activeConvo, session.currentDid, session.moderationOpts, 40.dp)
                        } else {
                            CaelumAvatar(
                                url = peerAvatar.takeUnless { peerModeration?.ui(ModerationContext.avatar)?.blur == true },
                                contentDescription = null,
                                size = 40.dp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = conversationTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.contrast1000.toComposeColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                VerificationBadge(peerVerification)
                                if (activeConvo?.muted == true) {
                                    Icon(
                                        Icons.Filled.NotificationsOff,
                                        contentDescription = tr("Muted"),
                                        tint = palette.contrast600.toComposeColor(),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (!isGroup && (peerBasic?.chatHandle() != null || !peerPronouns.isNullOrBlank())) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    peerBasic?.chatHandle()?.let { handle ->
                                        Text(
                                            text = handle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.contrast600.toComposeColor(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                    // Caelum customization: DM header pronouns pill next to @handle
                                    if (!peerPronouns.isNullOrBlank()) {
                                        Text(
                                            text = peerPronouns,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.contrast700.toComposeColor(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.contrast50.toComposeColor())
                                                .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    val hasSettings = activeConvo != null && activeConvo.groupProperty("lockStatus") != "locked-permanently"
                    IconButton(
                        enabled = hasSettings,
                        onClick = { onNavigate(AppRoute.MessagesConversationSettings(convoId)) },
                    ) {
                        Icon(
                            ShellIcons.More,
                            contentDescription = if (isGroup) tr("Open group chat settings") else tr("Chat settings"),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.contrast0.toComposeColor())
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                if (convo?.status == "request") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.contrast25.toComposeColor())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Message request",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast700.toComposeColor(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { container.api.leaveConvo(convoId) }
                                        .onSuccess { onBack() }
                                }
                            },
                        ) {
                            Text(tr("Reject"))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        container.api.acceptConvo(convoId)
                                        convo = container.api.getConvo(convoId)
                                    }
                                }
                            },
                        ) {
                            Text(tr("Accept"))
                        }
                    }
                } else if (canWrite) {
                    attachedPost?.let { post ->
                        val preview = attachedPreview
                        val canShowPreview = preview != null && ModerationEngine.moderatePost(preview, session.moderationOpts)
                            .ui(ModerationContext.contentList).let { !it.blur && !it.filter }
                        val previewText = preview?.record?.stringValue("text").orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.contrast25.toComposeColor())
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .clickable {
                                        val author = post.uri.removePrefix("at://").substringBefore('/')
                                        onNavigate(AppRoute.PostThread(author, post.uri.substringAfterLast('/')))
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = if (canShowPreview) chatProfileName(preview.author, session.moderationOpts)
                                    else tr("Post", context = "description"),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (canShowPreview && previewText.isNotBlank()) {
                                    Text(previewText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { includeSharedPost = false }, enabled = !isSending) {
                                Icon(Icons.Filled.Close, contentDescription = tr("Remove attachment"))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val messageInputLabel = tr("Message input field")
                        OutlinedTextField(
                            value = composerText,
                            onValueChange = { composerText = it },
                            placeholder = { Text(tr("Message", context = "description")) },
                            enabled = !isSending,
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dm_composer_input")
                                .semantics { contentDescription = messageInputLabel },
                        )
                        IconButton(
                            onClick = { sendCurrentMessage() },
                            enabled = canSend,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend || isSending) palette.primary500.toComposeColor()
                                    else palette.contrast100.toComposeColor()
                                ),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = tr("Send message"),
                                    tint = if (canSend) Color.White else palette.contrast500.toComposeColor(),
                                )
                            }
                        }
                    }
                } else if (activeConvo != null) {
                    Text(
                        text = if (activeConvo.isLockedGroup()) tr("This chat is locked") else tr("You can read chat history but can’t send new messages."),
                        color = palette.contrast700.toComposeColor(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null && messages.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { loadInitial() } }) {
                        Text("Try again")
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag("dm_messages_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = messages, key = { it.id }) { msg ->
                        val isMine = msg.senderDid == session.currentDid
                        val alignment = if (isMine) Alignment.End else Alignment.Start
                        val bubbleBg = if (isMine) palette.primary500.toComposeColor() else palette.contrast50.toComposeColor()
                        val bubbleTextColor = if (isMine) (palette.onAccent ?: "#FFFFFF").toComposeColor() else palette.contrast1000.toComposeColor()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = alignment,
                        ) {
                            if (isGroup && !isMine && !msg.isDeleted) {
                                val sender = relatedProfiles[msg.senderDid]
                                Text(
                                    text = sender?.let { chatProfileName(it, session.moderationOpts) } ?: tr("Unknown sender"),
                                    color = palette.contrast700.toComposeColor(),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (msg.isDeleted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, palette.contrast200.toComposeColor(), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Message deleted",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = palette.contrast500.toComposeColor(),
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 290.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 18.dp,
                                                topEnd = 18.dp,
                                                bottomStart = if (isMine) 18.dp else 4.dp,
                                                bottomEnd = if (isMine) 4.dp else 18.dp,
                                            )
                                        )
                                        .background(bubbleBg)
                                        .combinedClickable(
                                            onClick = { selectedMessageForActions = msg },
                                            onLongClick = {
                                                haptics.longPress()
                                                selectedMessageForActions = msg
                                            },
                                        )
                                        .padding(horizontal = 13.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (msg.text.isNotBlank()) {
                                        RichTextView(
                                            text = msg.text,
                                            facets = msg.facets,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = bubbleTextColor),
                                            linkColor = if (isMine) bubbleTextColor else palette.primary500.toComposeColor(),
                                            linkDecoration = if (isMine) TextDecoration.Underline else TextDecoration.None,
                                            onMentionClick = { onNavigate(AppRoute.Profile(it)) },
                                            onTagClick = { onNavigate(AppRoute.Hashtag(it)) },
                                            onLinkClick = { disp, uri ->
                                                if (LinkNavigationHelper.isPotentiallyMisleadingLink(disp, uri)) {
                                                    pendingExternalMismatch = disp to uri
                                                } else {
                                                    LinkNavigationHelper.openUrl(
                                                        context = context,
                                                        rawUrl = uri,
                                                        useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                                        openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                                        onInternalRoute = onNavigate,
                                                    )
                                                }
                                            },
                                        )
                                    }

                                    msg.embed?.let { embedObj ->
                                        val rec = embedObj["record"] as? JsonObject ?: embedObj
                                        QuotedRecordCard(record = rec, onNavigate = onNavigate, moderationOpts = session.moderationOpts)
                                    }
                                }

                                // Emoji reaction chips
                                if (msg.reactions.isNotEmpty()) {
                                    val grouped = msg.reactions.groupBy { it.value }
                                    FlowRow(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        grouped.forEach { (emoji, list) ->
                                            val reactedByMe = list.any { it.senderDid == session.currentDid }
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (reactedByMe) palette.primary50.toComposeColor()
                                                        else palette.contrast25.toComposeColor()
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (reactedByMe) palette.primary500.toComposeColor()
                                                        else palette.contrast100.toComposeColor(),
                                                        shape = RoundedCornerShape(12.dp),
                                                    )
                                                    .clickable(enabled = canWrite) { toggleReaction(msg, emoji) }
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Text(text = emoji, fontSize = 13.sp)
                                                if (list.size > 1) {
                                                    Text(
                                                        text = list.size.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = palette.contrast700.toComposeColor(),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = LocalI18n.current.formatRelativeShort(msg.sentAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.contrast500.toComposeColor(),
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                            )
                        }
                    }

                    if (isLoadingOlder) {
                        item(key = "dm_older_loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMessageForActions?.let { msg ->
        ModalBottomSheet(onDismissRequest = { selectedMessageForActions = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                if (canWrite) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        QUICK_REACTION_EMOJIS.forEach { emoji ->
                            val active = msg.reactions.any { it.value == emoji && it.senderDid == session.currentDid }
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (active) palette.primary50.toComposeColor() else palette.contrast50.toComposeColor())
                                    .clickable {
                                        selectedMessageForActions = null
                                        toggleReaction(msg, emoji)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.contrast100.toComposeColor())
                }

                if (msg.text.isNotBlank()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        headlineContent = { Text("Copy message text") },
                        modifier = Modifier.clickable {
                            selectedMessageForActions = null
                            LinkNavigationHelper.copyToClipboard(context, "Message", msg.text)
                        },
                    )
                }

                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = palette.negative500.toComposeColor(),
                        )
                    },
                    headlineContent = {
                        Text("Delete for me", color = palette.negative500.toComposeColor())
                    },
                    modifier = Modifier.clickable {
                        selectedMessageForActions = null
                        scope.launch {
                            runCatching {
                                container.xrpcClient.postJson(
                                    nsid = "chat.bsky.convo.deleteMessageForSelf",
                                    body = buildJsonObject {
                                        put("convoId", JsonPrimitive(convoId))
                                        put("messageId", JsonPrimitive(msg.id))
                                    },
                                )
                            }.onSuccess {
                                messages = messages.filterNot { it.id == msg.id }
                            }.onFailure { ex ->
                                Toast.makeText(context, ex.message ?: "Unable to delete message", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }

    pendingExternalMismatch?.let { (disp, uri) ->
        LinkMismatchWarningDialog(
            displayText = disp,
            targetUri = uri,
            onConfirm = {
                pendingExternalMismatch = null
                LinkNavigationHelper.openUrl(
                    context = context,
                    rawUrl = uri,
                    useLinkRedirects = settings.infrastructure.useLinkRedirects,
                    openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                    onInternalRoute = onNavigate,
                )
            },
            onDismiss = { pendingExternalMismatch = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesConversationSettingsScreen(
    convoId: String,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var convo by remember { mutableStateOf<ConvoView?>(null) }
    var peerProfile by remember { mutableStateOf<ProfileViewDetailed?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var groupMembers by remember { mutableStateOf(emptyList<ProfileViewBasic>()) }
    var groupMembersCursor by remember { mutableStateOf<String?>(null) }
    var groupMembersLoading by remember { mutableStateOf(false) }
    var groupMembersFailed by remember { mutableStateOf(false) }

    suspend fun loadGroupMembers(cursor: String? = null) {
        groupMembersLoading = true
        groupMembersFailed = false
        runCatching { container.api.getConvoMembers(convoId, cursor) }.onSuccess { response ->
            groupMembers = if (cursor == null) response.members else (groupMembers + response.members).distinctBy { it.did }
            groupMembersCursor = response.cursor
        }.onFailure {
            groupMembersFailed = true
        }
        groupMembersLoading = false
    }

    LaunchedEffect(convoId) {
        isLoading = true
        runCatching {
            val c = container.api.getConvo(convoId)
            convo = c
            val peer = c.directPeer(session.currentDid)
            if (peer != null) {
                peerProfile = runCatching { container.api.getProfile(peer.did) }.getOrNull()
            }
        }
        isLoading = false
        if (convo?.isGroupConvo() == true) loadGroupMembers()
    }

    val peer = convo?.directPeer(session.currentDid)
    val isBlocked = !peerProfile?.viewer?.blocking.isNullOrBlank()
    val currentConvo = convo
    val isGroup = currentConvo?.isGroupConvo() == true
    val actionFailed = tr("Something went wrong")

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (isGroup) tr("Group chat settings") else tr("Chat settings"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (isGroup) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConversationAvatar(currentConvo, session.currentDid, session.moderationOpts, 120.dp)
                    Text(
                        text = conversationName(currentConvo, session.currentDid, session.moderationOpts),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }

            peer?.let { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(AppRoute.Profile(member.handle.ifBlank { member.did })) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CaelumAvatar(
                        url = peerProfile?.avatar ?: member.avatar,
                        contentDescription = member.displayName ?: member.handle,
                        size = 48.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayNameRow(profile = member, showHandle = true, showPronouns = true)
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }

            ListItem(
                leadingContent = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                headlineContent = { Text("Mute conversation") },
                supportingContent = { Text("Stop notifications for new messages in this chat") },
                trailingContent = {
                    Switch(
                        checked = convo?.muted == true,
                        onCheckedChange = { shouldMute ->
                            val current = convo ?: return@Switch
                            convo = current.copy(muted = shouldMute)
                            scope.launch {
                                runCatching {
                                    if (shouldMute) container.api.muteConvo(convoId)
                                    else container.api.unmuteConvo(convoId)
                                }.onSuccess {
                                    screenCache.messagesConvos = screenCache.messagesConvos?.map {
                                        if (it.id == convoId) it.copy(muted = shouldMute) else it
                                    }
                                }.onFailure {
                                    convo = current
                                }
                            }
                        },
                    )
                },
            )

            if (isGroup) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Group, contentDescription = null) },
                    headlineContent = { Text(tr("Requests to join")) },
                    supportingContent = { Text(tr("View incoming requests to join this group chat")) },
                    modifier = Modifier.clickable {
                        onNavigate(AppRoute.MessagesJoinRequests(convoId))
                    },
                )
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                Text(
                    text = tr("Members"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                groupMembers.forEach { member ->
                    ListItem(
                        headlineContent = { Text(chatProfileName(member, session.moderationOpts)) },
                        supportingContent = { member.chatHandle()?.let { Text(it) } },
                        leadingContent = { ChatProfileAvatar(member, session.moderationOpts, 40.dp) },
                        modifier = Modifier.clickable {
                            onNavigate(AppRoute.Profile(member.chatHandle()?.removePrefix("@") ?: member.did))
                        },
                    )
                }
                if (groupMembersLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
                } else if (groupMembersFailed || groupMembersCursor != null) {
                    TextButton(onClick = { scope.launch { loadGroupMembers(groupMembersCursor) } }) {
                        Text(if (groupMembersFailed) tr("Try again") else tr("Load more"))
                    }
                }
            }

            if (peer != null) {
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = palette.negative500.toComposeColor(),
                        )
                    },
                    headlineContent = {
                        Text(
                            text = if (isBlocked) "Unblock @${peer.handle}" else "Block @${peer.handle}",
                            color = palette.negative500.toComposeColor(),
                        )
                    },
                    modifier = Modifier.clickable {
                        val myDid = session.currentDid ?: return@clickable
                        scope.launch {
                            runCatching {
                                val blockingUri = peerProfile?.viewer?.blocking
                                if (!blockingUri.isNullOrBlank()) {
                                    container.api.deleteRecord(myDid, "app.bsky.graph.block", blockingUri.substringAfterLast('/'))
                                } else {
                                    container.api.blockActor(myDid, peer.did)
                                }
                                peerProfile = container.api.getProfile(peer.did)
                            }.onFailure { ex ->
                                Toast.makeText(context, ex.message ?: "Action failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )

                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Report,
                            contentDescription = null,
                            tint = palette.negative500.toComposeColor(),
                        )
                    },
                    headlineContent = {
                        Text("Report @${peer.handle}", color = palette.negative500.toComposeColor())
                    },
                    modifier = Modifier.clickable { showReportDialog = true },
                )
            }

            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)

            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = palette.negative500.toComposeColor(),
                    )
                },
                headlineContent = {
                    Text(tr("Leave conversation"), color = palette.negative500.toComposeColor())
                },
                modifier = Modifier.clickable { showLeaveConfirm = true },
            )
        }
    }

    if (showLeaveConfirm) {
        LeaveConversationDialog(onDismiss = { showLeaveConfirm = false }) {
            scope.launch {
                runCatching { container.api.leaveConvo(convoId) }.onSuccess {
                    screenCache.messagesConvos = screenCache.messagesConvos?.filterNot { it.id == convoId }
                    screenCache.messagesRequestConvos = screenCache.messagesRequestConvos?.filterNot { it.id == convoId }
                    container.sessionManager.refreshUnreadCounts()
                    onBack()
                }.onFailure { Toast.makeText(context, actionFailed, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    if (showReportDialog && peer != null) {
        ReportPostOrActorDialog(
            post = null,
            actorDid = peer.did,
            onDismiss = { showReportDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesJoinRequestsScreen(
    convoId: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var requests by remember(convoId) { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var isLoading by remember(convoId) { mutableStateOf(true) }
    var errorMsg by remember(convoId) { mutableStateOf<String?>(null) }
    var busyDid by remember(convoId) { mutableStateOf<String?>(null) }

    suspend fun loadJoinRequests() {
        isLoading = true
        errorMsg = null
        runCatching {
            val res = container.xrpcClient.getJson(
                nsid = "chat.bsky.convo.listJoinRequests",
                params = listOf("convoId" to convoId),
            ).jsonObject
            res["requests"]?.jsonArray?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val memberEl = obj["member"] ?: obj["profile"] ?: el
                runCatching { AtProtoJson.decodeFromJsonElement<ProfileViewBasic>(memberEl) }.getOrNull()
            }.orEmpty()
        }.onSuccess { list ->
            requests = list
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            errorMsg = formatChatErrorMessage(ex)
        }
        isLoading = false
    }

    LaunchedEffect(convoId) {
        loadJoinRequests()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Join requests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null && requests.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast700.toComposeColor(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { loadJoinRequests() } }) {
                        Text(tr("Try again"))
                    }
                }
            }
            requests.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No pending join requests.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(items = requests, key = { it.did }) { requester ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CaelumAvatar(
                                url = requester.avatar,
                                contentDescription = requester.displayName ?: requester.handle,
                                size = 40.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                DisplayNameRow(profile = requester, showHandle = true)
                            }
                            IconButton(
                                enabled = busyDid != requester.did,
                                onClick = {
                                    scope.launch {
                                        busyDid = requester.did
                                        runCatching {
                                            container.xrpcClient.postJson(
                                                nsid = "chat.bsky.convo.acceptJoinRequest",
                                                body = buildJsonObject {
                                                    put("convoId", JsonPrimitive(convoId))
                                                    put("did", JsonPrimitive(requester.did))
                                                },
                                            )
                                        }.onSuccess {
                                            requests = requests.filterNot { it.did == requester.did }
                                        }
                                        busyDid = null
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Approve", tint = palette.positive500.toComposeColor())
                            }
                            IconButton(
                                enabled = busyDid != requester.did,
                                onClick = {
                                    scope.launch {
                                        busyDid = requester.did
                                        runCatching {
                                            container.xrpcClient.postJson(
                                                nsid = "chat.bsky.convo.rejectJoinRequest",
                                                body = buildJsonObject {
                                                    put("convoId", JsonPrimitive(convoId))
                                                    put("did", JsonPrimitive(requester.did))
                                                },
                                            )
                                        }.onSuccess {
                                            requests = requests.filterNot { it.did == requester.did }
                                        }
                                        busyDid = null
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Decline", tint = palette.negative500.toComposeColor())
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val scope = rememberCoroutineScope()

    var allowIncoming by remember { mutableStateOf("following") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(session.currentDid) {
        val myDid = session.currentDid ?: return@LaunchedEffect
        isLoading = true
        val recordValue = runCatching {
            val res = container.xrpcClient.getJson(
                nsid = "com.atproto.repo.getRecord",
                params = listOf(
                    "repo" to myDid,
                    "collection" to "chat.bsky.actor.declaration",
                    "rkey" to "self",
                ),
            ).jsonObject
            (res["value"] as? JsonObject)?.get("allowIncoming")?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        allowIncoming = recordValue ?: runCatching {
            container.api.getProfile(myDid).associated?.chat?.allowIncoming
        }.getOrNull() ?: "following"
        isLoading = false
    }

    fun updateAllowIncoming(value: String) {
        val myDid = session.currentDid ?: return
        val prev = allowIncoming
        allowIncoming = value
        scope.launch {
            isSaving = true
            runCatching {
                container.api.putRecord(
                    repo = myDid,
                    collection = "chat.bsky.actor.declaration",
                    rkey = "self",
                    record = buildJsonObject {
                        put("\$type", JsonPrimitive("chat.bsky.actor.declaration"))
                        put("allowIncoming", JsonPrimitive(value))
                    },
                )
            }.onFailure { ex ->
                allowIncoming = prev
                Toast.makeText(context, ex.message ?: "Failed to update chat settings", Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    val options = listOf(
        Triple("all", "Everyone", "Anyone on Bluesky can send you message requests"),
        Triple("following", "Users I follow", "Only people you follow can start a conversation"),
        Triple("none", "No one", "Disable incoming chat requests"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Chat settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Allow new messages from",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.contrast1000.toComposeColor(),
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                Column {
                    options.forEachIndexed { idx, (value, title, subtitle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSaving) { updateAllowIncoming(value) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = allowIncoming == value,
                                onClick = { if (!isSaving) updateAllowIncoming(value) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor())
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
                            }
                        }
                        if (idx < options.lastIndex) {
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                ListItem(
                    headlineContent = { Text("Notification sound") },
                    supportingContent = { Text("Play sound for incoming chat notifications") },
                    trailingContent = {
                        Switch(
                            checked = settings.playSoundChat,
                            onCheckedChange = { enabled ->
                                scope.launch { container.preferences.setPlaySoundChat(enabled) }
                            },
                        )
                    },
                )
            }
        }
    }
}
