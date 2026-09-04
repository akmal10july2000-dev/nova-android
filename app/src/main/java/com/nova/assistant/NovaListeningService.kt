package com.nova.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class NovaListeningService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var wakePhraseRequired = true

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            publishStatus("Listening for ${if (wakePhraseRequired) "“Hey Nova”" else "a command"}")
        }

        override fun onBeginningOfSpeech() {
            publishStatus("Hearing you…")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!listening) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                publishStatus("Microphone permission is required")
                stopListening()
                return
            }
            scheduleRecognition(if (error == SpeechRecognizer.ERROR_CLIENT) 1_500L else 650L)
        }

        override fun onResults(results: android.os.Bundle?) {
            if (!listening) return
            val spokenText = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()

            when (val command = CommandParser.parse(spokenText, wakePhraseRequired)) {
                ParsedCommand.NotForNova -> publishStatus("Waiting for “Hey Nova”…")
                ParsedCommand.Unrecognized -> publishStatus("Try “Hey Nova, open YouTube”")
                is ParsedCommand.OpenApp -> {
                    val result = AppResolver.launch(this@NovaListeningService, command.target)
                    publishStatus(result.message)
                }
            }
            scheduleRecognition(450L)
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            return START_NOT_STICKY
        }

        wakePhraseRequired = intent?.getBooleanExtra(EXTRA_WAKE_REQUIRED, true) ?: true
        listening = true
        startAsForeground()
        startRecognition()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecognition() {
        if (!listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            publishStatus("Speech recognition is not available on this device")
            stopListening()
            return
        }

        recognizer?.destroy()
        recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
        recognizer?.setRecognitionListener(recognitionListener)

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            recognizer?.startListening(speechIntent)
        } catch (_: RuntimeException) {
            scheduleRecognition(1_500L)
        }
    }

    private fun scheduleRecognition(delayMillis: Long) {
        if (!listening) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startRecognition() }, delayMillis)
    }

    private fun stopListening() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        publishStatus("Nova is paused")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishStatus(message: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, message)
            .putBoolean(KEY_LISTENING, listening)
            .apply()

        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, message)
                .putExtra(EXTRA_LISTENING, listening),
        )
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, NovaListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nova)
            .setContentTitle("Nova is listening")
            .setContentText("Say “Hey Nova, open YouTube”")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop listening",
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nova microphone",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while Nova is actively listening"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STATUS = "com.nova.assistant.STATUS"
        const val ACTION_STOP = "com.nova.assistant.STOP"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LISTENING = "listening"
        const val EXTRA_WAKE_REQUIRED = "wake_required"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "nova_microphone"
        const val PREFS_NAME = "nova_state"
        const val KEY_STATUS = "status"
        const val KEY_LISTENING = "listening"
    }
}