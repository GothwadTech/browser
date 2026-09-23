package com.gothwad.browser.utils

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gothwad.browser.R
import com.gothwad.browser.databinding.DialogVoiceSearchBinding
import java.util.Locale

class VoiceSearchHelper(
    private val activity: Activity,
    private val requestCode: Int,
    private val permissionRequestCode: Int
) {

    private var activeDialog: Dialog? = null
    private var dialogBinding: DialogVoiceSearchBinding? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var isListening: Boolean = false
    private var lastRecognizedText: String = ""
    private var activeLanguageModel: String = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
    private var activeCallback: Callback? = null
    private var activeComponent: ComponentName? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "VoiceSearchHelper"

        fun getSpeechErrorDescription(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error (ERROR_AUDIO, code $errorCode)"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error (ERROR_CLIENT, code $errorCode)"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions (ERROR_INSUFFICIENT_PERMISSIONS, code $errorCode)"
                SpeechRecognizer.ERROR_NETWORK -> "Network error (ERROR_NETWORK, code $errorCode)"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout (ERROR_NETWORK_TIMEOUT, code $errorCode)"
                SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match found (ERROR_NO_MATCH, code $errorCode)"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy (ERROR_RECOGNIZER_BUSY, code $errorCode)"
                SpeechRecognizer.ERROR_SERVER -> "Server error (ERROR_SERVER, code $errorCode)"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input / timeout (ERROR_SPEECH_TIMEOUT, code $errorCode)"
                10 -> "Too many requests (ERROR_TOO_MANY_REQUESTS, code $errorCode)"
                11 -> "Server disconnected (ERROR_SERVER_DISCONNECTED, code $errorCode)"
                12 -> "Language not supported (ERROR_LANGUAGE_NOT_SUPPORTED, code $errorCode)"
                13 -> "Language unavailable (ERROR_LANGUAGE_UNAVAILABLE, code $errorCode)"
                14 -> "Cannot check support (ERROR_CANNOT_CHECK_SUPPORT, code $errorCode)"
                else -> "Unknown error code: $errorCode"
            }
        }
    }

    interface Callback {
        fun onResult(text: String?)
        fun onPartialResult(text: String) {}
        fun onError(errorMessage: String?) {}
    }

    fun initiateVoiceSearch(
        callback: Callback,
        languageModel: String = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
    ) {
        this.activeCallback = callback
        this.activeLanguageModel = languageModel
        this.lastRecognizedText = ""

        if (isActivityDestroyed()) return

        // 1. Audio permission check
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    permissionRequestCode
                )
            } else {
                Toast.makeText(activity, R.string.voice_permission_required, Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2. Start Multi-Tier Recognition Flow:
        // Tier 1: System default SpeechRecognizer if available
        // Tier 2: Discovered RecognitionService component (e.g. Jio VoiceServiceWrapper, OEM services)
        // Tier 3: Honest "not supported" failure state if no recognition service exists
        startSpeechRecognitionFlow()
    }

    private fun startSpeechRecognitionFlow(preferDiscoveredComponent: Boolean = false) {
        if (isActivityDestroyed()) return

        val defaultAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(activity)
        } catch (e: Exception) {
            Log.w(TAG, "SpeechRecognizer.isRecognitionAvailable check failed: ${e.message}")
            false
        }

        Log.d(TAG, "startSpeechRecognitionFlow: preferDiscovered=$preferDiscoveredComponent, defaultAvailable=$defaultAvailable")

        // Tier 1: System default SpeechRecognizer
        if (!preferDiscoveredComponent && defaultAvailable) {
            val started = startRecognizer(componentName = null)
            if (started) {
                Log.d(TAG, "Tier 1: System default SpeechRecognizer started successfully.")
                return
            }
            Log.w(TAG, "Tier 1: Default SpeechRecognizer failed to start. Falling back to Tier 2 discovery.")
        }

        // Tier 2: Discover any available RecognitionService via PackageManager
        val discoveredComponent = discoverRecognitionService(activity)
        if (discoveredComponent != null) {
            Log.i(TAG, "Tier 2: Discovered RecognitionService component: ${discoveredComponent.flattenToShortString()}")
            val started = startRecognizer(componentName = discoveredComponent)
            if (started) {
                Log.d(TAG, "Tier 2: SpeechRecognizer started with component ${discoveredComponent.flattenToShortString()}")
                return
            }
            Log.w(TAG, "Tier 2: SpeechRecognizer failed with component ${discoveredComponent.flattenToShortString()}")
        } else {
            Log.w(TAG, "Tier 2: No RecognitionService component discovered via PackageManager.")
        }

        // Tier 3: Honest failure state — Voice search is not supported on this device
        Log.e(TAG, "Tier 3: Voice search is not supported on this device. Neither default nor discovered RecognitionService is available.")
        showNotSupportedUi()
    }

    /**
     * Queries PackageManager for any installed services declaring the android.speech.RecognitionService intent-filter.
     * Works generically across all Android devices, detecting carrier/OEM-provided speech services
     * (e.g. Jio's com.android.app.jio.voiceassist/.VoiceServiceWrapper) even when not configured as the system default.
     */
    private fun discoverRecognitionService(context: Context): ComponentName? {
        try {
            val pm = context.packageManager ?: return null
            val intent = Intent(RecognitionService.SERVICE_INTERFACE)
            val resolveInfos = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)

            if (resolveInfos.isNullOrEmpty()) {
                Log.d(TAG, "discoverRecognitionService: No services found for ${RecognitionService.SERVICE_INTERFACE}")
                return null
            }

            Log.d(TAG, "discoverRecognitionService: Found ${resolveInfos.size} service(s) implementing RecognitionService:")
            for (info in resolveInfos) {
                val pkg = info.serviceInfo?.packageName
                val name = info.serviceInfo?.name
                Log.d(TAG, "  Candidate: package=$pkg, name=$name")
            }

            // Prioritize manufacturer/carrier/device-specific services if multiple exist, else use the first available
            val selected = resolveInfos.firstOrNull { info ->
                val pkg = info.serviceInfo?.packageName?.lowercase(Locale.ROOT) ?: ""
                val name = info.serviceInfo?.name?.lowercase(Locale.ROOT) ?: ""
                pkg.contains("jio") || pkg.contains("voiceassist") || name.contains("voiceservice") ||
                        pkg.contains("oem") || pkg.contains("tv") || pkg.contains("voice")
            } ?: resolveInfos.first()

            val serviceInfo = selected.serviceInfo ?: return null
            return ComponentName(serviceInfo.packageName, serviceInfo.name)
        } catch (e: Exception) {
            Log.e(TAG, "discoverRecognitionService error: ${e.message}", e)
            return null
        }
    }

    private fun startRecognizer(componentName: ComponentName?): Boolean {
        cleanupRecognizer()
        showVoiceDialog()

        return try {
            val recognizer = if (componentName != null) {
                Log.d(TAG, "Creating SpeechRecognizer explicitly targeting component: ${componentName.flattenToShortString()}")
                SpeechRecognizer.createSpeechRecognizer(activity, componentName)
            } else {
                Log.d(TAG, "Creating default SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(activity)
            }

            if (recognizer == null) {
                Log.e(TAG, "SpeechRecognizer.createSpeechRecognizer returned null for component: ${componentName?.flattenToShortString() ?: "default"}")
                cleanupRecognizer()
                return false
            }

            activeComponent = componentName
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(createRecognitionListener(componentName))

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, activeLanguageModel)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            recognizer.startListening(intent)
            isListening = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SpeechRecognizer (${componentName?.flattenToShortString() ?: "default"}): ${e.message}", e)
            cleanupRecognizer()
            false
        }
    }

    private fun createRecognitionListener(component: ComponentName?): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                mainHandler.post {
                    dialogBinding?.tvVoiceStatus?.text = activity.getString(R.string.voice_search_listening)
                }
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                mainHandler.post {
                    dialogBinding?.let { binding ->
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        val scale = 1.0f + (0.2f * normalized)
                        binding.flMicButton.scaleX = scale
                        binding.flMicButton.scaleY = scale
                    }
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                stopPulseAnimation()
                mainHandler.post {
                    dialogBinding?.apply {
                        tvVoiceStatus.text = activity.getString(R.string.voice_search_processing)
                        tvVoiceStatus.setTextColor(Color.parseColor("#E3B341"))
                        tvVoiceHint.text = ""
                    }
                }
            }

            override fun onError(error: Int) {
                isListening = false
                stopPulseAnimation()
                val errorDesc = getSpeechErrorDescription(error)
                Log.e(TAG, "SpeechRecognizer onError: code=$error, description=$errorDesc, component=${component?.flattenToShortString() ?: "default"}")

                mainHandler.post {
                    handleSpeechError(error, component)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                stopPulseAnimation()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull()?.trim() ?: lastRecognizedText.trim()
                Log.d(TAG, "SpeechRecognizer onResults: '$recognized'")

                mainHandler.post {
                    if (recognized.isNotBlank()) {
                        lastRecognizedText = recognized
                        dialogBinding?.apply {
                            tvVoiceRecognizedText.text = recognized
                            tvVoiceStatus.text = activity.getString(R.string.search)
                            tvVoiceStatus.setTextColor(Color.parseColor("#3FB950"))
                            btnVoiceSearch.isEnabled = true
                            btnVoiceSearch.alpha = 1.0f
                        }

                        mainHandler.postDelayed({
                            dismissDialog()
                            activeCallback?.onResult(recognized)
                        }, 350)
                    } else {
                        showDidNotCatchUi()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()

                if (!partial.isNullOrEmpty()) {
                    lastRecognizedText = partial
                    mainHandler.post {
                        dialogBinding?.apply {
                            tvVoiceRecognizedText.text = partial
                            btnVoiceSearch.isEnabled = true
                            btnVoiceSearch.alpha = 1.0f
                        }
                        activeCallback?.onPartialResult(partial)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleSpeechError(error: Int, failedComponent: ComponentName?) {
        // If Tier 1 (default recognizer) failed with a client/binding error on a device without standard Google services,
        // seamlessly attempt Tier 2 discovered RecognitionService before surfacing any error
        if (failedComponent == null && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)) {
            Log.w(TAG, "Tier 1 default recognizer failed with error $error (${getSpeechErrorDescription(error)}). Attempting Tier 2 discovered RecognitionService fallback.")
            val discoveredComponent = discoverRecognitionService(activity)
            if (discoveredComponent != null) {
                val started = startRecognizer(discoveredComponent)
                if (started) {
                    Log.i(TAG, "Successfully failed over to Tier 2 discovered component: ${discoveredComponent.flattenToShortString()}")
                    return
                }
            }
        }

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (lastRecognizedText.trim().isNotEmpty()) {
                    val finalSpeech = lastRecognizedText.trim()
                    dismissDialog()
                    activeCallback?.onResult(finalSpeech)
                } else {
                    showDidNotCatchUi()
                }
            }
            else -> {
                if (lastRecognizedText.trim().isNotEmpty()) {
                    val finalSpeech = lastRecognizedText.trim()
                    dismissDialog()
                    activeCallback?.onResult(finalSpeech)
                } else {
                    showDidNotCatchUi()
                }
            }
        }
    }

    private fun showVoiceDialog() {
        dismissDialog()
        val binding = DialogVoiceSearchBinding.inflate(LayoutInflater.from(activity))
        dialogBinding = binding

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                stopPulseAnimation()
                cleanupRecognizer()
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }

        activeDialog = dialog

        // Setup dialog view states
        binding.tvVoiceStatus.text = activity.getString(R.string.voice_search_listening)
        binding.tvVoiceStatus.setTextColor(Color.parseColor("#388BFD"))
        binding.tvVoiceHint.text = activity.getString(R.string.voice_search_speak_now)
        binding.tvVoiceRecognizedText.text = ""
        binding.btnVoiceSearch.isEnabled = false
        binding.btnVoiceSearch.alpha = 0.5f

        // Action listeners
        binding.ibVoiceClose.setOnClickListener {
            dismissDialog()
        }

        binding.btnVoiceCancel.setOnClickListener {
            dismissDialog()
        }

        binding.btnVoiceRetry.setOnClickListener {
            restartListening()
        }

        binding.flMicButton.setOnClickListener {
            if (!isListening) {
                restartListening()
            }
        }

        binding.btnVoiceSearch.setOnClickListener {
            val text = lastRecognizedText.trim()
            if (text.isNotEmpty()) {
                dismissDialog()
                activeCallback?.onResult(text)
            }
        }

        startPulseAnimation(binding.vPulseRing)

        try {
            dialog.show()
            binding.btnVoiceSearch.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restartListening() {
        lastRecognizedText = ""
        dialogBinding?.apply {
            tvVoiceRecognizedText.text = ""
            tvVoiceStatus.text = activity.getString(R.string.voice_search_listening)
            tvVoiceStatus.setTextColor(Color.parseColor("#388BFD"))
            tvVoiceHint.text = activity.getString(R.string.voice_search_speak_now)
            btnVoiceSearch.isEnabled = false
            btnVoiceSearch.alpha = 0.5f
            startPulseAnimation(vPulseRing)
        }

        if (activeComponent != null) {
            val started = startRecognizer(activeComponent)
            if (!started) {
                startSpeechRecognitionFlow(preferDiscoveredComponent = true)
            }
        } else {
            startSpeechRecognitionFlow()
        }
    }

    private fun showDidNotCatchUi() {
        dialogBinding?.apply {
            flMicButton.scaleX = 1.0f
            flMicButton.scaleY = 1.0f
            tvVoiceStatus.text = activity.getString(R.string.voice_search_didnt_hear)
            tvVoiceStatus.setTextColor(Color.parseColor("#F85149"))
            tvVoiceHint.text = activity.getString(R.string.voice_search_speak_now)
            btnVoiceRetry.requestFocus()
        }
    }

    private fun showNotSupportedUi() {
        dismissDialog()
        val message = activity.getString(R.string.voice_search_not_supported)
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        activeCallback?.onError(message)
    }

    fun launchSystemVoiceSearch(
        languageModel: String = RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
    ) {
        if (isActivityDestroyed()) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, languageModel)
            putExtra(RecognizerIntent.EXTRA_PROMPT, activity.getString(R.string.speak))
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        val pm = activity.packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        if (activities.isNotEmpty() || intent.resolveActivity(pm) != null) {
            try {
                activity.startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch system voice search activity: ${e.message}", e)
                initiateVoiceSearch(activeCallback ?: object : Callback {
                    override fun onResult(text: String?) {}
                }, languageModel)
            }
        } else {
            initiateVoiceSearch(activeCallback ?: object : Callback {
                override fun onResult(text: String?) {}
            }, languageModel)
        }
    }

    private fun startPulseAnimation(view: View) {
        stopPulseAnimation()
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.3f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.3f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0.2f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        dialogBinding?.let { binding ->
            binding.vPulseRing.scaleX = 1.0f
            binding.vPulseRing.scaleY = 1.0f
            binding.vPulseRing.alpha = 0.5f
            binding.flMicButton.scaleX = 1.0f
            binding.flMicButton.scaleY = 1.0f
        }
    }

    fun processActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != this.requestCode) return false

        if (resultCode == Activity.RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = matches?.firstOrNull()?.trim()
                ?: data.getStringExtra(RecognizerIntent.EXTRA_RESULTS)?.trim()
                ?: data.dataString?.trim()

            if (!recognizedText.isNullOrBlank()) {
                activeCallback?.onResult(recognizedText)
            } else {
                activeCallback?.onResult(null)
            }
        } else {
            activeCallback?.onResult(null)
        }
        return true
    }

    fun processPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != permissionRequestCode) return false
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            activeCallback?.let { initiateVoiceSearch(it, activeLanguageModel) }
        } else {
            Toast.makeText(activity, R.string.voice_permission_required, Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun dismissDialog() {
        stopPulseAnimation()
        try {
            if (activeDialog?.isShowing == true) {
                activeDialog?.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeDialog = null
            dialogBinding = null
            isListening = false
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        dismissDialog()
        cleanupRecognizer()
        activeCallback = null
    }

    private fun isActivityDestroyed(): Boolean {
        return activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }
}
