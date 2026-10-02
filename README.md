<p align="center">
  <img src="https://img.shields.io/badge/Android-Kotlin-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Python-PC_Server-3776AB?style=for-the-badge&logo=python&logoColor=white" />
  <img src="https://img.shields.io/badge/UDP-LAN_Communication-6E56CF?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Automation-pyautogui-FFB000?style=for-the-badge" />
  <img src="https://img.shields.io/badge/CI-GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white" />
</p>

<h1 align="center">🖱️ Mousedroid</h1>

<p align="center">
  <strong>Turn your Android phone into a wireless trackpad, keyboard, media remote, and presentation controller for your PC.</strong>
</p>

<p align="center">
  One Android app + one lightweight Python server, communicating directly over your local Wi-Fi network.
</p>

---

## ✨ Why Mousedroid?

Mousedroid turns a phone into a practical PC control surface without requiring a USB cable or dedicated hardware.

It combines:

- 📱 **Android/Kotlin client** for touch, gestures, keyboard and controls
- 🐍 **Python server** for translating commands into PC input
- 📡 **UDP LAN communication** for low-latency local control
- 🖱️ **Trackpad gestures** for everyday mouse interaction
- 🎞️ **Presentation controls** for slides and media
- ⌨️ **Keyboard + hotkeys** for common desktop actions
- 🎙️ **Voice-to-text** through Android speech input
- 📋 **Clipboard transfer** between phone and PC

## 🚀 Features

### Trackpad
- Single tap → left click
- Two-finger tap → right click
- Double-tap + hold → drag
- Two-finger scrolling
- Pinch-to-zoom
- Three-finger desktop and virtual-desktop gestures

### Media & Presentation
- Play / pause
- Previous / next
- Volume controls
- Next / previous slide
- Fullscreen and blank-screen shortcuts

### Keyboard & Automation
- Text input
- F1–F12 and navigation keys
- Number pad and D-pad
- Copy / paste / cut
- Undo / redo
- App switching
- Screenshot and system shortcuts

## 🏗️ Architecture

```
┌─────────────────────────┐
│       Android App       │
│        Kotlin           │
│                         │
│ Touch / Keyboard / UI   │
└────────────┬────────────┘
             │
             │ UDP over local Wi-Fi
             │
       ┌─────┴─────┐
       │           │
   Port 9998    Port 9999
   Discovery    Commands
       │           │
       └─────┬─────┘
             ↓
┌─────────────────────────┐
│       Python Server     │
│                         │
│ UDP listener → parser   │
│            → pyautogui  │
└────────────┬────────────┘
             ↓
      PC mouse / keyboard
      media / shortcuts
```

### Connection flow

```text
Android App
    │
    ├── Broadcast DISCOVER_SERVER_REQUEST → UDP 9998
    │
    ├── Receive server response
    │
    └── Send commands → UDP 9999
                         │
                         ↓
                    Python Server
                         │
                         ↓
                      pyautogui
                         │
                         ↓
                         PC
```

## 📡 Protocol

Commands are sent as UTF-8 UDP datagrams.

| Command | Example | Purpose |
|---|---|---|
| `MOUSE_MOVE:dx,dy` | `MOUSE_MOVE:3,-2` | Relative mouse movement |
| `LEFT_CLICK` | — | Left click |
| `RIGHT_CLICK` | — | Right click |
| `SCROLL:n` | `SCROLL:-3` | Scrolling |
| `ZOOM_IN` | — | Zoom in |
| `MEDIA_PLAY_PAUSE` | — | Media playback |
| `KEY_PRESS:key` | `KEY_PRESS:F5` | Key press |
| `HOTKEY:combo` | `HOTKEY:ctrl+c` | Keyboard combination |
| `TYPE_STRING:text` | `TYPE_STRING:hello` | Text input |

| Port | Protocol | Purpose |
|---|---|---|
| `9998` | UDP | Discovery |
| `9999` | UDP | Command traffic |

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Android client | Kotlin |
| UI/input | Android Views + custom touch handling |
| PC server | Python |
| PC automation | PyAutoGUI |
| Clipboard | Pyperclip |
| Transport | UDP / local Wi-Fi |
| CI | GitHub Actions |

## 🚀 Quick Start

### Requirements

- Android phone
- Windows/Linux/macOS PC with Python 3.8+
- Both devices connected to the same Wi-Fi network

### 1. Start the server

```bash
cd server
pip install -r requirements.txt
python mousedroid_server.py
```

### 2. Connect the Android app

Open Mousedroid and select **Scan Network**.

If discovery does not work, use **Enter IP** and provide the local IP shown by the server.

## 📁 Project Structure

```
Mousedroid/
├── app/
│   └── src/main/java/com/example/mousedroid/
│       ├── MainActivity.kt
│       ├── TrackpadActivity.kt
│       ├── ModernTrackpadView.kt
│       └── HapticHelper.kt
│
├── server/
│   ├── mousedroid_server.py
│   └── requirements.txt
│
└── .github/workflows/
    └── android.yml
```

## 🔐 Security & Network Model

Mousedroid is designed for trusted local networks.

The current protocol uses unauthenticated and unencrypted UDP command traffic. **Do not expose the server directly to the public internet or untrusted networks.**

For future hardening, the project could add:

- [ ] Authentication / pairing
- [ ] Encrypted transport
- [ ] Replay protection
- [ ] Command validation and rate limiting

## 🧪 CI

GitHub Actions builds the Android project on pushes and pull requests targeting `main`.

## 🗺️ Roadmap

- [ ] Secure device pairing
- [ ] Encrypted command channel
- [ ] Better cross-platform PC support
- [ ] Connection status and latency indicators
- [ ] Customizable shortcut profiles
- [ ] Improved gesture configuration

## 📄 License

See the repository for the current licensing information.
