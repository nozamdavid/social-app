package am.noz.caelum.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendPostViaChatScreen(
    postUri: String,
    postCid: String,
    onBack: () -> Unit,
    onSelectConversation: (AppRoute.MessagesConversation) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val account = session.currentDid
    val activation = session.activation

    var query by rememberSaveable(account) { mutableStateOf("") }
    var conversations by remember(account) { mutableStateOf(emptyList<ConvoView>()) }
    var profiles by remember(account) { mutableStateOf(emptyList<ProfileViewBasic>()) }
    var cursor by remember(account) { mutableStateOf<String?>(null) }
    var loadingRecent by remember(account) { mutableStateOf(true) }
    var loadingMore by remember(account) { mutableStateOf(false) }
    var searching by remember(account) { mutableStateOf(false) }
    var selecting by remember(account) { mutableStateOf(false) }
    var recentError by remember(account) { mutableStateOf(false) }
    var searchError by remember(account) { mutableStateOf(false) }
    var selectError by remember(account) { mutableStateOf(false) }
    var retrySearch by remember(account) { mutableStateOf(0) }

    suspend fun loadRecent(nextCursor: String? = null) {
        recentError = false
        loadingRecent = nextCursor == null
        loadingMore = nextCursor != null
        try {
            container.sessionManager.ensurePdsResolvedForCurrentAccount()
            val response = container.api.listConvos(status = "accepted", cursor = nextCursor)
            conversations = if (nextCursor == null) response.convos else (conversations + response.convos).distinctBy { it.id }
            cursor = response.cursor
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            recentError = true
        } finally {
            loadingRecent = false
            loadingMore = false
        }
    }

    LaunchedEffect(account) {
        if (account != null) loadRecent()
    }

    val search = query.trim()
    LaunchedEffect(search, account, retrySearch) {
        if (search.isEmpty() || account == null) return@LaunchedEffect
        searching = true
        try {
            delay(250)
            profiles = container.api.searchActorsTypeahead(search, limit = 20)
                .filterNot { it.did == account }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            searchError = true
        } finally {
            searching = false
        }
    }

    fun openDraft(convoId: String) {
        if (container.sessionManager.state.value.activation == activation) {
            onSelectConversation(AppRoute.MessagesConversation(convoId, StrongRef(postUri, postCid)))
        }
    }

    val matchingConversations = conversations.filter { convo ->
        search.isEmpty() || conversationName(convo, account, session.moderationOpts).contains(search, ignoreCase = true) ||
            convo.directPeer(account)?.let { peer ->
                peer.handle.contains(search, ignoreCase = true) || profiles.any { it.did == peer.did }
            } == true
    }
    val knownPeers = matchingConversations.mapNotNull { it.directPeer(account)?.did }.toSet()
    val matchingProfiles = profiles.filterNot { it.did in knownPeers }

    Scaffold(
        containerColor = palette.contrast0.toComposeColor(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Send post to...")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { next ->
                    if (query.trim() != next.trim()) {
                        profiles = emptyList()
                        searching = next.isNotBlank()
                        searchError = false
                        selectError = false
                    }
                    query = next
                },
                placeholder = { Text(tr("Search for people")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                enabled = !selecting && account != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("send_via_chat_search"),
            )
            if (selectError) {
                Text(
                    tr("An issue occurred starting the chat, please try again."),
                    color = palette.negative500.toComposeColor(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                account == null -> Text(tr("Sign in to view your messages."), modifier = Modifier.padding(16.dp))
                selecting || (search.isNotEmpty() && searching) || (search.isEmpty() && loadingRecent) -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).testTag("send_via_chat_recipients")) {
                        if (search.isEmpty() && conversations.isNotEmpty()) {
                            item { RecipientSectionTitle(tr("Recent chats")) }
                        }
                        items(matchingConversations, key = { "convo:${it.id}" }) { convo ->
                            ConversationIdentity(
                                convo = convo,
                                currentDid = account,
                                moderation = session.moderationOpts,
                                modifier = Modifier.fillMaxWidth().clickable { openDraft(convo.id) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                showPreview = false,
                            )
                        }
                        if (search.isNotEmpty()) {
                            items(matchingProfiles, key = { "profile:${it.did}" }) { profile ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selecting = true
                                        selectError = false
                                        scope.launch {
                                            try {
                                                val convo = withContext(container.sessionManager.bindAccount(account, activation)) {
                                                    container.api.getConvoForMembers(listOf(account, profile.did))
                                                }
                                                openDraft(convo.id)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                selectError = true
                                            } finally {
                                                selecting = false
                                            }
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChatProfileAvatar(profile, session.moderationOpts, 40.dp)
                                    Column {
                                        Text(chatProfileName(profile, session.moderationOpts), style = MaterialTheme.typography.bodyLarge)
                                        profile.chatHandle()?.let { Text(it, color = palette.contrast600.toComposeColor()) }
                                    }
                                }
                            }
                            if (searchError) {
                                item {
                                    TextButton(onClick = {
                                        searchError = false
                                        retrySearch++
                                    }) { Text(tr("Try again")) }
                                }
                            } else if (matchingProfiles.isEmpty() && matchingConversations.isEmpty()) {
                                item { RecipientSectionTitle(tr("No results")) }
                            }
                        } else if (recentError || cursor != null) {
                            item {
                                TextButton(
                                    enabled = !loadingMore,
                                    onClick = { scope.launch { loadRecent(cursor) } },
                                ) {
                                    Text(if (recentError) tr("Try again") else tr("Load more"))
                                }
                            }
                        } else if (conversations.isEmpty()) {
                            item { RecipientSectionTitle(tr("No conversations yet")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
