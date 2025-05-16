package com.onemoresecret

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.getFileInfo
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.MessageComposer.Companion.decode
import com.onemoresecret.crypto.MessageComposer.Companion.getDrawableIdForApplicationId
import com.onemoresecret.crypto.MessageComposer.Companion.readRsaAesEnvelope
import com.onemoresecret.crypto.MessageComposer.RsaAesEnvelope
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.databinding.FragmentQrBinding
import com.onemoresecret.qr.MessageParser
import com.onemoresecret.qr.QRCodeAnalyzer
import java.io.ByteArrayInputStream
import java.util.BitSet
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.IntStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit

class QRFragment : Fragment() {
    private var binding: FragmentQrBinding? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val menuProvider = QrMenuProvider()
    private var clipboardManager: ClipboardManager? = null
    private var preferences: SharedPreferences? = null
    private var parser: MessageParser? = null
    private val messageReceived = AtomicBoolean(false)
    private var nextPinRequestTimestamp: Long = 0
    private val updateWiFiPairingIndicator = Runnable {
        requireActivity()
            .mainExecutor
            .execute {
                if (binding == null) return@execute
                binding!!.imgPairing.visibility =
                    if ((requireActivity() as MainActivity).isWiFiCommSet) View.VISIBLE else View.INVISIBLE
            }
    }

    private val recentButtons: MutableList<ImageButton> = ArrayList()

    private val loadedPresets: MutableList<PresetFragment> = ArrayList()

    @JvmRecord
    data class RecentEntry(
        @field:JsonProperty("message") @param:JsonProperty("message") val message: String,
        @field:JsonProperty("drawableId") @param:JsonProperty("drawableId") val drawableId: Int,
        @field:JsonProperty("ttl") @param:JsonProperty("ttl") val ttl: Long
    )

