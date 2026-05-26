package com.onemoresecret

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import android.net.Uri
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.onemoresecret.composable.PinEntry
import com.onemoresecret.composable.PinSetupViewModel
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.navigation.*
import com.onemoresecret.qr.MessageParser
import com.onemoresecret.qr.QRCodeAnalyzer
import java.util.BitSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import java.util.stream.IntStream

object QRScreen {
    const val PROP_USE_ZXING = "use_zxing"
    const val PROP_RECENT_SIZE = "recent_size"
    const val PROP_RECENT_ENTRIES = "recent_entries"
    const val PROP_PRESETS = "presets"
    const val DEF_RECENT_SIZE = 3
    const val RECENT_TTL = 12 * 60 * 60 * 1000L

    const val ARG_URI = "URI"
    const val ARG_MESSAGE = "MESSAGE"
    const val ARG_TEXT = "TEXT"
    const val ARG_APPLICATION_ID = "AI"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val preferences = activity.getPreferences(Context.MODE_PRIVATE)
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPairingIndicator by remember { mutableStateOf(false) }
    var remainingCodes by remember { mutableStateOf("") }
    var recentEntries by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var showPinEntry by remember { mutableStateOf(false) }

    var pinProtectedAction by remember { mutableStateOf<Runnable?>(null) }
    var pinProtectedCancel by remember { mutableStateOf<Runnable?>(null) }
    var pinProtectedEvaluateNext by remember { mutableStateOf(false) }
    var nextPinRequestTimestampOuter by remember { mutableStateOf(0L) }

    val messageReceived = remember { AtomicBoolean(false) }
    var lastReceivedChunks by remember { mutableStateOf<BitSet?>(null) }

