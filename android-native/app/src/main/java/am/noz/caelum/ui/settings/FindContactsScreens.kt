package am.noz.caelum.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.network.ContactSyncStatus
import am.noz.caelum.core.network.FindContactsApi
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FindContactsSettingsScreen(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val api = rememberFindContactsApi()
    var status by remember(api) { mutableStateOf<ContactSyncStatus?>(null) }
    var matches by remember(api) { mutableStateOf<List<ProfileView>>(emptyList()) }
    var cursor by remember(api) { mutableStateOf<String?>(null) }
    var loadedMatches by remember(api) { mutableStateOf(false) }
    var busy by remember(api) { mutableStateOf(false) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var confirmingRemoval by remember(api) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        busy = true
        try {
            status = api.syncStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn’t load your contact sync status."
        } finally {
            busy = false
        }
    }

    SettingsScaffold(
        title = tr("Find & Invite Friends"),
        onBack = onBack,
        testTag = "find_contacts_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsInfoCard(
                    title = tr("Find friends from your contacts"),
                    body = tr("Verify your phone number to find Bluesky accounts that have also shared your number."),
                )
            }
            if (api == null) {
                item { Text(tr("Sign in to find friends from your contacts.")) }
            } else {
                item {
                    Button(
                        enabled = !busy,
                        onClick = { onNavigate(AppRoute.FindContactsFlow) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Find Friends")) }
                }
                if (status != null && !confirmingRemoval) {
                    if (!loadedMatches || cursor != null) {
                        item {
                            OutlinedButton(enabled = !busy, onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        val page = api.getMatches(cursor)
                                        matches = (matches + page.matches).distinctBy { it.did }
                                        cursor = page.cursor
                                        loadedMatches = true
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = "Couldn’t load your contact matches. Please try again."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }) { Text(if (loadedMatches) tr("Load more") else tr("View matches")) }
                        }
                    }
                    if (loadedMatches && matches.isEmpty()) {
                        item { Text(tr("No matching accounts found")) }
                    }
                    items(matches, key = { it.did }) { ContactMatchRow(it, onNavigate) }
                    item {
                        OutlinedButton(enabled = !busy, onClick = { confirmingRemoval = true }) {
                            Text(tr("Remove contact data"))
                        }
                    }
                }
                if (confirmingRemoval) {
                    item {
                        SettingsInfoCard(body = tr("Remove your synced contact data and existing matches from Bluesky?")) {
                            Button(enabled = !busy, onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        api.removeData()
                                        status = null
                                        matches = emptyList()
                                        cursor = null
                                        loadedMatches = false
                                        confirmingRemoval = false
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = "Couldn’t remove your contact data. Please try again."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }) { Text(tr("Remove contact data")) }
                            OutlinedButton(enabled = !busy, onClick = { confirmingRemoval = false }) {
                                Text(tr("Cancel"))
                            }
                        }
                    }
                }
            }
            if (busy) item { CircularProgressIndicator() }
            error?.let { message -> item { SettingsInfoCard(body = tr(message), isError = true) } }
        }
    }
}

@Composable
fun FindContactsFlowScreen(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val api = rememberFindContactsApi()
    SettingsScaffold(
        title = tr("Find Friends"),
        onBack = onBack,
        testTag = "find_contacts_flow_screen",
    ) { paddingModifier ->
        if (api == null) {
            Text(tr("Sign in to find friends from your contacts."), paddingModifier.padding(16.dp))
        } else {
            key(api) { FindContactsFlow(api, paddingModifier, onNavigate) }
        }
    }
}

private enum class ContactStep { Phone, Code, Import, Results }