    @JvmRecord
    data class PresetEntry(
        @field:JsonProperty("symbol") @param:JsonProperty("symbol") val symbol: String,
        @field:JsonProperty("name") @param:JsonProperty("name") val name: String,
        @field:JsonProperty("message") @param:JsonProperty("message") val message: String
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentQrBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding!!.imgPairing.visibility = View.INVISIBLE

        if (requireActivity().supportFragmentManager.backStackEntryCount != 0) {
            Log.w(TAG, "Discarding back stack")
            discardBackStack(this)
        }

        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        if (!preferences.getBoolean(PermissionsFragment.PROP_PERMISSIONS_REQUESTED, false)) {
            NavHostFragment.findNavController(this@QRFragment)
                .navigate(R.id.action_QRFragment_to_permissionsFragment)
            return
        }

        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        requireActivity().addMenuProvider(menuProvider)

        val intent = requireActivity().intent
        if (intent != null) {
            requireActivity().intent = null
            if (processIntent(intent)) return
        }

        //enable camera
        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            registerForActivityResult(
                RequestPermission()
            ) { result: Boolean? ->
                if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    Toast.makeText(context, R.string.insufficient_permissions, Toast.LENGTH_LONG)
                        .show()
                }
            }.launch(Manifest.permission.CAMERA)
        }

        parser = object : MessageParser() {
            override fun onMessage(message: String) {
                this@QRFragment.onMessage(message, true)
            }

            override fun onChunkReceived(
                receivedChunks: BitSet,
                cntReceived: Int,
                totalChunks: Int
            ) {
                this@QRFragment.onChunkReceived(receivedChunks, cntReceived, totalChunks)
            }
        }

        //feature under construction
        binding!!.txtPresets.visibility = View.INVISIBLE
        binding!!.flexLOutPresets.visibility = View.INVISIBLE
    }

    private val isZxingEnabled: Boolean
        get() = BuildConfig.FLAVOR == Util.FLAVOR_FOSS /*in the FOSS version, ZXing is the only QR engine */
                || preferences!!.getBoolean(PROP_USE_ZXING, false)

    private fun loadPresets() {
        //dummy presets
        val fragmentManager = childFragmentManager
        val containerId = binding!!.flexLOutPresets.id
        val trx = fragmentManager.beginTransaction()

        loadedPresets.forEach(Consumer { fragment: PresetFragment? ->
            trx.remove(
                fragment!!
            )
        }) //clear presets from the last call

        try {
            val presetJson =
                preferences!!.getString(PROP_PRESETS, "[]")!!
            val presetEntries: List<PresetEntry> = Util.JACKSON_MAPPER.readValue<List<PresetEntry>>(
                presetJson,
                object : TypeReference<List<PresetEntry>?>() {
                })

            for (i in presetEntries.indices) {
                val presetEntry = presetEntries[i]
                val presetFragment = PresetFragment(
                    presetEntry,
                    { e: PresetEntry -> onMessage(e.message, true) },
                    { e: PresetEntry? -> Log.d(TAG, "long click!") }) //TODO
                loadedPresets.add(presetFragment)
                trx.add(containerId, presetFragment)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        //finalize UI change
        trx.commit()
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as MainActivity).destroyWiFiListener()
    }

    override fun onStart() {
        super.onStart()
        checkBiometrics()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "resuming...")
        //get ready to receive new messages
        messageReceived.set(false)
        binding!!.imgPairing.visibility = View.INVISIBLE

        (requireActivity() as MainActivity).startWiFiListener(
            { msg: String -> onMessage(msg, true) },
            updateWiFiPairingIndicator
        )

        loadRecentButtons()

        loadPresets()
    }

    private fun processIntent(intent: Intent?): Boolean {
        try {
            if (intent != null) {
                val action = intent.action
                val type = intent.type
                Log.d(TAG, "Intent action: $action, type: $type")

                when (Objects.requireNonNull(intent.action)) {
                    Intent.ACTION_VIEW -> {
                        val uri = intent.data
                        if (uri == null) {
                            Toast.makeText(
                                requireContext(),
                                R.string.malformed_intent,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            onUri(uri)
                            return true
                        }
                    }

                    Intent.ACTION_SEND -> {
                        //a piece of text has been sent to the app using Android "send to" functionality
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                        if (text == null || text.isEmpty()) {
                            val uri =
                                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri?
                            if (uri != null) {
                                Log.d(TAG, "URI: $uri")
                                onUri(uri)
                                return true
                            }
                        } else {
                            if (decode(text) == null) {
                                //this is not an OMS message, forward it to the text encryption fragment
                                val bundle = Bundle()
                                bundle.putString(ARG_TEXT, text)

                                NavHostFragment.findNavController(this@QRFragment)
                                    .navigate(R.id.action_QRFragment_to_encryptTextFragment, bundle)
                            } else {
                                onMessage(text, true)
                            }
                            return true
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(
                context,
                Objects.requireNonNullElse(ex.message, ex.javaClass.name),
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    private fun onUri(uri: Uri) {
        val bundle = Bundle()
        bundle.putParcelable(ARG_URI, uri)

        val fileInfo = getFileInfo(requireContext(), uri)

        if (fileInfo.filename.endsWith("." + MessageComposer.OMS_FILE_TYPE)) {
            Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
            NavHostFragment.findNavController(this@QRFragment)
                .navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
        } else {
            //pass URI to file encoder
            Log.d(TAG, "calling " + FileEncryptionFragment::class.java.simpleName)
            NavHostFragment.findNavController(this@QRFragment)
                .navigate(R.id.action_QRFragment_to_fileEncryptionFragment, bundle)
        }
    }

    private fun checkBiometrics() {
        val biometricManager =
            requireContext().getSystemService(Context.BIOMETRIC_SERVICE) as BiometricManager

        when (biometricManager.canAuthenticate
            (
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
        )) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AlertDialog.Builder(
                context
            )
                .setTitle(R.string.biometrics_unavailable)
                .setMessage(R.string.biometrics_unavailable_long_text)
                .setIcon(R.drawable.baseline_fingerprint_24)
                .setNegativeButton(
                    R.string.exit
                ) { dialog: DialogInterface?, which: Int -> requireActivity().finish() }
                .show()

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AlertDialog.Builder(
                context
            )
                .setTitle(R.string.biometrics_not_detected)
                .setMessage(R.string.biometrics_not_detected_long_text)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNegativeButton(
                    R.string.exit
                ) { dialog: DialogInterface?, which: Int -> requireActivity().finish() }
                .show()

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AlertDialog.Builder(
                context
            )
                .setTitle(R.string.biometrics_not_enabled)
                .setMessage(R.string.biometrics_not_enabled_long_text)
                .setIcon(R.drawable.baseline_fingerprint_24)
                .setPositiveButton(
                    R.string.open_settings
                ) { dialog: DialogInterface?, which: Int -> startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                .setNegativeButton(
                    R.string.exit
                ) { dialog: DialogInterface?, which: Int -> requireActivity().finish() }
                .show()

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BiometricManager.BIOMETRIC_SUCCESS -> {}
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview =
                    Preview.Builder().build()
                preview.surfaceProvider = binding!!.cameraPreview.surfaceProvider

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                imageAnalysis!!.setAnalyzer(
                    requireContext().mainExecutor,
                    object : QRCodeAnalyzer(this@QRFragment.isZxingEnabled) {
                        override fun onQRCodeFound(barcodeValue: String?) {
                            try {
                                parser!!.consume(barcodeValue!!)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    })

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    String.format(getString(R.string.error_starting_camera), e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, requireContext().mainExecutor)
    }

    /**
     * Try to process inbound message
     *
     * @param message       Message in OMS format
     * @param callSetRecent add this message to recent messages
     * @see MessageComposer
     */
    private fun onMessage(message: String, callSetRecent: Boolean) {
        if (messageReceived.get()) return
        messageReceived.set(true)

        val bArr = decode(message)
        if (bArr == null) {
            Toast.makeText(context, getString(R.string.could_not_decode), Toast.LENGTH_LONG).show()
            messageReceived.set(false)
            return
        }

        try {
            ByteArrayInputStream(bArr).use { bais ->
                OmsDataInputStream(bais).use { dataInputStream ->
                    val applicationId = dataInputStream.readUnsignedShort()
                    val bundle = Bundle()
                    bundle.putByteArray(ARG_MESSAGE, bArr)
                    bundle.putInt(ARG_APPLICATION_ID, applicationId)
                    val navController = NavHostFragment.findNavController(this@QRFragment)

                    //other supported formats?
                    if (OneTimePassword(message).isValid) {
                        //time based OTP
                        Log.d(TAG, "calling " + TotpImportFragment::class.java.simpleName)
                        navController.navigate(R.id.action_QRFragment_to_TotpImportFragment, bundle)
                    } else {
                        Log.d(TAG, "Application-ID: " + Integer.toHexString(applicationId))

                        var closeSocketWaitingForReply =
                            true //socket not used for reply, close immediately

                        when (applicationId) {
                            MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER -> {
                                //key import is not PIN protected
                                Log.d(TAG, "calling " + KeyImportFragment::class.java.simpleName)
                                navController.navigate(
                                    R.id.action_QRFragment_to_keyImportFragment,
                                    bundle
                                )
                            }

                            MessageComposer.APPLICATION_KEY_REQUEST, MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                                closeSocketWaitingForReply = false

                                runPinProtected(
                                    {
                                        Log.d(
                                            TAG,
                                            "calling " + MessageFragment::class.java.simpleName
                                        )
                                        navController.navigate(
                                            R.id.action_QRFragment_to_MessageFragment,
                                            bundle
                                        )
                                    }, {
                                        //close socket if WiFiPairing active
                                        (requireActivity() as MainActivity).sendReplyViaSocket(
                                            byteArrayOf(),
                                            true
                                        )
                                        //enable message processing again
                                        messageReceived.set(false)
                                    },
                                    true
                                )
                            }

                            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED, MessageComposer.APPLICATION_TOTP_URI_DEPRECATED, MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                                dataInputStream.reset()
                                val rsaAesEnvelope = readRsaAesEnvelope(dataInputStream)
                                //(7) - cipher text
                                val cipherText = dataInputStream.readByteArray()
                                runPinProtected(
                                    {
                                        showBiometricPromptForDecryption(
                                            rsaAesEnvelope.fingerprint,
                                            rsaAesEnvelope.rsaTransormation,
                                            getAuthenticationCallback(
                                                rsaAesEnvelope,
                                                cipherText,
                                                if (callSetRecent) Optional.of(
                                                    message
                                                ) else Optional.empty()
                                            )
                                        )
                                    }, {
                                        //close socket if WiFiPairing active
                                        (requireActivity() as MainActivity).sendReplyViaSocket(
                                            byteArrayOf(),
                                            true
                                        )
                                        //enable message processing again
                                        messageReceived.set(false)
                                    },
                                    true
                                )
                            }

                            else -> Log.e(
                                TAG,
                                "No processor defined for application ID " +
                                        Integer.toHexString(applicationId)
                            )
                        }

                        if (closeSocketWaitingForReply) {
                            //close socket if WiFiPairing active
                            (requireActivity() as MainActivity).sendReplyViaSocket(
                                byteArrayOf(),
                                true
                            )
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            messageReceived.set(false)
            //close socket if WiFiPairing active
            (requireActivity() as MainActivity).sendReplyViaSocket(byteArrayOf(), true)
        }
    }


    private fun setRecent(message: String, drawableId: Int) {
        try {
            val recentEntries: MutableList<RecentEntry>? =
                Util.JACKSON_MAPPER.readValue(
                    preferences!!.getString(PROP_RECENT_ENTRIES, "[]"),
                    object : TypeReference<MutableList<RecentEntry>>() {
                    })

            if (recentEntries!!.isNotEmpty() &&
                recentEntries[0].message == message
            ) return  //do not store duplicates in history


            val recentSize = preferences!!.getInt(PROP_RECENT_SIZE, DEF_RECENT_SIZE)

            //add latest recent values
            val newEntry = RecentEntry(
                message,
                drawableId,
                System.currentTimeMillis() + RECENT_TTL
            )

            recentEntries.add(0, newEntry)

            //crop to maximal size
            while (recentEntries.size > recentSize) {
                recentEntries.removeAt(recentEntries.size - 1)
            }

            preferences!!.edit() {
                putString(
                    PROP_RECENT_ENTRIES,
                    Util.JACKSON_MAPPER.writeValueAsString(recentEntries)
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun loadRecentButtons() {
        recentButtons.forEach(Consumer { b: ImageButton? -> binding!!.linearRecent.removeView(b) })
        recentButtons.clear()

        try {
            val recentEntries: List<RecentEntry> = Util.JACKSON_MAPPER.readValue(
                preferences!!.getString(PROP_RECENT_ENTRIES, "[]"),
                object : TypeReference<List<RecentEntry>>() {
                })

            for (i in 0..<preferences!!.getInt(PROP_RECENT_SIZE, DEF_RECENT_SIZE)) {
                if (recentEntries.size == i) break //list too short


                val recentEntry = recentEntries[i]

                if (System.currentTimeMillis() > recentEntry.ttl) break //this and all remaining entries have expired

                //create new button
                val b = ImageButton(requireContext())
                val dpFactor = requireContext().resources.displayMetrics.density
                val layoutParams = LinearLayout.LayoutParams(
                    (100 * dpFactor).toInt(),
                    (50 * dpFactor).toInt(),
                    1f
                )
                layoutParams.marginStart = (8 * dpFactor).toInt()
                b.layoutParams = layoutParams
                b.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        recentEntry.drawableId,
                        requireContext().theme
                    )
                )
                b.setOnClickListener { v: View? ->
                    onMessage(
                        recentEntry.message,
                        false
                    )
                }
                b.setOnLongClickListener { v: View? -> true }

                binding!!.linearRecent.addView(b)
                recentButtons.add(b)
            }

            if (recentButtons.isEmpty()) {
                binding!!.scrollRecent.visibility = View.GONE
                binding!!.txtRecent.visibility = View.GONE
            } else {
                binding!!.scrollRecent.visibility = View.VISIBLE
                binding!!.txtRecent.visibility = View.VISIBLE
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun runPinProtected(
        onSuccess: Runnable,
        onCancel: Runnable?,
        evaluateNextPinRequestTimestamp: Boolean
    ) {
        if (preferences!!.getBoolean(PinSetupFragment.PROP_PIN_ENABLED, false) &&
            (System.currentTimeMillis() > nextPinRequestTimestamp || !evaluateNextPinRequestTimestamp)
        ) {
            PinEntryFragment(
                {
                    if (evaluateNextPinRequestTimestamp) {
                        //calculate next pin request time
                        val interval_ms = TimeUnit.MINUTES.toMillis(
                            preferences!!.getLong(PinSetupFragment.PROP_REQUEST_INTERVAL_MINUTES, 0)
                        )
                        nextPinRequestTimestamp =
                            if (interval_ms == 0L) Long.MAX_VALUE else System.currentTimeMillis() + interval_ms
                    }
                    onSuccess.run()
                },
                onCancel,
                {
                    requireActivity().mainExecutor.execute {
                        //both presets and recent data have been deleted
                        loadRecentButtons()
                        loadPresets()
                    }
                }).show(requireActivity().supportFragmentManager, null)
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
            .filter { i: Int -> !receivedChunks[i] }
            .mapToObj { i: Int -> (i + 1).toString() }.collect(Collectors.joining(", "))
        requireContext().mainExecutor.execute {
            if (binding != null) { //prevent post mortem calls
                binding!!.txtRemainingCodes.text = s
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as MainActivity).destroyWiFiListener()
        requireActivity().removeMenuProvider(menuProvider)
        if (cameraProvider != null) cameraProvider!!.unbindAll()
        binding = null
    }

    private inner class QrMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_qr, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            menu.findItem(R.id.menuItemVersion)
                .setTitle(String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.FLAVOR))
            menu.findItem(R.id.menuItemClearWiFiComm)
                .setVisible((requireActivity() as MainActivity).isWiFiCommSet)
            if (BuildConfig.FLAVOR == Util.FLAVOR_FOSS) {
                menu.findItem(R.id.menuItemZxing)
                    .setVisible(false) //FOSS version has only ZXing engine
            } else {
                menu.findItem(R.id.menuItemZxing).setChecked(this@QRFragment.isZxingEnabled)
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemQrPrivateKeys) {
                NavHostFragment.findNavController(this@QRFragment)
                    .navigate(R.id.action_QRFragment_to_keyManagementFragment)
            } else if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.qr_scanner_md_url, requireContext())
            } else if (menuItem.itemId == R.id.menuItemPwdGenerator) {
                NavHostFragment.findNavController(this@QRFragment)
                    .navigate(R.id.action_QRFragment_to_passwordGeneratorFragment)
            } else if (menuItem.itemId == R.id.menuItemHomePage) {
                openUrl(R.string.readme_url, requireContext())
            } else if (menuItem.itemId == R.id.menuItemPaste) {
                //based on pre-launch test
                //Exception java.lang.NullPointerException: Attempt to invoke virtual method 'android.content.ClipData$Item android.content.ClipData.getItemAt(int)'
                // on a null object reference
                val clipData = clipboardManager!!.primaryClip
                if (clipData != null) {
                    val item = clipboardManager!!.primaryClip!!.getItemAt(0)
                    val text = item.text.toString()
                    onMessage(text, true)
                }
            } else if (menuItem.itemId == R.id.menuItemFeedbackEmail) {
                val crashReportData = CrashReportData(null)

                val sendEmail =
                    Consumer<Boolean> { b: Boolean? ->
                        try {
                            val intentSendTo = Intent(Intent.ACTION_SENDTO)
                            intentSendTo.setData(Uri.parse("mailto:"))
                            intentSendTo.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret feedback")
                            intentSendTo.putExtra(
                                Intent.EXTRA_EMAIL,
                                arrayOf(getString(R.string.contact_email))
                            )
                            intentSendTo.putExtra(
                                Intent.EXTRA_TEXT,
                                getString(R.string.feedback_prompt) + "\n\n" + crashReportData.toString(
                                    b!!
                                )
                            )
                            startActivity(intentSendTo)
                        } catch (ex: ActivityNotFoundException) {
                            requireContext().mainExecutor.execute {
                                Toast.makeText(
                                    context,
                                    "Could not send email",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Include logcat into feedback?")
                    .setPositiveButton(
                        "Yes"
                    ) { dialogInterface: DialogInterface?, i: Int -> sendEmail.accept(true) }
                    .setNegativeButton("No") { dialogInterface: DialogInterface, i: Int ->
                        sendEmail.accept(false)
                        dialogInterface.dismiss()
                    }

                requireActivity().mainExecutor.execute { builder.create().show() }
            } else if (menuItem.itemId == R.id.menuItemFeedbackDiscord) {
                openUrl(R.string.discord_url, requireContext())
            } else if (menuItem.itemId == R.id.menuItemEncryptText) {
                NavHostFragment.findNavController(this@QRFragment)
                    .navigate(R.id.action_QRFragment_to_encryptTextFragment)
            } else if (menuItem.itemId == R.id.menuItemTotp) {
                NavHostFragment.findNavController(this@QRFragment)
                    .navigate(R.id.action_QRFragment_to_totpManualEntryFragment)
            } else if (menuItem.itemId == R.id.menuItemPinSetup) {
                runPinProtected(
                    {
                        NavHostFragment.findNavController(this@QRFragment)
                            .navigate(R.id.action_QRFragment_to_pinSetupFragment)
                    },
                    null, false
                )
            } else if (menuItem.itemId == R.id.menuItemLogcat) {
                val sendIntent = Intent()
                sendIntent.setAction(Intent.ACTION_SEND)
                sendIntent.putExtra(Intent.EXTRA_TEXT, CrashReportData(null).toString(true))

                sendIntent.putExtra(Intent.EXTRA_TITLE, "Diagnose Data")
                sendIntent.setType("text/plain")

                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            } else if (menuItem.itemId == R.id.menuItemPanic) {
                if (preferences!!.getBoolean(PinSetupFragment.PROP_PIN_ENABLED, false)) {
                    nextPinRequestTimestamp = 0
                    Thread {
                        OmsFileProvider.purgeTmp(requireContext())
                        if (preferences!!.contains(PROP_RECENT_ENTRIES)) preferences!!.edit(commit = true) {
                            remove(PROP_RECENT_ENTRIES)
                        }
                        requireContext().mainExecutor.execute { this@QRFragment.loadRecentButtons() }
                    }.start()
                    requireContext().mainExecutor.execute {
                        Toast.makeText(
                            context,
                            R.string.locked,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    //PIN not enabled, go to PIN setup instead
                    requireContext().mainExecutor.execute {
                        Toast.makeText(
                            context,
                            R.string.enable_pin_first,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    NavHostFragment.findNavController(this@QRFragment)
                        .navigate(R.id.action_QRFragment_to_pinSetupFragment)
                }
            } else if (menuItem.itemId == R.id.menuItemCryptoAdrGen) {
                NavHostFragment.findNavController(this@QRFragment)
                    .navigate(R.id.action_QRFragment_to_cryptoCurrencyAddressGenerator)
            } else if (menuItem.itemId == R.id.menuItemScreenshot) {
                menuItem.setChecked(!menuItem.isChecked)
                if (menuItem.isChecked) {
                    requireActivity().getWindow                    ().clearFlags                    (WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    requireActivity().getWindow                    ().addFlags                    (WindowManager.LayoutParams.FLAG_SECURE)
                }

                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        context,
                        String.format(
                            "Screenshots %s",
                            if (menuItem.isChecked) "enabled" else "disabled"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (menuItem.itemId == R.id.menuItemClearWiFiComm) {
                (requireActivity() as MainActivity).setWiFiComm(null)
                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        context,
                        "WiFi Pairing cleared",
                        Toast.LENGTH_LONG
                    ).show()
                }
                updateWiFiPairingIndicator.run()
            } else if (menuItem.itemId == R.id.menuItemZxing) {
                preferences!!.edit(commit = true) {
                    putBoolean(
                        PROP_USE_ZXING,
                        !this@QRFragment.isZxingEnabled
                    )
                }
                requireActivity().invalidateOptionsMenu()
            } else {
                return false
            }

            return true
        }
    }

    fun showBiometricPromptForDecryption(
        fingerprint: ByteArray,
        rsaTransformation: String?,
        authenticationCallback: BiometricPrompt.AuthenticationCallback
    ) {
        val cryptographyManager = CryptographyManager()
        val aliases: List<String>
        try {
            aliases = cryptographyManager.getByFingerprint(fingerprint)

            if (aliases.isEmpty()) throw NoSuchElementException(
                String.format(
                    requireContext().getString(
                        R.string.no_key_found
                    ), byteArrayToHex(fingerprint)
                )
            )

            check(aliases.size <= 1) { requireContext().getString(R.string.multiple_keys_found) }

            val biometricPrompt = BiometricPrompt(requireActivity(), authenticationCallback)
            val alias = aliases[0]

            val promptInfo = PromptInfo.Builder()
                .setTitle(requireContext().getString(R.string.prompt_info_title))
                .setSubtitle(
                    String.format(
                        requireContext().getString(R.string.prompt_info_subtitle),
                        alias
                    )
                )
                .setDescription(requireContext().getString(R.string.prompt_info_description))
                .setNegativeButtonText(requireContext().getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build()

            val cipher = CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation
            )

            requireContext().mainExecutor.execute {
                biometricPrompt.authenticate(
                    promptInfo,
                    BiometricPrompt.CryptoObject(cipher)
                )
            }
        } catch (ex: Exception) {
            messageReceived.set(false)
            ex.printStackTrace()
            requireContext().mainExecutor.execute {
                Toast.makeText(
                    context,
                    Objects.requireNonNullElse(
                        ex.message,
                        ex.javaClass.name
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getAuthenticationCallback(
        rsaAesEnvelope: RsaAesEnvelope,
        cipherText: ByteArray,
        optOriginalMessage: Optional<String>
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                messageReceived.set(false)
                val activity = requireActivity() as MainActivity
                Thread { activity.sendReplyViaSocket(byteArrayOf(), true) }.start()
                nextPinRequestTimestamp = 0
                Thread { OmsFileProvider.purgeTmp(requireContext()) }.start()
                Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errorCode))
                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        requireContext(),
                        "$errString ($errorCode)", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject!!.cipher

                try {
                    checkNotNull(cipher)
                    val aesSecretKeyData = cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                    val aesSecretKey = SecretKeySpec(aesSecretKeyData, "AES")

                    val payload = process(
                        Cipher.DECRYPT_MODE, cipherText,
                        aesSecretKey,
                        IvParameterSpec(rsaAesEnvelope.iv),
                        rsaAesEnvelope.aesTransformation
                    )

                    //payload starts with its own application identifier.
                    afterDecrypt(rsaAesEnvelope, payload, optOriginalMessage)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    messageReceived.set(false)
                    Toast.makeText(
                        requireActivity(),
                        if (ex.message == null) String.format(
                            requireContext().getString(R.string.authentication_failed_s),
                            ex.javaClass.name
                        ) else ex.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                messageReceived.set(false)
                nextPinRequestTimestamp = 0
                Log.d(
                    TAG,
                    "User biometrics rejected"
                )
                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        requireContext(),
                        requireContext().getString(R.string.auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun afterDecrypt(
        rsaAesEnvelope: RsaAesEnvelope,
        payload: ByteArray,
        optOriginalMessage: Optional<String>
    ) {
        ByteArrayInputStream(payload).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                val bundle = Bundle()
                bundle.putByteArray(ARG_MESSAGE, payload)
                val navController = NavHostFragment.findNavController(this@QRFragment)

                when (rsaAesEnvelope.applicationId) {
                    MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                        //(1) - application identifier Payload
                        val applicationId = dataInputStream.readUnsignedShort()

                        Log.d(TAG, "payload AI $applicationId")

                        bundle.putInt(ARG_APPLICATION_ID, applicationId)

                        when (applicationId) {
                            MessageComposer.APPLICATION_BITCOIN_ADDRESS, MessageComposer.APPLICATION_ENCRYPTED_MESSAGE, MessageComposer.APPLICATION_TOTP_URI -> {
                                //(2) message
                                bundle.putByteArray(ARG_MESSAGE, dataInputStream.readByteArray())
                                Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                                navController.navigate(
                                    R.id.action_QRFragment_to_MessageFragment,
                                    bundle
                                )
                            }

                            MessageComposer.APPLICATION_WIFI_PAIRING -> {
                                //(2)...(n) structured data to be read from the remaining bytes
                                val bArr = ByteArray(dataInputStream.available())
                                dataInputStream.read(bArr)
                                bundle.putByteArray(ARG_MESSAGE, bArr)

                                Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                                navController.navigate(
                                    R.id.action_QRFragment_to_MessageFragment,
                                    bundle
                                )
                            }

                            else -> throw IllegalArgumentException(
                                "No processor defined for application ID " +
                                        Integer.toHexString(rsaAesEnvelope.applicationId)
                            )
                        }
                    }

                    MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED, MessageComposer.APPLICATION_TOTP_URI_DEPRECATED -> {
                        //support for legacy formats
                        bundle.putInt(ARG_APPLICATION_ID, rsaAesEnvelope.applicationId)
                        Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                        navController.navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
                    }

                    else -> throw IllegalArgumentException(
                        "No processor defined for application ID " +
                                Integer.toHexString(rsaAesEnvelope.applicationId)
                    )
                }

                if (!optOriginalMessage.isPresent) return  //do not update history


                val optDrawableId = getDrawableIdForApplicationId(bundle.getInt(ARG_APPLICATION_ID))

                if (!optDrawableId.isPresent) return
                setRecent(optOriginalMessage.get(), optDrawableId.get())
            }
        }
    }

    companion object {
        val TAG: String = QRFragment::class.java.simpleName
        private const val PROP_USE_ZXING = "use_zxing"
        private const val PROP_RECENT_SIZE = "recent_size"
        const val PROP_RECENT_ENTRIES: String = "recent_entries"
        const val PROP_PRESETS: String = "presets"
        private const val DEF_RECENT_SIZE = 3
        private val RECENT_TTL = TimeUnit.HOURS.toMillis(12)
        const val ARG_URI: String = "URI"
        const val ARG_MESSAGE: String = "MESSAGE"
        const val ARG_TEXT: String = "TEXT"
        const val ARG_APPLICATION_ID: String = "AI"
    }
}