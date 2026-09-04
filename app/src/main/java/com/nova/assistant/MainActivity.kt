package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var wakeSwitch: Switch
    private var pendingStartAfterPermission = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra(NovaListeningService.EXTRA_STATUS)
                ?: getStatus()
            updateButtons(intent?.getBooleanExtra(NovaListeningService.EXTRA_LISTENING, false))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        refreshState()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(NovaListeningService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun buildContent(): View {
        val background = Color.rgb(10, 18, 38)
        val accent = Color.rgb(101, 184, 255)
        val secondary = Color.rgb(174, 190, 216)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(background)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(28))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val eyebrow = textView("NOVA / ANDROID ASSISTANT", 12f, accent, true)
        content.addView(eyebrow, marginParams(bottom = 18))

        val title = textView("Speak.\nNova opens it.", 38f, Color.WHITE, true).apply {
            letterSpacing = 0.01f
        }
        content.addView(title, marginParams(bottom = 14))

        val intro = textView(
            "A lightweight voice controller for the apps already installed on your phone.",
            16f,
            secondary,
            false,
        )
        content.addView(intro, marginParams(bottom = 30))

        statusText = textView(getStatus(), 16f, Color.WHITE, true).apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(Color.rgb(22, 37, 67))
        }
        content.addView(statusText, marginParams(bottom = 18))

        startButton = Button(this).apply {
            text = "Start listening"
            textSize = 16f
            isAllCaps = false
            setTextColor(background)
            setBackgroundColor(accent)
            setOnClickListener { requestPermissionsAndStart() }
        }
        content.addView(startButton, marginParams(bottom = 10))

        stopButton = Button(this).apply {
            text = "Stop listening"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(43, 61, 95))
            setOnClickListener { stopNova() }
        }
        content.addView(stopButton, marginParams(bottom = 28))

        wakeSwitch = Switch(this).apply {
            text = "Require “Hey Nova” wake phrase"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = getPreferences(MODE_PRIVATE).getBoolean(KEY_WAKE_PHRASE, true)
            setOnCheckedChangeListener { _, checked ->
                getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_WAKE_PHRASE, checked).apply()
            }
        }
        content.addView(wakeSwitch, marginParams(bottom = 28))

        val examplesTitle = textView("Try saying", 14f, accent, true)
        content.addView(examplesTitle, marginParams(bottom = 8))
        val examples = textView(
            "“Hey Nova, open YouTube”\n“Hey Nova, open Chrome”\n“Hey Nova, open Free Fire”\n“Hey Nova, open Camera”",
            16f,
            Color.WHITE,
            false,
        )
        content.addView(examples, marginParams(bottom = 28))

        val limitation = textView(
            "Android note\n" +
                "Nova starts listening from this screen and stays active through a foreground " +
                "microphone service with a persistent notification. Android does not allow a " +
                "normal app to silently restart an always-on microphone after the system kills " +
                "it, so the closest reliable behavior is user-started foreground listening.",
            14f,
            secondary,
            false,
        )
        content.addView(limitation, marginParams())

        return scroll
    }

    private fun requestPermissionsAndStart() {
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isNotEmpty()) {
            pendingStartAfterPermission = true
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            startNova()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS || !pendingStartAfterPermission) return
        pendingStartAfterPermission = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startNova()
        } else {
            statusText.text = "Microphone permission is required"
        }
    }

    private fun startNova() {
        val intent = Intent(this, NovaListeningService::class.java).apply {
            putExtra(
                NovaListeningService.EXTRA_WAKE_REQUIRED,
                wakeSwitch.isChecked,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "Starting Nova…"
        updateButtons(true)
    }

    private fun stopNova() {
        startService(
            Intent(this, NovaListeningService::class.java)
                .setAction(NovaListeningService.ACTION_STOP),
        )
        statusText.text = "Nova is paused"
        updateButtons(false)
    }

    private fun refreshState() {
        statusText.text = getStatus()
        val active = getSharedPreferences(NovaListeningService.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(NovaListeningService.KEY_LISTENING, false)
        updateButtons(active)
    }

    private fun getStatus(): String =
        getSharedPreferences(NovaListeningService.PREFS_NAME, MODE_PRIVATE)
            .getString(NovaListeningService.KEY_STATUS, "Nova is paused")
            .orEmpty()

    private fun updateButtons(active: Boolean?) {
        val isActive = active ?: false
        startButton.isEnabled = !isActive
        stopButton.isEnabled = isActive
        startButton.alpha = if (isActive) 0.5f else 1f
        stopButton.alpha = if (isActive) 1f else 0.5f
    }

    private fun textView(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean,
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setLineSpacing(0f, 1.18f)
    }

    private fun marginParams(
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMargins(0, 0, 0, dp(bottom))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 40
        private const val KEY_WAKE_PHRASE = "wake_phrase"
    }
}