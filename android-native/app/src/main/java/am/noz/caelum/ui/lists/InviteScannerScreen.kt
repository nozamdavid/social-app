package am.noz.caelum.ui.lists

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkParser
import am.noz.caelum.ui.navigation.DeepLinkTarget
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScannerScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualInput by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var scanHandled by remember { mutableStateOf(false) }
    var lastInvalidScanRaw by remember { mutableStateOf<String?>(null) }
    var lastInvalidScanAtMs by remember { mutableStateOf(0L) }

    fun handleScannedOrEntered(rawInput: String, fromCamera: Boolean) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val route = parseInviteOrStarterPackInput(trimmed)
        if (route != null && route !is AppRoute.NotFound) {
            parseError = null
            if (fromCamera) {
                scanHandled = true
            }
            onNavigate(route)
        } else {
            val message = "Unrecognized starter pack or Bluesky link"
            parseError = message
            if (fromCamera) {
                val now = System.currentTimeMillis()
                if (trimmed != lastInvalidScanRaw || now - lastInvalidScanAtMs > 2_500L) {
                    lastInvalidScanRaw = trimmed
                    lastInvalidScanAtMs = now
                    scope.launch { snackbarHostState.showSnackbar(message) }
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
                title = { Text("Scan QR / Invite", fontWeight = FontWeight.SemiBold) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // CameraX live scanner preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasCameraPermission) {
                    val previewView = remember {
                        PreviewView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }

                    DisposableEffect(lifecycleOwner, previewView) {
                        val cameraExecutor = Executors.newSingleThreadExecutor()
                        val barcodeScanner = BarcodeScanning.getClient()
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val mainExecutor = ContextCompat.getMainExecutor(context)
                        var boundProvider: ProcessCameraProvider? = null
                        var disposed = false

                        cameraProviderFuture.addListener(
                            {
                                if (disposed) return@addListener
                                runCatching {
                                    val provider = cameraProviderFuture.get()
                                    if (disposed) return@addListener
                                    boundProvider = provider
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { imageAnalysis ->
                                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                if (disposed) {
                                                    imageProxy.close()
                                                    return@setAnalyzer
                                                }
                                                analyzeBarcodeFrame(
                                                    imageProxy = imageProxy,
                                                    scanner = barcodeScanner,
                                                    onBarcodeDetected = { raw ->
                                                        if (!disposed && !scanHandled) {
                                                            handleScannedOrEntered(raw, fromCamera = true)
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis,
                                    )
                                }
                            },
                            mainExecutor,
                        )

                        onDispose {
                            disposed = true
                            runCatching { boundProvider?.unbindAll() }
                            runCatching { barcodeScanner.close() }
                            runCatching { cameraExecutor.shutdown() }
                        }
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Scan reticle overlay
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)),
                    )
                    Text(
                        text = "Point camera at a starter pack or Bluesky QR code",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            text = "Camera permission is needed to scan QR codes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                    }
                }
            }

            // Manual URL / code fallback input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.contrast0.toComposeColor())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = palette.primary500.toComposeColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Or paste a link or starter pack code",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.contrast1000.toComposeColor(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = {
                            manualInput = it
                            parseError = null
                        },
                        placeholder = { Text("https://bsky.app/starter-pack/… or code") },
                        singleLine = true,
                        isError = parseError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { handleScannedOrEntered(manualInput, fromCamera = false) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { handleScannedOrEntered(manualInput, fromCamera = false) },
                        enabled = manualInput.trim().isNotEmpty(),
                    ) {
                        Text("Open")
                    }
                }

                if (parseError != null) {
                    Text(
                        text = parseError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.negative500.toComposeColor(),
                    )
                }
            }
        }
    }
}

@AndroidOptIn(ExperimentalGetImage::class)
private fun analyzeBarcodeFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onBarcodeDetected: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
            if (raw != null) {
                onBarcodeDetected(raw)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun parseInviteOrStarterPackInput(raw: String): AppRoute? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("at://")) {
        return DeepLinkParser.atUriToRoute(trimmed)
    }

    // Handle go.bsky.app short URLs
    val parsedUri = runCatching { Uri.parse(trimmed) }.getOrNull()
    if (parsedUri != null && parsedUri.host?.equals("go.bsky.app", ignoreCase = true) == true) {
        val code = parsedUri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        if (code != null) {
            return AppRoute.StarterPackShort(code)
        }
    }

    // Handle Android Play Store starter pack referrer links
    val referrer = parsedUri?.getQueryParameter("referrer")
    if (!referrer.isNullOrBlank()) {
        val refUri = Uri.parse("https://bsky.app/?$referrer")
        val utmContent = refUri.getQueryParameter("utm_content")
        if (!utmContent.isNullOrBlank() && utmContent.startsWith("starterpack_")) {
            val parts = utmContent.split('_')
            if (parts.size >= 3) {
                val name = parts[1]
                val rkey = parts.drop(2).joinToString("_")
                if (name.isNotBlank() && rkey.isNotBlank()) {
                    return AppRoute.StarterPack(name, rkey)
                }
            }
        }
    }

    // Parse full URLs or relative paths via DeepLinkParser
    if (trimmed.contains("://") || trimmed.startsWith("/")) {
        val target = DeepLinkParser.parseUri(trimmed)
        if (target is DeepLinkTarget.RouteTarget && target.route !is AppRoute.NotFound) {
            return target.route
        }
    }

    // Bare domain path without scheme (e.g. bsky.app/starter-pack/...)
    if (trimmed.startsWith("bsky.app/", ignoreCase = true)) {
        val target = DeepLinkParser.parseUri("https://$trimmed")
        if (target is DeepLinkTarget.RouteTarget && target.route !is AppRoute.NotFound) {
            return target.route
        }
    }

    // Bare alphanumeric short code
    if (trimmed.matches(Regex("^[A-Za-z0-9_-]{4,24}$"))) {
        return AppRoute.StarterPackShort(trimmed)
    }

    return null
}
