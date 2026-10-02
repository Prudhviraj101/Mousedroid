package com.example.mousedroid

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var ipAddressEditText: EditText
    private lateinit var btnClearIp: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var scanIcon: ImageView
    private lateinit var scanButtonText: TextView
    private lateinit var scanButton: View
    private lateinit var connectButton: View
    private lateinit var recentSection: View
    private lateinit var recentChipsGroup: ChipGroup
    private lateinit var btnClearRecent: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var ivStatusDot: ImageView
    private lateinit var btnQuickInfo: ImageButton

    private val PREFS_NAME = "MousedroidPrefs"
    private val KEY_RECENT_IPS = "RecentIPs"
    private val KEY_LAST_IP = "LastIP"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        HapticHelper.init(this)

        initViews()
        setupListeners()
        loadNetworkInfo()
        loadRecentConnections()
        startStatusPulse()
    }

    private fun initViews() {
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        btnClearIp = findViewById(R.id.btnClearIp)
        progressBar = findViewById(R.id.progressBar)
        scanIcon = findViewById(R.id.scanIcon)
        scanButtonText = findViewById(R.id.scanButtonText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)
        recentSection = findViewById(R.id.recentSection)
        recentChipsGroup = findViewById(R.id.recentChipsGroup)
        btnClearRecent = findViewById(R.id.btnClearRecent)
        networkStatusText = findViewById(R.id.networkStatusText)
        ivStatusDot = findViewById(R.id.ivStatusDot)
        btnQuickInfo = findViewById(R.id.btnQuickInfo)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIp = prefs.getString(KEY_LAST_IP, "")
        if (!lastIp.isNullOrEmpty()) {
            ipAddressEditText.setText(lastIp)
            btnClearIp.visibility = View.VISIBLE
        }
    }

    private fun startStatusPulse() {
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            ivStatusDot,
            PropertyValuesHolder.ofFloat("scaleX", 0.75f, 1.25f),
            PropertyValuesHolder.ofFloat("scaleY", 0.75f, 1.25f),
            PropertyValuesHolder.ofFloat("alpha", 0.5f, 1.0f)
        ).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun setupListeners() {
        btnClearIp.setOnClickListener {
            HapticHelper.click(it)
            ipAddressEditText.text?.clear()
        }

        ipAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearIp.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        connectButton.setOnClickListener {
            HapticHelper.click(it)
            val ip = ipAddressEditText.text.toString().trim()
            if (ip.isNotEmpty()) {
                saveRecentIp(ip)
                navigateToTrackpad(ip)
            } else {
                Toast.makeText(this, "Please enter or scan a Host IP", Toast.LENGTH_SHORT).show()
            }
        }

        scanButton.setOnClickListener {
            HapticHelper.click(it)
            findServer()
        }

        btnQuickInfo.setOnClickListener {
            HapticHelper.click(it)
            showQuickInfoDialog()
        }

        btnClearRecent.setOnClickListener {
            HapticHelper.click(it)
            clearRecentConnections()
        }
    }

    private fun loadNetworkInfo() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipAddress = wifiInfo?.ipAddress
            if (ipAddress != null && ipAddress != 0) {
                @Suppress("DEPRECATION")
                val formattedIp = Formatter.formatIpAddress(ipAddress)
                networkStatusText.text = "IP: $formattedIp"
            } else {
                networkStatusText.text = "Wi-Fi Ready"
            }
        } catch (e: Exception) {
            networkStatusText.text = "Wi-Fi Ready"
        }
    }

    private fun loadRecentConnections() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECENT_IPS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            recentChipsGroup.removeAllViews()

            if (jsonArray.length() > 0) {
                recentSection.visibility = View.VISIBLE
                for (i in 0 until jsonArray.length()) {
                    val ip = jsonArray.getString(i)
                    val chip = Chip(this).apply {
                        text = ip
                        isClickable = true
                        isCheckable = false
                        setChipBackgroundColorResource(R.color.glass_surface)
                        setTextColor(getColor(R.color.text_primary))
                        setChipStrokeColorResource(R.color.glass_stroke)
                        chipStrokeWidth = 2.5f
                        textSize = 13f

                        setOnClickListener {
                            HapticHelper.click(it)
                            ipAddressEditText.setText(ip)
                            navigateToTrackpad(ip)
                        }
                    }
                    recentChipsGroup.addView(chip)
                }
            } else {
                recentSection.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearRecentConnections() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT_IPS).apply()
        recentChipsGroup.removeAllViews()
        recentSection.visibility = View.GONE
    }

    private fun saveRecentIp(ip: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECENT_IPS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            list.add(ip)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getString(i)
                if (item != ip && list.size < 6) {
                    list.add(item)
                }
            }
            val newJsonArray = JSONArray(list)
            prefs.edit()
                .putString(KEY_RECENT_IPS, newJsonArray.toString())
                .putString(KEY_LAST_IP, ip)
                .apply()

            loadRecentConnections()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun navigateToTrackpad(ip: String) {
        val intent = Intent(this, TrackpadActivity::class.java).apply {
            putExtra("SERVER_IP", ip)
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun findServer() {
        progressBar.visibility = View.VISIBLE
        scanIcon.visibility = View.GONE
        scanButtonText.text = getString(R.string.scanning)
        scanButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            var serverIp: String? = null
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val sendData = "DISCOVER_SERVER_REQUEST".toByteArray()
                    val sendPacket = DatagramPacket(
                        sendData,
                        sendData.size,
                        InetAddress.getByName("255.255.255.255"),
                        9998
                    )
                    socket.send(sendPacket)

                    val receiveData = ByteArray(1024)
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.soTimeout = 2500
                    socket.receive(receivePacket)

                    val response = String(receivePacket.data, 0, receivePacket.length)
                    if (response.startsWith("DISCOVER_SERVER_RESPONSE")) {
                        serverIp = receivePacket.address.hostAddress
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                scanIcon.visibility = View.VISIBLE
                scanButtonText.text = getString(R.string.scan_network)
                scanButton.isEnabled = true

                val resolvedIp = serverIp
                if (resolvedIp != null) {
                    HapticHelper.heavyClick()
                    ipAddressEditText.setText(resolvedIp)
                    saveRecentIp(resolvedIp)
                    Toast.makeText(this@MainActivity, "✨ Host found at $resolvedIp!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No host found on Wi-Fi. Ensure PC server is running.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showQuickInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About Mousedroid")
            .setMessage("Mousedroid turns your Android device into a precision wireless trackpad, media controller, hotkey deck & remote keyboard.\n\n" +
                    "• Connect phone & PC to same Wi-Fi network\n" +
                    "• Run 'python mousedroid_server.py' on PC\n" +
                    "• 1-finger tap = Left Click\n" +
                    "• 2-finger tap = Right Click\n" +
                    "• 2-finger drag = Smooth Scroll\n" +
                    "• 3-finger swipe up = Task View\n" +
                    "• 3-finger swipe down = Show Desktop\n" +
                    "• Pinch = Zoom In / Out")
            .setPositiveButton("Got it", null)
            .show()
    }
}