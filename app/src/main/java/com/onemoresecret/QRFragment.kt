package com.onemoresecret

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.onemoresecret.composable.PinSetupViewModel
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.qr.MessageParser
import com.onemoresecret.qr.QRCodeAnalyzer
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import java.util.stream.IntStream

class QRFragment : Fragment() {
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val menuProvider = QrMenuProvider()
    private lateinit var clipboardManager: ClipboardManager
    lateinit var preferences: SharedPreferences
    private lateinit var parser: MessageParser
    val messageReceived = AtomicBoolean(false)
    var nextPinRequestTimestamp: Long = 0
    private val cryptographyManager = CryptographyManager()
    private lateinit var messageHandler: QrMessageHandler

    // Compose states
    private val showPairingIndicator = mutableStateOf(false)
    private val remainingCodes = mutableStateOf("")
    private val recentEntries = mutableStateOf<List<RecentEntry>>(emptyList())

    private val updateWiFiPairingIndicator = Runnable {
        requireActivity().mainExecutor.execute {
            showPairingIndicator.value = (requireActivity() as MainActivity).isWiFiCommSet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.onemoresecret.composable.OneMoreSecretTheme {
                    QRScreen(
                        onPreviewViewCreated = { previewView ->
                            if (ContextCompat.checkSelfPermission(
                                    requireContext(),
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startCamera(previewView)
                            } else {
                                registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                                    if (result == true) {
                                        startCamera(previewView)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.insufficient_permissions,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }.launch(Manifest.permission.CAMERA)
                            }
                        },
                        showPairingIndicator = showPairingIndicator.value,
                        remainingCodes = remainingCodes.value,
                        recentEntries = recentEntries.value,
                        onRecentEntryClicked = { entry ->
                            messageHandler.onMessage(entry.message, false)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showPairingIndicator.value = false

        if (requireActivity().supportFragmentManager.backStackEntryCount != 0) {
            Log.w(TAG, "Discarding back stack")
            Util.discardBackStack(this)
        }

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        if (!preferences.getBoolean(PermissionsFragment.PROP_PERMISSIONS_REQUESTED, false)) {
            NavHostFragment.findNavController(this)
                .navigate(R.id.action_QRFragment_to_permissionsFragment)
            return
        }

        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        messageHandler = QrMessageHandler(this, cryptographyManager)

        requireActivity().addMenuProvider(menuProvider)

        val intent = requireActivity().intent
        if (intent != null) {
            requireActivity().intent = null
            if (processIntent(intent)) return
        }

        parser = object : MessageParser() {
            override fun onMessage(message: String?) {
                if (message != null) messageHandler.onMessage(message, true)
            }

            override fun onChunkReceived(receivedChunks: BitSet?, cntReceived: Int, totalChunks: Int) {
                if (receivedChunks != null) this@QRFragment.onChunkReceived(receivedChunks, cntReceived, totalChunks)
            }
        }
    }

    private fun isZxingEnabled(): Boolean {
        return BuildConfig.FLAVOR == Util.FLAVOR_FOSS || preferences.getBoolean(PROP_USE_ZXING, false)
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as MainActivity).destroyWiFiListener()
    }

    override fun onStart() {
        super.onStart()
        if (!checkBiometrics()) return
        if (!cryptographyManager.isMasterKeySetUp()) {
            cryptographyManager.createMasterRsaKey(requireContext())
            Log.d(TAG, "RSA master key created")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "resuming...")
        messageReceived.set(false)
        showPairingIndicator.value = false

        (requireActivity() as MainActivity).startWiFiListener(
            { msg -> messageHandler.onMessage(msg, true) },
            updateWiFiPairingIndicator
        )

        loadRecentButtons()
    }

    private fun loadRecentButtons() {
        recentEntries.value = QrRecentManager.getRecentEntries(preferences)
    }

    private fun processClipboard() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()
            if (text != null) {
                messageHandler.onMessage(text, true)
            }
        }
    }

    private fun processIntent(intent: Intent): Boolean {
        try {
            val action = intent.action
            val type = intent.type
            Log.d(TAG, "Intent action: $action, type: $type")

            when (action) {
                Intent.ACTION_VIEW -> {
                    val m = intent.getStringExtra("m")
                    if (m != null) {
                        messageHandler.onMessage(m, true)
                        return true
                    }
                    val uri = intent.data
                    if (uri == null) {
                        Toast.makeText(requireContext(), R.string.malformed_intent, Toast.LENGTH_LONG).show()
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
                            Log.d(TAG, "URI: $uri")
                            onUri(uri)
                            return true
                        }
                    } else {
                        if (MessageComposer.decode(text) == null) {
                            val bundle = Bundle().apply {
                                putString(ARG_TEXT, text)
                            }
                            NavHostFragment.findNavController(this)
                                .navigate(R.id.action_QRFragment_to_encryptTextFragment, bundle)
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

    private fun onUri(uri: Uri) {
        val bundle = Bundle().apply {
            putParcelable(ARG_URI, uri)
        }
        val fileInfo = Util.getFileInfo(requireContext(), uri)

        if (fileInfo.filename.endsWith("." + MessageComposer.OMS_FILE_TYPE)) {
            Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
            NavHostFragment.findNavController(this)
                .navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
        } else {
            Log.d(TAG, "calling " + FileEncryptionFragment::class.java.simpleName)
            NavHostFragment.findNavController(this)
                .navigate(R.id.action_QRFragment_to_fileEncryptionFragment, bundle)
        }
    }

    private fun checkBiometrics(): Boolean {
        val biometricManager = requireContext().getSystemService(Context.BIOMETRIC_SERVICE) as BiometricManager

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_unavailable)
                    .setMessage(R.string.biometrics_unavailable_long_text)
                    .setIcon(R.drawable.baseline_fingerprint_24)
                    .setNegativeButton(R.string.exit) { _, _ -> requireActivity().finish() }
                    .show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_not_detected)
                    .setMessage(R.string.biometrics_not_detected_long_text)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNegativeButton(R.string.exit) { _, _ -> requireActivity().finish() }
                    .show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.biometrics_not_enabled)
                    .setMessage(R.string.biometrics_not_enabled_long_text)
                    .setIcon(R.drawable.baseline_fingerprint_24)
                    .setPositiveButton(R.string.open_settings) { _, _ -> startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    .setNegativeButton(R.string.exit) { _, _ -> requireActivity().finish() }
                    .show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_IDENTITY_CHECK_NOT_ACTIVE -> {
            }
        }
        return true
    }

    private fun startCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis?.setAnalyzer(
                    ContextCompat.getMainExecutor(requireContext()),
                    object : QRCodeAnalyzer(isZxingEnabled()) {
                        override fun onQRCodeFound(barcodeValue: String?) {
                            if (barcodeValue == null) return
                            try {
                                parser.consume(barcodeValue)
                            } catch (e: Exception) {
                                Util.printStackTrace(e)
                            }
                        }
                    }
                )

                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Util.printStackTrace(e)
                Toast.makeText(context, String.format(getString(R.string.error_starting_camera), e.message), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    fun runPinProtected(
        onSuccess: Runnable,
        onCancel: Runnable?,
        evaluateNextPinRequestTimestamp: Boolean
    ) {
        if (preferences.getBoolean(PinSetupViewModel.PIN_ENABLED, false) &&
            (System.currentTimeMillis() > nextPinRequestTimestamp || !evaluateNextPinRequestTimestamp)
        ) {
            PinEntryFragment(
                {
                    if (evaluateNextPinRequestTimestamp) {
                        val intervalMs = TimeUnit.MINUTES.toMillis(
                            preferences.getLong(PinSetupViewModel.PIN_REQUEST_INTERVAL_MINUTES, 0)
                        )
                        nextPinRequestTimestamp = if (intervalMs == 0L) Long.MAX_VALUE else System.currentTimeMillis() + intervalMs
                    }
                    onSuccess.run()
                },
                onCancel,
                {
                    requireActivity().mainExecutor.execute { loadRecentButtons() }
                }
            ).show(requireActivity().supportFragmentManager, null)
        } else {
            requireContext().mainExecutor.execute(onSuccess)
        }
    }

    private var lastReceivedChunks: BitSet? = null

    private fun onChunkReceived(receivedChunks: BitSet, cntReceived: Int, totalChunks: Int) {
        if (messageReceived.get()) return
        if (receivedChunks == lastReceivedChunks) return
        lastReceivedChunks = receivedChunks

        val s = IntStream.range(0, totalChunks)
            .filter { i -> !receivedChunks.get(i) }
            .mapToObj { i -> (i + 1).toString() }
            .collect(Collectors.joining(", "))

        requireContext().mainExecutor.execute {
            remainingCodes.value = s
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as MainActivity).destroyWiFiListener()
        requireActivity().removeMenuProvider(menuProvider)
        cameraProvider?.unbindAll()
    }

    private inner class QrMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_qr, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            menu.findItem(R.id.menuItemVersion).title = String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)
            menu.findItem(R.id.menuItemClearWiFiComm).isVisible = (requireActivity() as MainActivity).isWiFiCommSet
            if (BuildConfig.FLAVOR == Util.FLAVOR_FOSS) {
                menu.findItem(R.id.menuItemZxing).isVisible = false
            } else {
                menu.findItem(R.id.menuItemZxing).isChecked = isZxingEnabled()
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.menuItemQrPrivateKeys -> {
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_keyManagementFragment)
                }
                R.id.menuItemHelp -> {
                    Util.openUrl(R.string.qr_scanner_md_url, requireContext())
                }
                R.id.menuItemPwdGenerator -> {
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_passwordGeneratorFragment)
                }
                R.id.menuItemHomePage -> {
                    Util.openUrl(R.string.readme_url, requireContext())
                }
                R.id.menuItemPaste -> {
                    processClipboard()
                }
                R.id.menuItemFeedbackEmail -> {
                    val crashReportData = CrashReportData(null)
                    val sendEmail: (Boolean) -> Unit = { includeLogcat ->
                        try {
                            val intentSendTo = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret feedback")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.contact_email)))
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    getString(R.string.feedback_prompt) + "\n\n" + crashReportData.toString(includeLogcat)
                                )
                            }
                            startActivity(intentSendTo)
                        } catch (ex: ActivityNotFoundException) {
                            requireContext().mainExecutor.execute {
                                Toast.makeText(context, "Could not send email", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    AlertDialog.Builder(requireContext())
                        .setTitle("Include logcat into feedback?")
                        .setPositiveButton("Yes") { _, _ -> sendEmail(true) }
                        .setNegativeButton("No") { dialogInterface, _ ->
                            sendEmail(false)
                            dialogInterface.dismiss()
                        }
                        .create()
                        .show()
                }
                R.id.menuItemFeedbackDiscord -> {
                    Util.openUrl(R.string.discord_url, requireContext())
                }
                R.id.menuItemEncryptText -> {
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_encryptTextFragment)
                }
                R.id.menuItemTotp -> {
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_totpManualEntryFragment)
                }
                R.id.menuItemPinSetup -> {
                    runPinProtected(
                        {
                            NavHostFragment.findNavController(this@QRFragment)
                                .navigate(R.id.action_QRFragment_to_pinSetupFragment)
                        },
                        null,
                        false
                    )
                }
                R.id.menuItemLogcat -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, CrashReportData(null).toString(true))
                        putExtra(Intent.EXTRA_TITLE, "Diagnose Data")
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, null))
                }
                R.id.menuItemPanic -> {
                    if (preferences.getBoolean(PinSetupViewModel.PIN_ENABLED, false)) {
                        nextPinRequestTimestamp = 0
                        Thread {
                            OmsFileProvider.purgeTmp(requireContext())
                            if (preferences.contains(PROP_RECENT_ENTRIES)) {
                                preferences.edit().remove(PROP_RECENT_ENTRIES).commit()
                            }
                            requireContext().mainExecutor.execute { loadRecentButtons() }
                        }.start()
                        requireContext().mainExecutor.execute {
                            Toast.makeText(context, R.string.locked, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        requireContext().mainExecutor.execute {
                            Toast.makeText(context, R.string.enable_pin_first, Toast.LENGTH_LONG).show()
                        }
                        NavHostFragment.findNavController(this@QRFragment)
                            .navigate(R.id.action_QRFragment_to_pinSetupFragment)
                    }
                }
                R.id.menuItemCryptoAdrGen -> {
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_cryptoCurrencyAddressGenerator)
                }
                R.id.menuItemScreenshot -> {
                    menuItem.isChecked = !menuItem.isChecked
                    if (menuItem.isChecked) {
                        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    requireContext().mainExecutor.execute {
                        Toast.makeText(
                            context,
                            String.format("Screenshots %s", if (menuItem.isChecked) "enabled" else "disabled"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                R.id.menuItemClearWiFiComm -> {
                    (requireActivity() as MainActivity).setWiFiComm(null, null)
                    requireContext().mainExecutor.execute {
                        Toast.makeText(context, "WiFi Pairing cleared", Toast.LENGTH_LONG).show()
                    }
                    updateWiFiPairingIndicator.run()
                }
                R.id.menuItemZxing -> {
                    preferences.edit().putBoolean(PROP_USE_ZXING, !isZxingEnabled()).commit()
                    requireActivity().invalidateOptionsMenu()
                }
                else -> return false
            }
            return true
        }
    }

    companion object {
        private val TAG = QRFragment::class.java.simpleName
        const val PROP_USE_ZXING = "use_zxing"
        const val PROP_RECENT_ENTRIES = "recent_entries"
        const val PROP_PRESETS = "presets"

        const val ARG_URI = "URI"
        const val ARG_MESSAGE = "MESSAGE"
        const val ARG_TEXT = "TEXT"
        const val ARG_APPLICATION_ID = "AI"
    }
}