    val cryptographyManager = remember { CryptographyManager() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    var isZxingEnabled by remember { mutableStateOf(BuildConfig.FLAVOR == Util.FLAVOR_FOSS || preferences.getBoolean(QRScreen.PROP_USE_ZXING, false)) }

    LaunchedEffect(Unit) {
        if (!preferences.getBoolean(PermissionsScreen.PROP_PERMISSIONS_REQUESTED, false)) {
            navController.navigate(PermissionsRoute)
        }
    }

    val callbacks = remember {
        object : QrMessageHandlerCallbacks {
            override val context: Context get() = activity
            override val activity: MainActivity get() = activity
            override val navController: NavController get() = navController
            override val preferences: SharedPreferences get() = preferences
            override val messageReceived: AtomicBoolean get() = messageReceived

            override var nextPinRequestTimestamp: Long
                get() = nextPinRequestTimestampOuter
                set(value) { nextPinRequestTimestampOuter = value }

            override fun runPinProtected(
                onSuccess: Runnable,
                onCancel: Runnable?,
                evaluateNextPinRequestTimestamp: Boolean
            ) {
                if (preferences.getBoolean(PinSetupViewModel.PIN_ENABLED, false) &&
                    (System.currentTimeMillis() > this.nextPinRequestTimestamp || !evaluateNextPinRequestTimestamp)
                ) {
                    pinProtectedAction = onSuccess
                    pinProtectedCancel = onCancel
                    pinProtectedEvaluateNext = evaluateNextPinRequestTimestamp
                    showPinEntry = true
                } else {
                    activity.mainExecutor.execute(onSuccess)
                }
            }
        }
    }

    val messageHandler = remember(callbacks, cryptographyManager) { QrMessageHandler(callbacks, cryptographyManager) }

    val parser = remember(messageHandler, messageReceived) {
        object : MessageParser() {
            override fun onMessage(message: String?) {
                message?.let { messageHandler.onMessage(it, true) }
            }

            override fun onChunkReceived(receivedChunks: BitSet?, cntReceived: Int, totalChunks: Int) {
                if (messageReceived.get()) return
                if (receivedChunks == lastReceivedChunks) return
                lastReceivedChunks = receivedChunks

                if (receivedChunks == null) return

                val s = IntStream.range(0, totalChunks)
                    .filter { i -> !receivedChunks.get(i) }
                    .mapToObj { i -> (i + 1).toString() }
                    .collect(Collectors.joining(", "))

                activity.mainExecutor.execute {
                    remainingCodes = s
                }
            }
        }
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val provider = cameraProvider!!
                val preview = Preview.Builder().build()
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView!!.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    object : QRCodeAnalyzer(isZxingEnabled) {
                        override fun onQRCodeFound(barcodeValue: String?) {
                            try {
                                barcodeValue?.let { parser.consume(it) }
                            } catch (e: Exception) {
                                Util.printStackTrace(e)
                            }
                        }
                    }
                )

                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Util.printStackTrace(e)
                Toast.makeText(context, String.format(context.getString(R.string.error_starting_camera), e.message), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, R.string.insufficient_permissions, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(previewView, isZxingEnabled) {
        if (previewView != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun checkBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_unavailable)
                    .setMessage(R.string.biometrics_unavailable_long_text)
                    .setIcon(R.drawable.baseline_fingerprint_24)
                    .setNegativeButton(R.string.exit) { _, _ -> activity.finish() }
                    .show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_not_detected)
                    .setMessage(R.string.biometrics_not_detected_long_text)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNegativeButton(R.string.exit) { _, _ -> activity.finish() }
                    .show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_not_enabled)
                    .setMessage(R.string.biometrics_not_enabled_long_text)
                    .setIcon(R.drawable.baseline_fingerprint_24)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }
                    .setNegativeButton(R.string.exit) { _, _ -> activity.finish() }
                    .show()
                return false
            }
            else -> return true
        }
    }

    fun onUri(uri: Uri) {
        val fileInfo = Util.getFileInfo(context, uri)
        if (fileInfo.filename.endsWith(".${MessageComposer.OMS_FILE_TYPE}")) {
            navController.navigate(MessageRoute(uriString = uri.toString()))
        } else {
            navController.navigate(FileEncryptionRoute(uriString = uri.toString()))
        }
    }

    fun processIntent(intent: Intent): Boolean {
        try {
            val action = intent.action
            val type = intent.type
            Log.d("QRScreen", "Intent action: $action, type: $type")

            when (action) {
                Intent.ACTION_VIEW -> {
                    val m = intent.getStringExtra("m")
                    if (m != null) {
                        messageHandler.onMessage(m, true)
                        return true
                    }
                    val uri = intent.data
                    if (uri == null) {
                        Toast.makeText(context, R.string.malformed_intent, Toast.LENGTH_LONG).show()
                    } else {
                        onUri(uri)
                        return true
                    }
                }
                Intent.ACTION_SEND -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text.isNullOrEmpty()) {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        if (uri != null) {
                            Log.d("QRScreen", "URI: $uri")
                            onUri(uri)
                            return true
                        }
                    } else {
                        if (MessageComposer.decode(text) == null) {
                            navController.navigate(EncryptTextRoute(text = text))
                        } else {
                            messageHandler.onMessage(text, true)
                        }
                        return true
                    }
                }
            }
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            Toast.makeText(context, ex.message ?: ex.javaClass.name, Toast.LENGTH_LONG).show()
        }
        return false
    }

    fun processClipboard() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()
            if (text != null) {
                messageHandler.onMessage(text, true)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (checkBiometrics()) {
                    if (!cryptographyManager.isMasterKeySetUp()) {
                        cryptographyManager.createMasterRsaKey(context)
                        Log.d("QRScreen", "RSA master key created")
                    }
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("QRScreen", "resuming...")
                messageReceived.set(false)
                showPairingIndicator = false

                activity.startWiFiListener(
                    { msg -> messageHandler.onMessage(msg, true) },
                    { activity.mainExecutor.execute { showPairingIndicator = activity.isWiFiCommSet } }
                )

                recentEntries = QrRecentManager.getRecentEntries(preferences)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                activity.destroyWiFiListener()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity.destroyWiFiListener()
        }
    }
    
    LaunchedEffect(activity.intent) {
        val intent = activity.intent
        if (intent != null) {
            activity.intent = null
            processIntent(intent)
        }
    }
    
    val menuProvider = remember {
        object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_qr, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.menuItemVersion).title = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})"
                menu.findItem(R.id.menuItemClearWiFiComm).isVisible = activity.isWiFiCommSet
                if (BuildConfig.FLAVOR == Util.FLAVOR_FOSS) {
                    menu.findItem(R.id.menuItemZxing).isVisible = false
                } else {
                    menu.findItem(R.id.menuItemZxing).isChecked = isZxingEnabled
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.menuItemQrPrivateKeys -> navController.navigate(KeyManagementRoute)
                    R.id.menuItemHelp -> Util.openUrl(R.string.qr_scanner_md_url, context)
                    R.id.menuItemPwdGenerator -> navController.navigate(PasswordGeneratorRoute)
                    R.id.menuItemHomePage -> Util.openUrl(R.string.readme_url, context)
                    R.id.menuItemPaste -> processClipboard()
                    R.id.menuItemFeedbackEmail -> {
                        val crashReportData = CrashReportData(null)
                        val sendEmail: (Boolean) -> Unit = { includeLogcat ->
                            try {
                                val intentSendTo = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret feedback")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.contact_email)))
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.feedback_prompt) + "\n\n" + crashReportData.toString(includeLogcat))
                                }
                                context.startActivity(intentSendTo)
                            } catch (ex: ActivityNotFoundException) {
                                activity.mainExecutor.execute {
                                    Toast.makeText(context, "Could not send email", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        
                        AlertDialog.Builder(context)
                            .setTitle("Include logcat into feedback?")
                            .setPositiveButton("Yes") { _, _ -> sendEmail(true) }
                            .setNegativeButton("No") { dialog, _ ->
                                sendEmail(false)
                                dialog.dismiss()
                            }
                            .show()
                    }
                    R.id.menuItemFeedbackDiscord -> Util.openUrl(R.string.discord_url, context)
                    R.id.menuItemEncryptText -> navController.navigate(EncryptTextRoute())
                    R.id.menuItemTotp -> navController.navigate(TotpManualEntryRoute)
                    R.id.menuItemPinSetup -> {
                        callbacks.runPinProtected({ navController.navigate(PinSetupRoute) }, null, false)
                    }
                    R.id.menuItemLogcat -> {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, CrashReportData(null).toString(true))
                            putExtra(Intent.EXTRA_TITLE, "Diagnose Data")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }
                    R.id.menuItemPanic -> {
                        if (preferences.getBoolean(PinSetupViewModel.PIN_ENABLED, false)) {
                            nextPinRequestTimestampOuter = 0
                            Thread {
                                OmsFileProvider.purgeTmp(context)
                                preferences.edit().remove(QRScreen.PROP_RECENT_ENTRIES).commit()
                                activity.mainExecutor.execute {
                                    recentEntries = emptyList()
                                }
                            }.start()
                            activity.mainExecutor.execute {
                                Toast.makeText(context, R.string.locked, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            activity.mainExecutor.execute {
                                Toast.makeText(context, R.string.enable_pin_first, Toast.LENGTH_LONG).show()
                            }
                            navController.navigate(PinSetupRoute)
                        }
                    }
                    R.id.menuItemCryptoAdrGen -> navController.navigate(CryptoCurrencyAddressRoute)
                    R.id.menuItemScreenshot -> {
                        menuItem.isChecked = !menuItem.isChecked
                        if (menuItem.isChecked) {
                            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                        activity.mainExecutor.execute {
                            Toast.makeText(
                                context,
                                "Screenshots ${if (menuItem.isChecked) "enabled" else "disabled"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    R.id.menuItemClearWiFiComm -> {
                        activity.setWiFiComm(null, null)
                        activity.mainExecutor.execute {
                            Toast.makeText(context, "WiFi Pairing cleared", Toast.LENGTH_LONG).show()
                        }
                        showPairingIndicator = activity.isWiFiCommSet
                    }
                    R.id.menuItemZxing -> {
                        val newZxing = !isZxingEnabled
                        preferences.edit().putBoolean(QRScreen.PROP_USE_ZXING, newZxing).apply()
                        isZxingEnabled = newZxing
                        activity.invalidateOptionsMenu()
                    }
                    else -> return false
                }
                return true
            }
        }
    }
    
    DisposableEffect(activity) {
        activity.addMenuProvider(menuProvider)
        onDispose { activity.removeMenuProvider(menuProvider) }
    }
    
    if (showPinEntry) {
        Dialog(
            onDismissRequest = {
                showPinEntry = false
                activity.mainExecutor.execute { pinProtectedCancel?.run() }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PinEntry(
                onDismissRequest = {
                    showPinEntry = false
                    activity.mainExecutor.execute { pinProtectedCancel?.run() }
                },
                onUnlock = { enteredPin ->
                    val panicPin = preferences.getString(PinSetupViewModel.PIN_PANIC, null)
                    val correctPin = preferences.getString(PinSetupViewModel.PIN_VALUE, null)
                    val panicPinEntered = (enteredPin == panicPin)

                    if (panicPinEntered) {
                        OmsFileProvider.purgeTmp(context)
                        cryptographyManager.deleteAndroidKeystoreEntries()
                        preferences.edit {
                            remove(QRScreen.PROP_RECENT_ENTRIES)
                            remove(QRScreen.PROP_PRESETS)
                            remove(CryptographyManager.PROP_KEYSTORE)
                            remove(CryptographyManager.MASTER_KEY_ALIAS)
                        }
                        activity.mainExecutor.execute {
                            recentEntries = QrRecentManager.getRecentEntries(preferences)
                        }
                        showPinEntry = false
                        return@PinEntry false
                    }
                    
                    if (enteredPin == correctPin) {
                        Toast.makeText(context, R.string.pin_accepted, Toast.LENGTH_SHORT).show()
                        if (pinProtectedEvaluateNext) {
                            val intervalMs = TimeUnit.MINUTES.toMillis(
                                preferences.getLong(PinSetupViewModel.PIN_REQUEST_INTERVAL_MINUTES, 0)
                            )
                            nextPinRequestTimestampOuter = if (intervalMs == 0L) Long.MAX_VALUE else System.currentTimeMillis() + intervalMs
                        }
                        activity.mainExecutor.execute { pinProtectedAction?.run() }
                        pinProtectedCancel = null
                        showPinEntry = false
                    } else {
                        var remainingAttempts = preferences.getInt(PinSetupViewModel.PIN_REMAINING_ATTEMPTS, Int.MAX_VALUE)
                        remainingAttempts--
                        preferences.edit { putInt(PinSetupViewModel.PIN_REMAINING_ATTEMPTS, remainingAttempts) }
                        if (remainingAttempts <= 0) {
                            OmsFileProvider.purgeTmp(context)
                            cryptographyManager.deleteAndroidKeystoreEntries()
                            preferences.edit {
                                remove(QRScreen.PROP_RECENT_ENTRIES)
                                remove(QRScreen.PROP_PRESETS)
                                remove(CryptographyManager.PROP_KEYSTORE)
                                remove(CryptographyManager.MASTER_KEY_ALIAS)
                            }
                            activity.mainExecutor.execute {
                                recentEntries = QrRecentManager.getRecentEntries(preferences)
                            }
                            showPinEntry = false
                        }
                        Toast.makeText(context, R.string.wrong_pin, Toast.LENGTH_LONG).show()
                        return@PinEntry false
                    }
                    return@PinEntry false
                }
            )
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = { processClipboard() }) {
                        Icon(painterResource(id = R.drawable.baseline_content_paste_24), contentDescription = "Paste")
                    }
                    IconButton(onClick = {
                        if (preferences.getBoolean(PinSetupViewModel.PIN_ENABLED, false)) {
                            nextPinRequestTimestampOuter = 0
                            Thread {
                                OmsFileProvider.purgeTmp(context)
                                preferences.edit().remove(QRScreen.PROP_RECENT_ENTRIES).commit()
                                activity.mainExecutor.execute { recentEntries = emptyList() }
                            }.start()
                            activity.mainExecutor.execute {
                                Toast.makeText(context, R.string.locked, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            activity.mainExecutor.execute {
                                Toast.makeText(context, R.string.enable_pin_first, Toast.LENGTH_LONG).show()
                            }
                            navController.navigate(PinSetupRoute)
                        }
                    }) {
                        Icon(painterResource(id = R.drawable.baseline_lock_24), contentDescription = "Panic")
                    }
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.password_generator)) },
                            onClick = { expanded = false; navController.navigate(PasswordGeneratorRoute) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.crypto_address_generator)) },
                            onClick = { expanded = false; navController.navigate(CryptoCurrencyAddressRoute) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.time_based_otp)) },
                            onClick = { expanded = false; navController.navigate(TotpManualEntryRoute) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.encrypt_text)) },
                            onClick = { expanded = false; navController.navigate(EncryptTextRoute()) }
                        )
                        if (activity.isWiFiCommSet) {
                            DropdownMenuItem(
                                text = { androidx.compose.material3.Text(stringResource(R.string.clear_wifi_pairing)) },
                                onClick = {
                                    expanded = false
                                    activity.setWiFiComm(null, null)
                                    activity.mainExecutor.execute {
                                        Toast.makeText(context, "WiFi Pairing cleared", Toast.LENGTH_LONG).show()
                                    }
                                    showPairingIndicator = activity.isWiFiCommSet
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.pin_setup)) },
                            onClick = {
                                expanded = false
                                callbacks.runPinProtected({ navController.navigate(PinSetupRoute) }, null, false)
                            }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.action_private_keys)) },
                            onClick = { expanded = false; navController.navigate(KeyManagementRoute) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(if (isZxingEnabled) "ZXing Enabled (On)" else "ZXing Enabled (Off)") },
                            onClick = {
                                expanded = false
                                val newZxing = !isZxingEnabled
                                preferences.edit().putBoolean(QRScreen.PROP_USE_ZXING, newZxing).apply()
                                isZxingEnabled = newZxing
                            }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.logcat)) },
                            onClick = {
                                expanded = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, CrashReportData(null).toString(true))
                                    putExtra(Intent.EXTRA_TITLE, "Diagnose Data")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.project_home_page)) },
                            onClick = { expanded = false; Util.openUrl(R.string.readme_url, context) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(stringResource(R.string.help)) },
                            onClick = { expanded = false; Util.openUrl(R.string.qr_scanner_md_url, context) }
                        )
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(" ()") },
                            onClick = { expanded = false }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showPairingIndicator) {
                Image(
                    painter = painterResource(id = R.drawable.leak_add),
                    contentDescription = "Wi-Fi Pairing Active",
                    modifier = Modifier
                        .size(128.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.remaining_codes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = remainingCodes,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            if (recentEntries.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.recent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentEntries) { entry ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { messageHandler.onMessage(entry.message, false) }
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(id = entry.drawableId),
                                contentDescription = "Recent Message"
                            )
                        }
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        text = ctx.getText(R.string.qr_banner)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        text = ctx.getText(R.string.qr_banner1)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )
        }
    }
    }
}
