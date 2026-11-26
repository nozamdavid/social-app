package am.noz.caelum.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.I18nCatalog
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.CaelumLogoMark
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onBrowseGuest: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val container = LocalAppContainer.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val scope = rememberCoroutineScope()
    var langExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.contrast0.toComposeColor())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CaelumLogoMark(size = 54.dp)
            Text(
                text = "Caelum",
                style = MaterialTheme.typography.displayLarge.copy(color = palette.primary500.toComposeColor()),
            )
            Text(
                text = tr("What’s up?"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = palette.contrast1000.toComposeColor(),
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "A fast native Android client for Bluesky & the open ATProto atmosphere.",
                style = MaterialTheme.typography.bodyLarge.copy(color = palette.contrast700.toComposeColor()),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("welcome_create_account"),
            ) {
                Text(tr("Create account"))
            }
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("welcome_sign_in"),
            ) {
                Text(tr("Sign in"))
            }
            TextButton(
                onClick = onBrowseGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_browse_guest"),
            ) {
                Text("Explore public feeds")
            }

            Box {
                TextButton(onClick = { langExpanded = true }) {
                    val activeCode = settings.appLanguage.ifBlank { "en" }
                    val label = I18nCatalog.SUPPORTED_LANGUAGES.firstOrNull { it.code == activeCode }?.nativeName ?: "English"
                    Text("App language: $label")
                }
                DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                    I18nCatalog.SUPPORTED_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text("${lang.nativeName} (${lang.name})") },
                            onClick = {
                                langExpanded = false
                                scope.launch {
                                    container.preferences.setLanguages(appLanguage = lang.code)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var serviceOrigin by remember { mutableStateOf("https://bsky.social") }
    var showServerDialog by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var authFactorToken by remember { mutableStateOf("") }
    var needs2FA by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Caelum typeahead.waow.tech live suggestions
    var suggestions by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var suppressTypeahead by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }

    LaunchedEffect(identifier, serviceOrigin) {
        if (suppressTypeahead) {
            suppressTypeahead = false
            return@LaunchedEffect
        }
        val q = identifier.trim().removePrefix("@")
        if (q.length < 2 || '@' in q || serviceOrigin != "https://bsky.social") {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        suggestions = runCatching {
            container.api.searchLoginUsernameSuggestions(q, limit = 5)
        }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Sign in")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hosting provider selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                    .clickable { showServerDialog = true },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Dns, contentDescription = null, tint = palette.primary500.toComposeColor())
                        Column {
                            Text("Hosting provider", style = MaterialTheme.typography.labelMedium, color = palette.contrast600.toComposeColor())
                            Text(serviceOrigin.removePrefix("https://"), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Text("Change", color = palette.primary500.toComposeColor(), style = MaterialTheme.typography.labelLarge)
                }
            }

            OutlinedTextField(
                value = identifier,
                onValueChange = {
                    identifier = it
                    errorMessage = null
                },
                label = { Text("Username or email address") },
                placeholder = { Text("alice.bsky.social") },
                leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signin_identifier"),
            )

            if (suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.contrast25.toComposeColor(),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
                ) {
                    Column {
                        for (actor in suggestions) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        suppressTypeahead = true
                                        identifier = actor.handle
                                        suggestions = emptyList()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CaelumAvatar(url = actor.avatar, contentDescription = actor.handle, size = 32.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    if (!actor.displayName.isNullOrBlank()) {
                                        Text(actor.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                    Text("@${actor.handle}", style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor(), maxLines = 1)
                                }
                            }
                            HorizontalDivider(color = palette.contrast50.toComposeColor())
                        }
                    }
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle password visibility",
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signin_password"),
            )

            if (needs2FA) {
                OutlinedTextField(
                    value = authFactorToken,
                    onValueChange = { authFactorToken = it },
                    label = { Text("2FA Confirmation Code (sent to your email)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = palette.negative500.toComposeColor(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showForgotDialog = true }) {
                    Text("Forgot password?")
                }
                Button(
                    enabled = !isSubmitting && identifier.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            errorMessage = null
                            try {
                                container.sessionManager.signIn(
                                    serviceOrigin = serviceOrigin,
                                    identifier = identifier,
                                    password = password,
                                    authFactorToken = authFactorToken.takeIf { needs2FA && it.isNotBlank() },
                                )
                                onSignedIn()
                            } catch (ex: XrpcException) {
                                if (ex.error == "AuthFactorTokenRequired") {
                                    needs2FA = true
                                    errorMessage = "Please enter the confirmation code sent to your email."
                                } else {
                                    errorMessage = ex.message
                                }
                            } catch (ex: Exception) {
                                errorMessage = ex.message ?: "Unable to connect. Check network or server."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("signin_submit"),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(tr("Next"))
                    }
                }
            }
        }
    }

    if (showServerDialog) {
        var customUrl by remember { mutableStateOf(serviceOrigin) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Choose hosting provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bluesky Social is the default, or enter your custom PDS domain.")
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                    )
                    TextButton(onClick = { customUrl = "https://bsky.social" }) {
                        Text("Reset to bsky.social")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = customUrl.trim()
                    if (trimmed.isNotEmpty()) {
                        serviceOrigin = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
                    }
                    showServerDialog = false
                }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf("") }
        var resetToken by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var codeSent by remember { mutableStateOf(false) }
        var resetErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!codeSent) {
                        OutlinedTextField(value = resetEmail, onValueChange = { resetEmail = it }, label = { Text("Email address") }, singleLine = true)
                    } else {
                        OutlinedTextField(value = resetToken, onValueChange = { resetToken = it }, label = { Text("Reset code") }, singleLine = true)
                        OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    }
                    resetErr?.let { Text(it, color = palette.negative500.toComposeColor(), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            if (!codeSent) {
                                container.api.requestPasswordReset(serviceOrigin, resetEmail.trim())
                                codeSent = true
                            } else {
                                container.api.resetPassword(serviceOrigin, resetToken.trim(), newPass)
                                showForgotDialog = false
                            }
                        }.onFailure { resetErr = it.message }
                    }
                }) { Text(if (!codeSent) "Send code" else "Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private data class PendingSignup(
    val account: StoredAccount,
    val tokens: SessionTokens,
    val birthdate: LocalDate,
    val birthdateSaved: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupOnboardingScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var serviceOrigin by remember { mutableStateOf("https://bsky.social") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var selectedInterests by remember { mutableStateOf(setOf("news", "tech")) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pendingSignup by remember { mutableStateOf<PendingSignup?>(null) }
    var registeredDid by remember { mutableStateOf<String?>(null) }

    val interestChoices = remember {
        listOf("news", "tech", "art", "science", "gaming", "music", "books", "sports", "politics", "nature")
    }

    val missingBirthdateMessage = tr("Please enter your date of birth.")
    val invalidBirthdateMessage = tr("Enter a valid date of birth in YYYY-MM-DD format.")
    val underageMessage = tr("You must be at least 13 years old to create an account.")
    val signupErrorMessage = tr("Something went wrong, please try again.")
    val finishSignupError = tr("Your account was created, but setup could not be completed. Please try again.")

    fun goBack() {
        if (loading) return
        errorMsg = null
        if (step == 1 && pendingSignup == null) step = 0 else onBack()
    }

    fun validateBirthdate(): LocalDate? {
        val date = parseSignupBirthdate(birthdate)
        val today = LocalDate.now()
        errorMsg = when {
            birthdate.isBlank() -> missingBirthdateMessage
            date == null || date.isAfter(today) -> invalidBirthdateMessage
            !date.meetsSignupAge(today) -> underageMessage
            else -> null
        }
        return date.takeIf { errorMsg == null }
    }

    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create account (Step ${step + 1}/3)") },
                navigationIcon = {
                    IconButton(onClick = ::goBack, enabled = !loading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    Text("Account details", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = serviceOrigin, onValueChange = { serviceOrigin = it }, label = { Text("Hosting provider URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 8 chars)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = birthdate,
                        onValueChange = { birthdate = it },
                        label = { Text(tr("Date of birth")) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, label = { Text("Invite code (if required)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                1 -> {
                    Text("Choose your handle", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("Username / handle") },
                        placeholder = { Text("yourname.bsky.social") },
                        enabled = !loading && pendingSignup == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                2 -> {
                    Text("Select your interests", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            interestChoices.chunked(3).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { tag ->
                                        val selected = tag in selectedInterests
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                selectedInterests = if (selected) selectedInterests - tag else selectedInterests + tag
                                            },
                                            label = { Text(tag) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            errorMsg?.let {
                Text(it, color = palette.negative500.toComposeColor(), style = MaterialTheme.typography.bodySmall)
            }

            Button(
                enabled = !loading,
                onClick = {
                    if (loading) return@Button
                    errorMsg = null
                    when (step) {
                        0 -> {
                            if (email.isBlank() || password.length < 8) {
                                errorMsg = "Enter a valid email and an 8+ character password."
                            } else if (validateBirthdate() != null) {
                                step = 1
                            }
                        }
                        1 -> {
                            val existing = pendingSignup
                            val date = existing?.birthdate ?: validateBirthdate()
                            if (date == null) {
                                step = 0
                            } else if (existing == null && handle.trim().length < 3) {
                                errorMsg = "Choose a valid handle."
                            } else {
                                loading = true
                                scope.launch {
                                    try {
                                        var signup = existing ?: run {
                                            val service = serviceOrigin.trim().trimEnd('/')
                                            val enteredEmail = email.trim()
                                            val cleanHandle = handle.trim().let {
                                                if ('.' in it) it else "$it.bsky.social"
                                            }
                                            val res = container.api.createAccount(
                                                serviceOrigin = service,
                                                email = enteredEmail,
                                                handle = cleanHandle,
                                                password = password,
                                                inviteCode = inviteCode.trim().takeIf { it.isNotEmpty() },
                                            )
                                            val pds = container.sessionManager.extractPdsFromDidDoc(res.didDoc) ?: service
                                            PendingSignup(
                                                account = StoredAccount(
                                                    did = res.did,
                                                    handle = res.handle,
                                                    serviceUrl = service,
                                                    pdsUrl = pds,
                                                    email = res.email ?: enteredEmail,
                                                    emailConfirmed = res.emailConfirmed ?: false,
                                                ),
                                                tokens = SessionTokens(res.accessJwt, res.refreshJwt),
                                                birthdate = date,
                                            ).also {
                                                pendingSignup = it
                                                password = ""
                                            }
                                        }
                                        if (!signup.birthdateSaved) {
                                            container.api.setSignupBirthdate(
                                                pdsUrl = signup.account.pdsUrl ?: signup.account.serviceUrl,
                                                accessJwt = signup.tokens.accessJwt,
                                                birthdate = signup.birthdate,
                                            )
                                            signup = signup.copy(birthdateSaved = true)
                                            pendingSignup = signup
                                        }
                                        container.sessionManager.registerNewAccount(signup.account, signup.tokens)
                                        registeredDid = signup.account.did
                                        pendingSignup = null
                                        step = 2
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        errorMsg = if (pendingSignup == null) error.message ?: signupErrorMessage else finishSignupError
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        }
                        else -> {
                            val did = checkNotNull(registeredDid)
                            loading = true
                            scope.launch {
                                try {
                                    container.preferences.setInterests(did, selectedInterests.toList())
                                    onCompleted()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    errorMsg = signupErrorMessage
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp))
                else Text(
                    when {
                        pendingSignup != null -> tr("Try again")
                        step < 2 -> tr("Next")
                        else -> tr("Let’s go!")
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.state.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Switch account",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            for (acc in session.accounts) {
                val isCurrent = acc.did == session.currentDid
                ListItem(
                    leadingContent = {
                        CaelumAvatar(url = acc.avatar, contentDescription = acc.handle, size = 40.dp)
                    },
                    headlineContent = { Text(acc.displayName?.takeIf { it.isNotBlank() } ?: acc.handle) },
                    supportingContent = { Text("@${acc.handle}") },
                    trailingContent = {
                        if (isCurrent) {
                            Icon(Icons.Filled.Check, contentDescription = "Active account", tint = LocalAlfPalette.current.primary500.toComposeColor())
                        }
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        if (!isCurrent) {
                            onSwitchAccount(acc.did)
                        }
                    },
                )
            }
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                headlineContent = { Text("Add another account") },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddAccount()
                },
            )
            if (session.currentDid != null) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Close, contentDescription = null) },
                    headlineContent = { Text("Sign out of current account") },
                    modifier = Modifier.clickable {
                        val did = session.currentDid
                        onDismiss()
                        if (did != null) {
                            onRemoveAccount(did)
                        }
                    },
                )
            }
        }
    }
}
