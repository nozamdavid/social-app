package am.noz.caelum.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File

@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val account = sessionState.currentAccount
    val scope = rememberCoroutineScope()

    var showUpdateEmailDialog by remember { mutableStateOf(false) }
    var showVerifyEmailDialog by remember { mutableStateOf(false) }
    var showChangeHandleDialog by remember { mutableStateOf(false) }
    var showPasswordResetDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var exportingCar by remember { mutableStateOf(false) }
    var sessionEmail by remember(account?.did) { mutableStateOf(account?.email.orEmpty()) }
    var emailConfirmed by remember(account?.did) { mutableStateOf(account?.emailConfirmed ?: true) }
    var isBot by remember { mutableStateOf(false) }

    LaunchedEffect(account?.did) {
        if (account != null) {
            runCatching { container.api.getSession() }.getOrNull()?.let { sess ->
                sess["email"]?.jsonPrimitive?.contentOrNull?.let { sessionEmail = it }
                sess["emailConfirmed"]?.jsonPrimitive?.booleanOrNull?.let { emailConfirmed = it }
            }
            runCatching { container.api.getProfile(account.did) }.getOrNull()?.let { prof ->
                isBot = prof.labels.any { it.`val` == "bot" }
            }
        }
    }

    fun exportCarArchive() {
        val current = account ?: return
        val auth = container.sessionManager.currentAuth() ?: return
        scope.launch {
            exportingCar = true
            runCatching {
                val pds = auth.pdsUrl.trimEnd('/')
                val req = Request.Builder()
                    .url("$pds/xrpc/com.atproto.sync.getRepo?did=${current.did}")
                    .header("Authorization", "Bearer ${auth.accessJwt}")
                    .get()
                    .build()
                val outFile = withContext(Dispatchers.IO) {
                    container.okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Export failed with HTTP ${resp.code}")
                        }
                        val bytes = resp.body?.bytes() ?: throw IllegalStateException("Empty repository archive")
                        val safeName = current.handle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        File(context.cacheDir, "$safeName.car").apply { writeBytes(bytes) }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.ipld.car"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(share, "Export repository (.car)").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Failed to export CAR archive", Toast.LENGTH_LONG).show()
            }
            exportingCar = false
        }
    }

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
        testTag = "account_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (account == null) {
                item {
                    SettingsInfoCard(
                        title = "Not signed in",
                        body = "Sign in to manage account settings.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                return@LazyColumn
            }

            item {
                SettingsSectionHeader("Identity & Contact")
                SettingsListItem(
                    title = "Email",
                    subtitle = sessionEmail.ifBlank { "(no email set)" },
                    icon = Icons.Filled.Email,
                    badge = if (emailConfirmed) "Confirmed" else "Unconfirmed",
                    showChevron = false,
                    onClick = { showUpdateEmailDialog = true },
                )
                if (!emailConfirmed) {
                    SettingsListItem(
                        title = "Verify email address",
                        subtitle = "Send a confirmation code to $sessionEmail",
                        icon = Icons.Filled.VerifiedUser,
                        onClick = { showVerifyEmailDialog = true },
                    )
                }
                SettingsListItem(
                    title = "Update email",
                    subtitle = "Change the email address tied to your PDS account",
                    icon = Icons.Filled.Email,
                    onClick = { showUpdateEmailDialog = true },
                )
                SettingsListItem(
                    title = "Handle",
                    subtitle = "@${account.handle}",
                    icon = Icons.Filled.AlternateEmail,
                    onClick = { showChangeHandleDialog = true },
                )
                SettingsListItem(
                    title = "Password & Reset",
                    subtitle = "Request a password reset email or apply a reset code",
                    icon = Icons.Filled.Lock,
                    onClick = { showPasswordResetDialog = true },
                )
                SettingsListItem(
                    title = "Automation label",
                    subtitle = if (isBot) "Marked as an automated / bot account" else "Not marked as automated",
                    icon = Icons.Filled.SmartToy,
                    badge = if (isBot) "Bot" else null,
                    onClick = { onNavigate(AppRoute.AutomationLabelSettings) },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Data & Portability")
                SettingsListItem(
                    title = "Export my data (.car)",
                    subtitle = if (exportingCar) "Downloading repository archive..." else "Download your ATProto repository archive",
                    icon = Icons.Filled.Archive,
                    showChevron = !exportingCar,
                    trailing = if (exportingCar) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else null,
                    onClick = { if (!exportingCar) exportCarArchive() },
                )
                SettingsListItem(
                    title = "PDS Hosting",
                    subtitle = account.pdsUrl ?: account.serviceUrl,
                    showChevron = false,
                    onClick = {
                        LinkNavigationHelper.copyToClipboard(
                            context,
                            "DID",
                            account.did,
                            "Copied DID (${account.did})",
                        )
                    },
                    badge = "Copy DID",
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Account Status")
                SettingsListItem(
                    title = "Deactivate account",
                    subtitle = "Temporarily hide your profile, posts, and feeds without deleting your data",
                    icon = Icons.Filled.PauseCircleOutline,
                    onClick = { showDeactivateDialog = true },
                )
                SettingsListItem(
                    title = "Delete account",
                    subtitle = "Permanently delete your account from your PDS",
                    icon = Icons.Filled.DeleteForever,
                    destructive = true,
                    onClick = { showDeleteDialog = true },
                )
            }
        }
    }

    if (showUpdateEmailDialog && account != null) {
        UpdateEmailDialog(
            currentEmail = sessionEmail,
            onUpdated = { newEmail ->
                sessionEmail = newEmail
                emailConfirmed = false
                scope.launch {
                    val updatedAccounts = sessionState.accounts.map {
                        if (it.did == account.did) it.copy(email = newEmail, emailConfirmed = false) else it
                    }
                    container.preferences.updateAccounts(updatedAccounts, sessionState.currentDid)
                }
                showUpdateEmailDialog = false
                Toast.makeText(context, "Email updated", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUpdateEmailDialog = false },
        )
    }

    if (showVerifyEmailDialog && account != null) {
        VerifyEmailDialog(
            email = sessionEmail,
            onVerified = {
                emailConfirmed = true
                scope.launch {
                    val updatedAccounts = sessionState.accounts.map {
                        if (it.did == account.did) it.copy(emailConfirmed = true) else it
                    }
                    container.preferences.updateAccounts(updatedAccounts, sessionState.currentDid)
                }
                showVerifyEmailDialog = false
                Toast.makeText(context, "Email verified", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showVerifyEmailDialog = false },
        )
    }

    if (showChangeHandleDialog && account != null) {
        ChangeHandleDialog(
            currentHandle = account.handle,
            onChanged = { newHandle ->
                scope.launch {
                    val updatedAccounts = sessionState.accounts.map {
                        if (it.did == account.did) it.copy(handle = newHandle) else it
                    }
                    container.preferences.updateAccounts(updatedAccounts, sessionState.currentDid)
                    container.identityResolver.clearCaches()
                }
                showChangeHandleDialog = false
                Toast.makeText(context, "Handle updated to @$newHandle", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showChangeHandleDialog = false },
        )
    }

    if (showPasswordResetDialog && account != null) {
        PasswordResetDialog(
            serviceOrigin = account.pdsUrl ?: account.serviceUrl,
            initialEmail = sessionEmail,
            onDismiss = { showPasswordResetDialog = false },
        )
    }

    if (showDeactivateDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Deactivate account?") },
            text = {
                Text("Your profile, posts, feeds, and lists will be hidden until you sign back in and reactivate your account.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                container.xrpcClient.postJson("com.atproto.server.deactivateAccount")
                                container.sessionManager.removeAccount(account.did)
                                showDeactivateDialog = false
                                Toast.makeText(context, "Account deactivated", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to deactivate", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) {
                    Text("Deactivate", color = palette.negative500.toComposeColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteDialog && account != null) {
        DeleteAccountDialog(
            did = account.did,
            email = sessionEmail,
            onDeleted = {
                scope.launch {
                    container.sessionManager.removeAccount(account.did)
                }
                showDeleteDialog = false
                Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun UpdateEmailDialog(
    currentEmail: String,
    onUpdated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var newEmail by remember { mutableStateOf(currentEmail) }
    var token by remember { mutableStateOf("") }
    var needsToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("New email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsToken) {
                    Text(
                        text = "Enter the confirmation code sent to your current email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Confirmation code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.negative500.toComposeColor(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && newEmail.contains('@'),
                onClick = {
                    scope.launch {
                        busy = true
                        errorMsg = null
                        runCatching {
                            val body = buildJsonObject {
                                put("email", JsonPrimitive(newEmail.trim()))
                                if (token.isNotBlank()) put("token", JsonPrimitive(token.trim()))
                            }
                            container.xrpcClient.postJson("com.atproto.server.updateEmail", body)
                        }.onSuccess {
                            onUpdated(newEmail.trim())
                        }.onFailure { err ->
                            val msg = err.message.orEmpty()
                            if (msg.contains("TokenRequired", ignoreCase = true) || msg.contains("token", ignoreCase = true)) {
                                needsToken = true
                                runCatching { container.xrpcClient.postJson("com.atproto.server.requestEmailUpdate") }
                                errorMsg = "A confirmation code was sent to your current email."
                            } else {
                                errorMsg = msg.ifBlank { "Failed to update email" }
                            }
                        }
                        busy = false
                    }
                },
            ) {
                Text(if (busy) "Saving..." else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun VerifyEmailDialog(
    email: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (sent) "Enter the verification code sent to $email." else "Send a verification code to $email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast700.toComposeColor(),
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            errorMsg = null
                            runCatching { container.api.requestEmailConfirmation() }
                                .onSuccess { sent = true }
                                .onFailure { errorMsg = it.message ?: "Failed to send confirmation email" }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (sent) "Resend code" else "Send verification email")
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Verification code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && code.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        errorMsg = null
                        runCatching { container.api.confirmEmail(email, code.trim()) }
                            .onSuccess { onVerified() }
                            .onFailure { errorMsg = it.message ?: "Invalid confirmation code" }
                        busy = false
                    }
                },
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ChangeHandleDialog(
    currentHandle: String,
    onChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var handleInput by remember { mutableStateOf(currentHandle) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Handle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your new handle (e.g. name.bsky.social or your custom domain).",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
                OutlinedTextField(
                    value = handleInput,
                    onValueChange = { handleInput = it; errorMsg = null },
                    label = { Text("Handle") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && handleInput.trim().removePrefix("@").contains('.'),
                onClick = {
                    val clean = handleInput.trim().removePrefix("@").lowercase()
                    scope.launch {
                        busy = true
                        errorMsg = null
                        runCatching {
                            container.xrpcClient.postJson(
                                nsid = "com.atproto.identity.updateHandle",
                                body = buildJsonObject { put("handle", JsonPrimitive(clean)) },
                            )
                        }.onSuccess {
                            onChanged(clean)
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Failed to update handle"
                        }
                        busy = false
                    }
                },
            ) {
                Text(if (busy) "Updating..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasswordResetDialog(
    serviceOrigin: String,
    initialEmail: String,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf(initialEmail) }
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Account email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = !busy && email.contains('@'),
                    onClick = {
                        scope.launch {
                            busy = true
                            errorMsg = null
                            runCatching { container.api.requestPasswordReset(serviceOrigin, email.trim()) }
                                .onSuccess { codeSent = true }
                                .onFailure { errorMsg = it.message ?: "Could not request reset code" }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (codeSent) "Resend reset code" else "Send reset code")
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Reset code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && token.isNotBlank() && newPassword.length >= 8,
                onClick = {
                    scope.launch {
                        busy = true
                        errorMsg = null
                        runCatching {
                            container.api.resetPassword(serviceOrigin, token.trim(), newPassword)
                        }.onSuccess {
                            Toast.makeText(context, "Password updated", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Failed to reset password"
                        }
                        busy = false
                    }
                },
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
    did: String,
    email: String,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var tokenSent by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This permanently deletes your account on your PDS. Request a deletion confirmation code sent to ${email.ifBlank { "your email" }}, then enter your code and password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast700.toComposeColor(),
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            errorMsg = null
                            runCatching { container.xrpcClient.postJson("com.atproto.server.requestAccountDelete") }
                                .onSuccess { tokenSent = true }
                                .onFailure { errorMsg = it.message ?: "Failed to request deletion email" }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (tokenSent) "Resend deletion code" else "Send deletion code")
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Deletion code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && token.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.negative500.toComposeColor()),
                onClick = {
                    scope.launch {
                        busy = true
                        errorMsg = null
                        runCatching {
                            container.xrpcClient.postJson(
                                nsid = "com.atproto.server.deleteAccount",
                                body = buildJsonObject {
                                    put("did", JsonPrimitive(did))
                                    put("password", JsonPrimitive(password))
                                    put("token", JsonPrimitive(token.trim()))
                                },
                            )
                        }.onSuccess {
                            onDeleted()
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Failed to delete account"
                        }
                        busy = false
                    }
                },
            ) {
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PrivacyAndSecuritySettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val account = sessionState.currentAccount
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var email2faEnabled by remember(account?.did) { mutableStateOf(account?.emailAuthFactor ?: false) }
    var appPasswordCount by remember { mutableStateOf<Int?>(null) }
    var pwiOptOut by remember { mutableStateOf(false) }
    var pwiLoading by remember { mutableStateOf(true) }
    var show2faCodeDialog by remember { mutableStateOf(false) }
    var twoFaTargetState by remember { mutableStateOf(false) }
    var twoFaToken by remember { mutableStateOf("") }
    var twoFaError by remember { mutableStateOf<String?>(null) }
    var twoFaBusy by remember { mutableStateOf(false) }

    LaunchedEffect(account?.did) {
        if (account != null) {
            runCatching { container.api.getSession() }.getOrNull()?.let { sess ->
                sess["emailAuthFactor"]?.jsonPrimitive?.booleanOrNull?.let { email2faEnabled = it }
            }
            runCatching { container.api.listAppPasswords() }.getOrNull()?.let { res ->
                appPasswordCount = res["passwords"]?.jsonArray?.size
            }
            runCatching { container.api.getProfile(account.did) }.getOrNull()?.let { prof: ProfileViewDetailed ->
                pwiOptOut = prof.labels.any { it.`val` == "!no-unauthenticated" }
            }
        }
        pwiLoading = false
    }

    suspend fun applyEmail2fa(enable: Boolean, token: String? = null) {
        val current = account ?: return
        val email = current.email ?: container.api.getSession()["email"]?.jsonPrimitive?.contentOrNull
        if (email.isNullOrBlank()) {
            Toast.makeText(context, "Add and confirm an email address in Account settings first.", Toast.LENGTH_LONG).show()
            return
        }
        val body = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("emailAuthFactor", JsonPrimitive(enable))
            if (!token.isNullOrBlank()) put("token", JsonPrimitive(token.trim()))
        }
        container.xrpcClient.postJson("com.atproto.server.updateEmail", body)
        email2faEnabled = enable
        val updated = sessionState.accounts.map {
            if (it.did == current.did) it.copy(emailAuthFactor = enable) else it
        }
        container.preferences.updateAccounts(updated, sessionState.currentDid)
    }

    SettingsScaffold(
        title = "Privacy & Security",
        onBack = onBack,
        testTag = "privacy_security_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSectionHeader("Security")
                SettingsSwitchItem(
                    title = "Two-factor authentication (Email 2FA)",
                    subtitle = if (email2faEnabled) {
                        "Enabled — sign-in requires a confirmation code sent to your email."
                    } else {
                        "Require an email confirmation code when signing in with your password."
                    },
                    icon = Icons.Filled.Security,
                    checked = email2faEnabled,
                    enabled = account != null,
                    haptics = haptics,
                    onCheckedChange = { wantEnable ->
                        scope.launch {
                            runCatching {
                                applyEmail2fa(wantEnable)
                                Toast.makeText(
                                    context,
                                    if (wantEnable) "Email 2FA enabled" else "Email 2FA disabled",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }.onFailure { err ->
                                twoFaTargetState = wantEnable
                                twoFaError = null
                                twoFaToken = ""
                                runCatching { container.xrpcClient.postJson("com.atproto.server.requestEmailUpdate") }
                                show2faCodeDialog = true
                            }
                        }
                    },
                )

                SettingsListItem(
                    title = "App passwords",
                    subtitle = "Generate dedicated passwords for third-party ATProto clients",
                    icon = Icons.Filled.Key,
                    badge = appPasswordCount?.toString(),
                    onClick = { onNavigate(AppRoute.AppPasswords) },
                )

                SettingsListItem(
                    title = "Allow others to be notified of your posts",
                    subtitle = "Choose who can subscribe to activity notifications for your new posts",
                    icon = Icons.Filled.NotificationsActive,
                    onClick = { onNavigate(AppRoute.ActivityPrivacySettings) },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Visibility")
                SettingsSwitchItem(
                    title = "Discourage logged-out visibility",
                    subtitle = "Adds the !no-unauthenticated self-label asking apps and sites not to show your profile or posts to logged-out visitors.",
                    icon = Icons.Filled.VisibilityOff,
                    checked = pwiOptOut,
                    enabled = account != null && !pwiLoading,
                    haptics = haptics,
                    onCheckedChange = { optOut ->
                        val did = account?.did ?: return@SettingsSwitchItem
                        pwiOptOut = optOut
                        scope.launch {
                            runCatching {
                                toggleProfileSelfLabel(
                                    xrpc = container.xrpcClient,
                                    api = container.api,
                                    did = did,
                                    labelValue = "!no-unauthenticated",
                                    enabled = optOut,
                                )
                                Toast.makeText(context, "Visibility preference saved", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                pwiOptOut = !optOut
                                Toast.makeText(context, err.message ?: "Failed to update visibility", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsInfoCard(
                    title = "Public ATProto Network",
                    body = "Bluesky and Caelum operate on the open Atmosphere network. Visibility settings ask compliant apps not to display your content to logged-out users, though public repository records remain part of the open network.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    TextButton(
                        onClick = {
                            LinkNavigationHelper.openUrl(
                                context = context,
                                rawUrl = "https://blueskyweb.zendesk.com/hc/en-us/articles/15835264007693-Data-Privacy",
                                useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                onInternalRoute = onNavigate,
                            )
                        },
                    ) {
                        Text("Learn more about what is public")
                    }
                }
            }
        }
    }

    if (show2faCodeDialog) {
        AlertDialog(
            onDismissRequest = { show2faCodeDialog = false },
            title = { Text(if (twoFaTargetState) "Enable Email 2FA" else "Disable Email 2FA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the confirmation code sent to your email address to update 2FA settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                    OutlinedTextField(
                        value = twoFaToken,
                        onValueChange = { twoFaToken = it },
                        label = { Text("Confirmation code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!twoFaError.isNullOrBlank()) {
                        Text(twoFaError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !twoFaBusy && twoFaToken.isNotBlank(),
                    onClick = {
                        scope.launch {
                            twoFaBusy = true
                            twoFaError = null
                            runCatching { applyEmail2fa(twoFaTargetState, twoFaToken) }
                                .onSuccess {
                                    show2faCodeDialog = false
                                    Toast.makeText(
                                        context,
                                        if (twoFaTargetState) "Email 2FA enabled" else "Email 2FA disabled",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .onFailure { err ->
                                    twoFaError = err.message ?: "Invalid code"
                                }
                            twoFaBusy = false
                        }
                    },
                ) {
                    Text(if (twoFaBusy) "Confirming..." else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { show2faCodeDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun ActivityPrivacySettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf("followers") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(sessionState.currentDid) {
        val did = sessionState.currentDid
        if (did != null) {
            runCatching {
                val res = container.xrpcClient.getJson(
                    nsid = "com.atproto.repo.getRecord",
                    params = listOf(
                        "repo" to did,
                        "collection" to "app.bsky.notification.declaration",
                        "rkey" to "self",
                    ),
                ).jsonObject
                res["value"]?.jsonObject?.get("allowSubscriptions")?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.let { value ->
                selected = value
            }
        }
        loading = false
    }

    fun saveDeclaration(value: String) {
        val did = sessionState.currentDid ?: return
        val previous = selected
        selected = value
        haptics.selection()
        scope.launch {
            saving = true
            runCatching {
                container.api.putRecord(
                    repo = did,
                    collection = "app.bsky.notification.declaration",
                    rkey = "self",
                    record = buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.notification.declaration"))
                        put("allowSubscriptions", JsonPrimitive(value))
                    },
                )
            }.onFailure { err ->
                selected = previous
                Toast.makeText(context, err.message ?: "Failed to save preference", Toast.LENGTH_LONG).show()
            }
            saving = false
        }
    }

    SettingsScaffold(
        title = "Activity Subscriptions",
        onBack = onBack,
        testTag = "activity_privacy_settings_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsInfoCard(
                title = "Allow others to be notified of your posts",
                body = "This lets people receive notifications for your new posts and replies. Choose who can subscribe.",
                icon = Icons.Filled.NotificationsActive,
            )

            if (loading) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val options = listOf(
                    Triple("followers", "Followers", "Anyone who follows you can subscribe to your posts."),
                    Triple("mutuals", "Mutuals", "Only people you follow back can subscribe."),
                    Triple("none", "No one", "Disable post activity notifications for others."),
                )
                options.forEach { (value, title, sub) ->
                    SettingsRadioRow(
                        title = title,
                        subtitle = sub,
                        selected = selected == value,
                        enabled = !saving,
                        onClick = { saveDeclaration(value) },
                    )
                }
            }
        }
    }
}

private data class AppPasswordItem(
    val name: String,
    val createdAt: String,
    val privileged: Boolean,
)

@Composable
fun AppPasswordsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var passwords by remember { mutableStateOf<List<AppPasswordItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createdSecret by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmRevokeName by remember { mutableStateOf<String?>(null) }

    suspend fun reloadPasswords() {
        loading = true
        errorMsg = null
        runCatching {
            val res = container.api.listAppPasswords()
            val arr = res["passwords"]?.jsonArray.orEmpty()
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val privileged = obj["privileged"]?.jsonPrimitive?.booleanOrNull ?: false
                AppPasswordItem(name = name, createdAt = createdAt, privileged = privileged)
            }
        }.onSuccess {
            passwords = it
        }.onFailure { err ->
            errorMsg = err.message ?: "Failed to fetch app passwords"
        }
        loading = false
    }

    LaunchedEffect(Unit) { reloadPasswords() }

    SettingsScaffold(
        title = "App Passwords",
        onBack = onBack,
        testTag = "app_passwords_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsInfoCard(
                    title = "Client-specific passwords",
                    body = "Use app passwords to sign into third-party Bluesky & ATProto apps without sharing your main account password.",
                    icon = Icons.Filled.Key,
                )
            }

            item {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add App Password")
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                item { SettingsInfoCard(body = errorMsg.orEmpty(), isError = true) }
            }

            if (loading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (passwords.isEmpty()) {
                item {
                    Text(
                        text = "No app passwords created yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            } else {
                items(passwords, key = { it.name }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.contrast25.toComposeColor())
                            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.contrast1000.toComposeColor(),
                                )
                                if (item.privileged) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = palette.primary50.toComposeColor(),
                                    ) {
                                        Text(
                                            text = "Privileged",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.primary600.toComposeColor(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (item.createdAt.isNotBlank()) {
                                Text(
                                    text = "Created ${item.createdAt.take(10)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                        }
                        TextButton(onClick = { confirmRevokeName = item.name }) {
                            Text("Revoke", color = palette.negative500.toComposeColor())
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        var privileged by remember { mutableStateOf(false) }
        var creating by remember { mutableStateOf(false) }
        var createErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create App Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; createErr = null },
                        label = { Text("App password name") },
                        placeholder = { Text("e.g. Deck, Skylight, CLI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = privileged, onCheckedChange = { privileged = it })
                        Text(
                            text = "Allow access to direct messages (privileged)",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast800.toComposeColor(),
                        )
                    }
                    if (!createErr.isNullOrBlank()) {
                        Text(createErr.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !creating && nameInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            creating = true
                            createErr = null
                            runCatching {
                                container.api.createAppPassword(nameInput.trim(), privileged)
                            }.onSuccess { res ->
                                val secret = res["password"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val createdName = res["name"]?.jsonPrimitive?.contentOrNull ?: nameInput.trim()
                                showCreateDialog = false
                                createdSecret = createdName to secret
                                reloadPasswords()
                            }.onFailure { err ->
                                createErr = err.message ?: "Failed to create app password"
                            }
                            creating = false
                        }
                    },
                ) {
                    Text(if (creating) "Creating..." else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    createdSecret?.let { (name, secret) ->
        AlertDialog(
            onDismissRequest = { createdSecret = null },
            title = { Text("App Password Created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Copy the password for “$name” now. For security, it will only be shown this once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast700.toComposeColor(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.contrast50.toComposeColor())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = secret,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = palette.contrast1000.toComposeColor(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                LinkNavigationHelper.copyToClipboard(context, name, secret, "Copied app password")
                            },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        LinkNavigationHelper.copyToClipboard(context, name, secret, "Copied app password")
                        createdSecret = null
                    },
                ) {
                    Text("Copy & Close")
                }
            },
        )
    }

    confirmRevokeName?.let { nameToRevoke ->
        AlertDialog(
            onDismissRequest = { confirmRevokeName = null },
            title = { Text("Revoke “$nameToRevoke”?") },
            text = { Text("Any apps using this app password will immediately lose access.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                container.api.revokeAppPassword(nameToRevoke)
                                confirmRevokeName = null
                                reloadPasswords()
                                Toast.makeText(context, "Revoked $nameToRevoke", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to revoke", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) {
                    Text("Revoke", color = palette.negative500.toComposeColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeName = null }) { Text("Cancel") }
            },
        )
    }
}
