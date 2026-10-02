package com.example.mousedroid

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale

class TrackpadActivity : AppCompatActivity(), ModernTrackpadView.TrackpadListener {

    private var udpSocket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val SERVER_PORT = 9999

    private lateinit var tvServerBadge: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var tabTrackpad: TextView
    private lateinit var tabMedia: TextView
    private lateinit var tabHotkeys: TextView
    private lateinit var tabNumpad: TextView

    private lateinit var modernTrackpadView: ModernTrackpadView
    private lateinit var panelMedia: View
    private lateinit var panelHotkeys: View
    private lateinit var panelNumpad: View

    private lateinit var leftClickButton: View
    private lateinit var middleClickButton: View
    private lateinit var rightClickButton: View
    private lateinit var keyboardButton: Button
    private lateinit var btnQuickVoice: ImageButton
    private lateinit var btnQuickPaste: ImageButton
    private lateinit var btnQuickEsc: ImageButton

    // Voice recognition launcher
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                HapticHelper.heavyClick()
                sendCommand("TYPE_STRING:$spokenText")
                Toast.makeText(this, "Dictated: $spokenText", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_trackpad)

        HapticHelper.init(this)

        val serverIp = intent.getStringExtra("SERVER_IP") ?: "127.0.0.1"
        setupUdpConnection(serverIp)

