#!/usr/bin/env python3
"""
Mousedroid PC Companion Server
High-performance UDP receiver and auto-discovery responder for Android Mousedroid.
"""

import socket
import threading
import sys
import os

# Ensure safe UTF-8 stdout encoding on Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

try:
    import pyautogui
    pyautogui.FAILSAFE = False
    pyautogui.PAUSE = 0
except ImportError:
    print("[!] Error: 'pyautogui' is required. Run: pip install pyautogui")
    sys.exit(1)

try:
    import pyperclip
except ImportError:
    pyperclip = None

DISCOVERY_PORT = 9998
COMMAND_PORT = 9999

KEY_MAPPINGS = {
    "ESC": "esc",
    "ENTER": "enter",
    "BACKSPACE": "backspace",
    "DELETE": "delete",
    "TAB": "tab",
    "SPACE": "space",
    "UP": "up",
    "DOWN": "down",
    "LEFT": "left",
    "RIGHT": "right",
    "PAGE_UP": "pageup",
    "PAGE_DOWN": "pagedown",
    "HOME": "home",
    "END": "end",
    "WIN": "win",
    "B": "b",
    "F1": "f1", "F2": "f2", "F3": "f3", "F4": "f4", "F5": "f5", "F6": "f6",
    "F7": "f7", "F8": "f8", "F9": "f9", "F10": "f10", "F11": "f11", "F12": "f12",
    "NUMPAD0": "0", "NUMPAD1": "1", "NUMPAD2": "2", "NUMPAD3": "3", "NUMPAD4": "4",
    "NUMPAD5": "5", "NUMPAD6": "6", "NUMPAD7": "7", "NUMPAD8": "8", "NUMPAD9": "9",
    "DECIMAL": ".", "DIVIDE": "/", "MULTIPLY": "*", "SUBTRACT": "-", "ADD": "+"
}

def get_local_ips():
    ips = []
    try:
        host_name = socket.gethostname()
        for ip in socket.gethostbyname_ex(host_name)[2]:
            if not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    return ips or ["127.0.0.1"]

def discovery_listener():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", DISCOVERY_PORT))
    print(f"[*] Discovery Beacon active on UDP port {DISCOVERY_PORT}")

    while True:
        try:
            data, addr = sock.recvfrom(1024)
            message = data.decode("utf-8", errors="ignore").strip()
            if message == "DISCOVER_SERVER_REQUEST":
                response = "DISCOVER_SERVER_RESPONSE".encode("utf-8")
                sock.sendto(response, addr)
                print(f"[+] Responded to auto-discovery from {addr[0]}")
        except Exception as e:
            pass

def handle_hotkey(hotkey_str):
    raw = hotkey_str.lower()
    if raw == "alt_tab" or raw == "alt+tab":
        pyautogui.hotkey("alt", "tab")
    elif raw == "win_tab":
        pyautogui.hotkey("win", "tab")
    elif raw == "ctrl+win+left":
        pyautogui.hotkey("ctrl", "win", "left")
    elif raw == "ctrl+win+right":
        pyautogui.hotkey("ctrl", "win", "right")
    elif raw == "show_desktop" or raw == "win+d":
        pyautogui.hotkey("win", "d")
    elif raw == "win+l":
        pyautogui.hotkey("win", "l")
    elif raw == "win+e":
        pyautogui.hotkey("win", "e")
    elif raw == "win+shift+s":
        pyautogui.hotkey("win", "shift", "s")
    elif raw == "ctrl+shift+esc":
        pyautogui.hotkey("ctrl", "shift", "esc")
    elif raw == "alt+f4":
        pyautogui.hotkey("alt", "f4")
    elif "+" in hotkey_str:
        keys = [k.strip().lower() for k in hotkey_str.split("+")]
        pyautogui.hotkey(*keys)

def handle_command(cmd):
    try:
        if cmd.startswith("MOUSE_MOVE:"):
            _, coords = cmd.split(":", 1)
            dx_s, dy_s = coords.split(",")
            dx, dy = int(dx_s), int(dy_s)
            pyautogui.moveRel(dx, dy)

        elif cmd == "LEFT_CLICK":
            pyautogui.click(button="left")
        elif cmd == "RIGHT_CLICK":
            pyautogui.click(button="right")
        elif cmd == "MIDDLE_CLICK":
            pyautogui.click(button="middle")
        elif cmd == "LEFT_DOWN":
            pyautogui.mouseDown(button="left")
        elif cmd == "LEFT_UP":
            pyautogui.mouseUp(button="left")

        elif cmd.startswith("SCROLL:"):
            _, amount_s = cmd.split(":", 1)
            amount = int(amount_s)
            pyautogui.scroll(amount * 40)

        elif cmd == "ZOOM_IN":
            pyautogui.keyDown("ctrl")
            pyautogui.scroll(120)
            pyautogui.keyUp("ctrl")
        elif cmd == "ZOOM_OUT":
            pyautogui.keyDown("ctrl")
            pyautogui.scroll(-120)
            pyautogui.keyUp("ctrl")

        elif cmd == "MEDIA_PLAY_PAUSE":
            pyautogui.press("playpause")
        elif cmd == "MEDIA_PREV":
            pyautogui.press("prevtrack")
        elif cmd == "MEDIA_NEXT":
            pyautogui.press("nexttrack")
        elif cmd == "VOLUME_UP":
            pyautogui.press("volumeup")
        elif cmd == "VOLUME_DOWN":
            pyautogui.press("volumedown")
        elif cmd == "VOLUME_MUTE":
            pyautogui.press("volumemute")

        elif cmd.startswith("KEY_PRESS:"):
            _, key = cmd.split(":", 1)
            target = KEY_MAPPINGS.get(key.upper(), key.lower())
            pyautogui.press(target)

        elif cmd.startswith("HOTKEY:"):
            _, combo = cmd.split(":", 1)
            handle_hotkey(combo)

        elif cmd.startswith("TYPE_STRING:"):
            _, text = cmd.split(":", 1)
            if pyperclip is not None and any(ord(c) > 127 for c in text):
                old_clip = pyperclip.paste()
                pyperclip.copy(text)
                pyautogui.hotkey("ctrl", "v")
                pyperclip.copy(old_clip)
            else:
                pyautogui.write(text, interval=0.005)

    except Exception as e:
        print(f"[!] Error executing '{cmd}': {e}")

def command_listener():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", COMMAND_PORT))
    print(f"[*] Command Receiver active on UDP port {COMMAND_PORT}")

    while True:
        try:
            data, addr = sock.recvfrom(2048)
            cmd = data.decode("utf-8", errors="ignore").strip()
            if cmd:
                handle_command(cmd)
        except Exception as e:
            pass

def main():
    print("=" * 60)
    print("        [+] MOUSE DROID PC COMPANION SERVER [+]")
    print("=" * 60)
    ips = get_local_ips()
    print("  Available Host IP(s) on your Wi-Fi:")
    for ip in ips:
        print(f"    -> {ip}")
    print("\n  1. Open Mousedroid on your Android device.")
    print("  2. Tap 'Scan Network' or enter one of the IPs above.")
    print("=" * 60 + "\n")

    t1 = threading.Thread(target=discovery_listener, daemon=True)
    t2 = threading.Thread(target=command_listener, daemon=True)
    t1.start()
    t2.start()

    try:
        t1.join()
        t2.join()
    except KeyboardInterrupt:
        print("\n[!] Server stopped by user.")

if __name__ == "__main__":
    main()