@Composable
private fun FindContactsFlow(api: FindContactsApi, modifier: Modifier, onNavigate: (AppRoute) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val region = Locale.getDefault().country
    var step by remember { mutableStateOf(ContactStep.Phone) }
    var phoneInput by remember { mutableStateOf("") }
    var verifiedNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var noNumbers by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<ProfileView>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(context.hasContactsPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        error = if (it) null else "Contacts access was denied. You can allow it in Android settings."
    }

    fun runStep(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: XrpcException) {
                error = when {
                    e.error == "InvalidCode" -> "That code didn’t match. Check the code and try again."
                    e.error == "InvalidPhone" -> "Enter a valid phone number with its country code."
                    e.statusCode == 429 -> "Too many attempts. Wait a few minutes and try again."
                    else -> failure
                }
            } catch (e: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (step) {
            ContactStep.Phone -> item {
                Text(tr("Verify your phone number"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Enter your number with its country code. Bluesky will text you a verification code."))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        error = null
                    },
                    enabled = !busy,
                    label = { Text(tr("Phone number")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("find_contacts_phone"),
                )
                Button(enabled = !busy && phoneInput.isNotBlank(), onClick = {
                    val number = phoneInput.trim().takeIf { it.startsWith("+") }
                        ?.let { PhoneNumberUtils.formatNumberToE164(it, region) }
                    if (number == null) {
                        error = "Enter a valid phone number with its country code."
                    } else {
                        runStep("Couldn’t send the verification code. Please try again.") {
                            api.startPhoneVerification(number)
                            verifiedNumber = number
                            code = ""
                            step = ContactStep.Code
                        }
                    }
                }) { Text(tr("Send verification code")) }
            }

            ContactStep.Code -> item {
                Text(tr("Enter your verification code"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter { digit -> digit in '0'..'9' }.take(6)
                        error = null
                    },
                    enabled = !busy,
                    label = { Text(tr("Verification code")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("find_contacts_code"),
                )
                Button(enabled = !busy && code.length == 6, onClick = {
                    runStep("Couldn’t verify your phone number. Please try again.") {
                        token = api.verifyPhone(verifiedNumber, code)
                        code = ""
                        step = ContactStep.Import
                    }
                }) { Text(tr("Verify phone number")) }
                OutlinedButton(enabled = !busy, onClick = {
                    runStep("Couldn’t resend the verification code. Please try again.") {
                        api.startPhoneVerification(verifiedNumber)
                        code = ""
                    }
                }) { Text(tr("Resend code")) }
                OutlinedButton(enabled = !busy, onClick = {
                    error = null
                    step = ContactStep.Phone
                }) { Text(tr("Change phone number")) }
            }

            ContactStep.Import -> item {
                SettingsInfoCard(
                    title = tr("Share contacts"),
                    body = tr("Bluesky will receive up to 1,000 phone numbers from your contacts to find mutual matches. Contact names stay on your device."),
                ) {
                    Button(enabled = !busy, onClick = {
                        hasPermission = context.hasContactsPermission()
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            runStep("Couldn’t import contacts. Verify your phone number to try again.") {
                                val numbers = withContext(Dispatchers.IO) {
                                    readContactNumbers(context, region, verifiedNumber)
                                }
                                noNumbers = numbers.isEmpty()
                                if (noNumbers) {
                                    matches = emptyList()
                                    step = ContactStep.Results
                                } else {
                                    val importToken = checkNotNull(token)
                                    token = null
                                    // A completed or interrupted import can consume the single-use token.
                                    step = ContactStep.Phone
                                    matches = api.importContacts(importToken, numbers).distinctBy { it.did }
                                    step = ContactStep.Results
                                }
                                token = null
                                verifiedNumber = ""
                                phoneInput = ""
                            }
                        }
                    }) { Text(if (hasPermission) tr("Import contacts") else tr("Allow contacts access")) }
                    OutlinedButton(onClick = {
                        uriHandler.openUri("https://bsky.social/about/support/find-friends-privacy-policy")
                    }) { Text(tr("Find Friends Privacy Policy")) }
                }
            }

            ContactStep.Results -> {
                if (matches.isEmpty()) {
                    item {
                        Text(if (noNumbers) tr("No eligible phone numbers found in your contacts.") else tr("No matching accounts found"))
                    }
                } else {
                    items(matches, key = { it.did }) { ContactMatchRow(it, onNavigate) }
                }
            }
        }
        if (busy) item { CircularProgressIndicator() }
        error?.let { message -> item { SettingsInfoCard(body = tr(message), isError = true) } }
    }
}

@Composable
private fun rememberFindContactsApi(): FindContactsApi? {
    val container = LocalAppContainer.current
    val session by container.sessionManager.state.collectAsState()
    val did = session.currentDid
    return remember(container, did, session.activation) {
        did?.let { FindContactsApi(container.xrpcClient, container.sessionManager.bindAccount(it, session.activation)) }
    }
}

@Composable
private fun ContactMatchRow(actor: ProfileView, onNavigate: (AppRoute) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(AppRoute.Profile(actor.handle)) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CaelumAvatar(url = actor.avatar, contentDescription = null, size = 44.dp)
        Column {
            Text(actor.displayName?.takeIf { it.isNotBlank() } ?: actor.handle)
            Text("@${actor.handle}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Context.hasContactsPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

private fun readContactNumbers(context: Context, region: String, ownNumber: String): List<String> {
    val found = linkedSetOf<String>()
    val projection = arrayOf(Phone.NORMALIZED_NUMBER, Phone.NUMBER)
    val cursor = requireNotNull(context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null))
    cursor.use {
        while (found.size < FindContactsApi.MAX_CONTACTS && it.moveToNext()) {
            val number = it.getString(0)?.let { PhoneNumberUtils.formatNumberToE164(it, region) }
                ?: it.getString(1)?.let { PhoneNumberUtils.formatNumberToE164(it, region) }
            if (number != null && number != ownNumber) found.add(number)
        }
    }
    return found.toList()
}