        initViews(serverIp)
        setupModeSwitcher()
        setupMediaControls()
        setupHotkeyControls()
        setupNumpadControls()
        setupClickControls()
    }

    private fun initViews(serverIp: String) {
        tvServerBadge = findViewById(R.id.tvServerBadge)
        btnBack = findViewById(R.id.btnBack)
        btnSettings = findViewById(R.id.btnSettings)

        tabTrackpad = findViewById(R.id.tabTrackpad)
        tabMedia = findViewById(R.id.tabMedia)
        tabHotkeys = findViewById(R.id.tabHotkeys)
        tabNumpad = findViewById(R.id.tabNumpad)

        modernTrackpadView = findViewById(R.id.modernTrackpadView)
        panelMedia = findViewById(R.id.panelMedia)
        panelHotkeys = findViewById(R.id.panelHotkeys)
        panelNumpad = findViewById(R.id.panelNumpad)

        leftClickButton = findViewById(R.id.leftClickButton)
        middleClickButton = findViewById(R.id.middleClickButton)
        rightClickButton = findViewById(R.id.rightClickButton)
        keyboardButton = findViewById(R.id.keyboardButton)
        btnQuickVoice = findViewById(R.id.btnQuickVoice)
        btnQuickPaste = findViewById(R.id.btnQuickPaste)
        btnQuickEsc = findViewById(R.id.btnQuickEsc)

        tvServerBadge.text = serverIp
        modernTrackpadView.listener = this

        btnBack.setOnClickListener {
            HapticHelper.click(it)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnSettings.setOnClickListener {
            HapticHelper.click(it)
            showSettingsBottomSheet()
        }

        keyboardButton.setOnClickListener {
            HapticHelper.click(it)
            showKeyboardBottomSheet()
        }

        btnQuickVoice.setOnClickListener {
            HapticHelper.click(it)
            startVoiceInput()
        }

        btnQuickPaste.setOnClickListener {
            HapticHelper.click(it)
            pasteClipboardToPc()
        }

        btnQuickEsc.setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:ESC")
        }
    }

    private fun setupModeSwitcher() {
        tabTrackpad.setOnClickListener {
            HapticHelper.click(it)
            selectMode(0)
        }
        tabMedia.setOnClickListener {
            HapticHelper.click(it)
            selectMode(1)
        }
        tabHotkeys.setOnClickListener {
            HapticHelper.click(it)
            selectMode(2)
        }
        tabNumpad.setOnClickListener {
            HapticHelper.click(it)
            selectMode(3)
        }
    }

    private fun selectMode(modeIndex: Int) {
        val activeBg = R.drawable.bg_pill_chip_active
        val activeTextColor = getColor(R.color.pill_active_text)
        val inactiveTextColor = getColor(R.color.text_secondary)

        tabTrackpad.setBackgroundResource(if (modeIndex == 0) activeBg else R.color.transparent)
        tabTrackpad.setTextColor(if (modeIndex == 0) activeTextColor else inactiveTextColor)

        tabMedia.setBackgroundResource(if (modeIndex == 1) activeBg else R.color.transparent)
        tabMedia.setTextColor(if (modeIndex == 1) activeTextColor else inactiveTextColor)

        tabHotkeys.setBackgroundResource(if (modeIndex == 2) activeBg else R.color.transparent)
        tabHotkeys.setTextColor(if (modeIndex == 2) activeTextColor else inactiveTextColor)

        tabNumpad.setBackgroundResource(if (modeIndex == 3) activeBg else R.color.transparent)
        tabNumpad.setTextColor(if (modeIndex == 3) activeTextColor else inactiveTextColor)

        modernTrackpadView.visibility = if (modeIndex == 0) View.VISIBLE else View.GONE
        panelMedia.visibility = if (modeIndex == 1) View.VISIBLE else View.GONE
        panelHotkeys.visibility = if (modeIndex == 2) View.VISIBLE else View.GONE
        panelNumpad.visibility = if (modeIndex == 3) View.VISIBLE else View.GONE
    }

    private fun setupMediaControls() {
        findViewById<View>(R.id.btnMediaPlayPause).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("MEDIA_PLAY_PAUSE")
        }
        findViewById<View>(R.id.btnMediaPrev).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("MEDIA_PREV")
        }
        findViewById<View>(R.id.btnMediaNext).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("MEDIA_NEXT")
        }
        findViewById<View>(R.id.btnVolumeUp).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("VOLUME_UP")
        }
        findViewById<View>(R.id.btnVolumeDown).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("VOLUME_DOWN")
        }
        findViewById<View>(R.id.btnVolumeMute).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("VOLUME_MUTE")
        }
        findViewById<View>(R.id.btnSlidePrev).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:PAGE_UP")
        }
        findViewById<View>(R.id.btnSlideNext).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:PAGE_DOWN")
        }
        findViewById<View>(R.id.btnSpace).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:SPACE")
        }
        findViewById<View>(R.id.btnBlankScreen).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:B")
        }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:F5")
        }
    }

    private fun setupHotkeyControls() {
        // Editing Row 1
        findViewById<View>(R.id.btnCopy).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+C")
        }
        findViewById<View>(R.id.btnPaste).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+V")
        }
        findViewById<View>(R.id.btnUndo).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+Z")
        }
        // Editing Row 2
        findViewById<View>(R.id.btnSelectAll).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+A")
        }
        findViewById<View>(R.id.btnCut).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+X")
        }
        findViewById<View>(R.id.btnRedo).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+Y")
        }
        // Browser & Tabs Row 3
        findViewById<View>(R.id.btnNewTab).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+T")
        }
        findViewById<View>(R.id.btnCloseTab).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+W")
        }
        findViewById<View>(R.id.btnReopenTab).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:CTRL+SHIFT+T")
        }
        // Tools Row 4
        findViewById<View>(R.id.btnScreenshot).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:WIN+SHIFT+S")
        }
        findViewById<View>(R.id.btnExplorer).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("HOTKEY:WIN+E")
        }
        findViewById<View>(R.id.btnCloseApp).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:ALT+F4")
        }
        // System Row 5 & 6
        findViewById<View>(R.id.btnAltTab).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:ALT+TAB")
        }
        findViewById<View>(R.id.btnWinKey).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:WIN")
        }
        findViewById<View>(R.id.btnShowDesktop).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:WIN+D")
        }
        findViewById<View>(R.id.btnTaskMgr).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:CTRL+SHIFT+ESC")
        }
        findViewById<View>(R.id.btnLockPc).setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("HOTKEY:WIN+L")
        }
        findViewById<View>(R.id.btnEsc).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:ESC")
        }
    }

    private fun setupNumpadControls() {
        val numKeyMap = mapOf(
            R.id.btnNum0 to "NUMPAD0",
            R.id.btnNum1 to "NUMPAD1",
            R.id.btnNum2 to "NUMPAD2",
            R.id.btnNum3 to "NUMPAD3",
            R.id.btnNum4 to "NUMPAD4",
            R.id.btnNum5 to "NUMPAD5",
            R.id.btnNum6 to "NUMPAD6",
            R.id.btnNum7 to "NUMPAD7",
            R.id.btnNum8 to "NUMPAD8",
            R.id.btnNum9 to "NUMPAD9",
            R.id.btnNumDot to "DECIMAL",
            R.id.btnNumDiv to "DIVIDE",
            R.id.btnNumMul to "MULTIPLY",
            R.id.btnNumSub to "SUBTRACT",
            R.id.btnNumAdd to "ADD",
            R.id.btnNumEnter to "ENTER"
        )

        for ((btnId, keyName) in numKeyMap) {
            findViewById<View>(btnId)?.setOnClickListener {
                HapticHelper.tick(it)
                sendCommand("KEY_PRESS:$keyName")
            }
        }

        // Directional D-Pad buttons
        findViewById<View>(R.id.btnDpadUp).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:UP")
        }
        findViewById<View>(R.id.btnDpadDown).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:DOWN")
        }
        findViewById<View>(R.id.btnDpadLeft).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:LEFT")
        }
        findViewById<View>(R.id.btnDpadRight).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:RIGHT")
        }
        findViewById<View>(R.id.btnDpadCenter).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:ENTER")
        }
    }

    private fun setupClickControls() {
        leftClickButton.setOnClickListener {
            HapticHelper.click(it)
            sendCommand("LEFT_CLICK")
        }
        middleClickButton.setOnClickListener {
            HapticHelper.click(it)
            sendCommand("MIDDLE_CLICK")
        }
        rightClickButton.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendCommand("RIGHT_CLICK")
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak text to send to PC...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteClipboardToPc() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                HapticHelper.heavyClick()
                sendCommand("TYPE_STRING:$text")
                Toast.makeText(this, "Pasted ${text.length} chars to PC!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboardBottomSheet() {
        val dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_keyboard_sheet, null)
        dialog.setContentView(view)

        val etSendText: EditText = view.findViewById(R.id.etSendText)
        val btnSendText: Button = view.findViewById(R.id.btnSendText)
        val btnSheetVoice: ImageButton = view.findViewById(R.id.btnSheetVoice)
        val btnSheetPaste: ImageButton = view.findViewById(R.id.btnSheetPaste)
        val btnClose: ImageButton = view.findViewById(R.id.btnCloseKeyboard)

        btnClose.setOnClickListener { dialog.dismiss() }

        // --- Parallel / Live Typing: stream each new character as it's typed ---
        var lastText = ""
        etSendText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val current = s?.toString() ?: ""
                when {
                    // New characters appended — send just the new chars
                    current.length > lastText.length && current.startsWith(lastText) -> {
                        val newChars = current.substring(lastText.length)
                        sendCommand("TYPE_STRING:$newChars")
                    }
                    // Backspace — send one BACKSPACE per deleted char
                    current.length < lastText.length -> {
                        val deleted = lastText.length - current.length
                        repeat(deleted) { sendCommand("KEY_PRESS:BACKSPACE") }
                    }
                    // Paste or autocomplete replacement — send full string
                    current != lastText -> sendCommand("TYPE_STRING:$current")
                }
                lastText = current
            }
        })

        // Enter key sends Enter and clears the field
        etSendText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendCommand("KEY_PRESS:ENTER")
                etSendText.text.clear()
                lastText = ""
                true
            } else false
        }

        // Send button flushes remaining text then clears
        btnSendText.setOnClickListener {
            val text = etSendText.text.toString()
            if (text.isNotEmpty()) {
                HapticHelper.click()
                etSendText.text.clear()
                lastText = ""
            }
            sendCommand("KEY_PRESS:ENTER")
        }

        btnSheetVoice.setOnClickListener {
            dialog.dismiss()
            startVoiceInput()
        }

        btnSheetPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrEmpty()) {
                etSendText.setText(text)
                etSendText.setSelection(text.length)
            }
        }

        // Function Keys (F1-F12)
        val fKeyIds = listOf(
            R.id.btnF1 to "F1", R.id.btnF2 to "F2", R.id.btnF3 to "F3", R.id.btnF4 to "F4",
            R.id.btnF5 to "F5", R.id.btnF6 to "F6", R.id.btnF7 to "F7", R.id.btnF8 to "F8",
            R.id.btnF9 to "F9", R.id.btnF10 to "F10", R.id.btnF11 to "F11", R.id.btnF12 to "F12"
        )
        for ((btnId, fKey) in fKeyIds) {
            view.findViewById<View>(btnId)?.setOnClickListener {
                HapticHelper.tick(it)
                sendCommand("KEY_PRESS:$fKey")
            }
        }

        // Navigation keys
        view.findViewById<View>(R.id.btnKeyEsc).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:ESC")
        }
        view.findViewById<View>(R.id.btnKeyTab).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:TAB")
        }
        view.findViewById<View>(R.id.btnKeyBackspace).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:BACKSPACE")
        }
        view.findViewById<View>(R.id.btnKeyDel).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:DELETE")
        }
        view.findViewById<View>(R.id.btnKeyEnter).setOnClickListener {
            HapticHelper.click(it)
            sendCommand("KEY_PRESS:ENTER")
        }
        view.findViewById<View>(R.id.btnArrowLeft).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:LEFT")
        }
        view.findViewById<View>(R.id.btnArrowRight).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:RIGHT")
        }
        view.findViewById<View>(R.id.btnArrowUp).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:UP")
        }
        view.findViewById<View>(R.id.btnArrowDown).setOnClickListener {
            HapticHelper.tick(it)
            sendCommand("KEY_PRESS:DOWN")
        }

        dialog.show()
    }

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_settings_sheet, null)
        dialog.setContentView(view)

        val sliderSensitivity: Slider = view.findViewById(R.id.sliderSensitivity)
        val tvSensitivityValue: TextView = view.findViewById(R.id.tvSensitivityValue)
        val sliderScrollSpeed: Slider = view.findViewById(R.id.sliderScrollSpeed)
        val tvScrollValue: TextView = view.findViewById(R.id.tvScrollValue)
        val switchTapToClick: MaterialSwitch = view.findViewById(R.id.switchTapToClick)
        val switchSmoothing: MaterialSwitch = view.findViewById(R.id.switchSmoothing)
        val switchInvertScroll: MaterialSwitch = view.findViewById(R.id.switchInvertScroll)
        val toggleGroupHaptics: MaterialButtonToggleGroup = view.findViewById(R.id.toggleGroupHaptics)

        sliderSensitivity.value = modernTrackpadView.sensitivity
        tvSensitivityValue.text = "${String.format("%.1f", modernTrackpadView.sensitivity)}x"

        sliderScrollSpeed.value = modernTrackpadView.scrollMultiplier
        tvScrollValue.text = "${String.format("%.1f", modernTrackpadView.scrollMultiplier)}x"

        switchTapToClick.isChecked = modernTrackpadView.tapToClick
        switchSmoothing.isChecked = modernTrackpadView.smoothingEnabled
        switchInvertScroll.isChecked = modernTrackpadView.invertScroll

        when (HapticHelper.currentLevel) {
            HapticHelper.HapticLevel.OFF -> toggleGroupHaptics.check(R.id.btnHapticOff)
            HapticHelper.HapticLevel.SOFT -> toggleGroupHaptics.check(R.id.btnHapticSoft)
            HapticHelper.HapticLevel.CRISP -> toggleGroupHaptics.check(R.id.btnHapticCrisp)
            HapticHelper.HapticLevel.HEAVY -> toggleGroupHaptics.check(R.id.btnHapticHeavy)
        }

        sliderSensitivity.addOnChangeListener { _, value, _ ->
            modernTrackpadView.sensitivity = value
            tvSensitivityValue.text = "${String.format("%.1f", value)}x"
            HapticHelper.tick()
        }

        sliderScrollSpeed.addOnChangeListener { _, value, _ ->
            modernTrackpadView.scrollMultiplier = value
            tvScrollValue.text = "${String.format("%.1f", value)}x"
            HapticHelper.tick()
        }

        switchTapToClick.setOnCheckedChangeListener { _, isChecked ->
            modernTrackpadView.tapToClick = isChecked
            HapticHelper.click()
        }

        switchSmoothing.setOnCheckedChangeListener { _, isChecked ->
            modernTrackpadView.smoothingEnabled = isChecked
            HapticHelper.click()
        }

        switchInvertScroll.setOnCheckedChangeListener { _, isChecked ->
            modernTrackpadView.invertScroll = isChecked
            HapticHelper.click()
        }

        toggleGroupHaptics.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnHapticOff -> {
                        HapticHelper.currentLevel = HapticHelper.HapticLevel.OFF
                        HapticHelper.enabled = false
                    }
                    R.id.btnHapticSoft -> {
                        HapticHelper.currentLevel = HapticHelper.HapticLevel.SOFT
                        HapticHelper.enabled = true
                        HapticHelper.tick()
                    }
                    R.id.btnHapticCrisp -> {
                        HapticHelper.currentLevel = HapticHelper.HapticLevel.CRISP
                        HapticHelper.enabled = true
                        HapticHelper.click()
                    }
                    R.id.btnHapticHeavy -> {
                        HapticHelper.currentLevel = HapticHelper.HapticLevel.HEAVY
                        HapticHelper.enabled = true
                        HapticHelper.heavyClick()
                    }
                }
            }
        }

        dialog.show()
    }

    // --- UDP Networking ---
    private fun setupUdpConnection(ip: String) {
        scope.launch {
            try {
                serverAddress = InetAddress.getByName(ip)
                udpSocket = DatagramSocket()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendCommand(command: String) {
        scope.launch {
            try {
                val addr = serverAddress ?: return@launch
                val sock = udpSocket ?: return@launch
                val sendData = command.toByteArray()
                val sendPacket = DatagramPacket(sendData, sendData.size, addr, SERVER_PORT)
                sock.send(sendPacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- ModernTrackpadView Callbacks ---
    override fun onMouseMove(dx: Float, dy: Float) {
        sendCommand("MOUSE_MOVE:${dx.toInt()},${dy.toInt()}")
    }

    override fun onMouseLeftClick() {
        sendCommand("LEFT_CLICK")
    }

    override fun onMouseRightClick() {
        sendCommand("RIGHT_CLICK")
    }

    override fun onScroll(deltaY: Int, deltaX: Int) {
        sendCommand("SCROLL:$deltaY")
    }

    override fun onLeftDown() {
        sendCommand("LEFT_DOWN")
    }

    override fun onLeftUp() {
        sendCommand("LEFT_UP")
    }

    override fun onSpecialGesture(action: String) {
        sendCommand(action)
    }

    override fun onZoom(scaleFactor: Float) {
        if (scaleFactor > 1.0f) {
            sendCommand("ZOOM_IN")
        } else {
            sendCommand("ZOOM_OUT")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        udpSocket?.close()
    }
}