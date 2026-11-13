#!/usr/bin/env python3
"""Install the APK and optionally enable explicitly requested TV permissions."""
import argparse
import os
import pathlib
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--device", required=True, help="ADB serial, e.g. 192.168.1.104:5555")
parser.add_argument("--port", default="5038", help="ADB server port")
parser.add_argument("--enable-navigation", action="store_true")
parser.add_argument("--enable-playback", action="store_true")
parser.add_argument("--disable-navigation", action="store_true")
parser.add_argument("--disable-playback", action="store_true")
parser.add_argument("--no-launch", action="store_true", help="Update without interrupting the foreground app")
parser.add_argument("--gemini-key-file", help="File holding a Google AI Studio API key; stored encrypted on the TV")
parser.add_argument("--gemini-key-env", action="store_true", help="Read the key from the GEMINI_API_KEY environment variable")
parser.add_argument("--gemini-model", help="Override the default Gemini model (e.g. gemini-3.8-flash)")
parser.add_argument("--clear-gemini-key", action="store_true")
parser.add_argument("--skip-install", action="store_true", help="Only change settings/key, do not reinstall the APK")
args = parser.parse_args()
adb = ["adb", "-P", args.port, "-s", args.device]

def run(*values):
    return subprocess.run(adb + list(values), check=True, text=True, capture_output=True).stdout.strip()

apk = pathlib.Path(__file__).resolve().parent / "dist" / "tv-assistant.apk"
if not args.skip_install:
    print(run("install", "-r", str(apk)))
navigation = "dev.mert.tvassistant/dev.mert.tvassistant.NavigationService"
playback = "dev.mert.tvassistant/dev.mert.tvassistant.PlaybackService"
if args.enable_navigation or args.disable_navigation:
    old = run("shell", "settings", "get", "secure", "enabled_accessibility_services")
    services = [s for s in old.split(":") if s and s != "null" and s != navigation]
    # Rebind our service after an upgrade without disabling other accessibility services.
    run("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services) or "null")
    if args.enable_navigation:
        services.append(navigation)
    run("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services) or "null")
    if services:
        run("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    print("Navigation enabled." if args.enable_navigation else "Navigation disabled.")
if args.enable_playback or args.disable_playback:
    run("shell", "cmd", "notification", "allow_listener" if args.enable_playback else "disallow_listener", playback, "0")
    print("Playback access enabled." if args.enable_playback else "Playback access disabled.")
receiver = "dev.mert.tvassistant/.KeyReceiver"
key = None
if args.gemini_key_file:
    key = pathlib.Path(args.gemini_key_file).expanduser().read_text().strip()
elif args.gemini_key_env:
    key = os.environ.get("GEMINI_API_KEY", "").strip()
if key is not None:
    if not re.fullmatch(r"[A-Za-z0-9_\-]{20,200}", key):
        raise SystemExit("Gemini key is missing or malformed; nothing was sent.")
    model = ""
    if args.gemini_model:
        if not re.fullmatch(r"[a-z0-9.\-]{3,80}", args.gemini_model):
            raise SystemExit("Invalid Gemini model name")
        model = f" --es model {args.gemini_model}"
    # Sent through the shell's stdin so the key never appears in this computer's process list.
    script = f"am broadcast -f 32 -n {receiver} -a dev.mert.tvassistant.SET_GEMINI_KEY --es key {key}{model}\nexit\n"
    out = subprocess.run(adb + ["shell"], input=script, check=True, text=True, capture_output=True).stdout
    print("Gemini key stored." if "Gemini key stored" in out else "Gemini key NOT confirmed: " + out.replace(key, "[key]").strip())
if args.clear_gemini_key:
    print(run("shell", "am", "broadcast", "-f", "32", "-n", receiver, "-a", "dev.mert.tvassistant.CLEAR_GEMINI_KEY"))
if not args.no_launch:
    print(run("shell", "am", "start", "-W", "-n", "dev.mert.tvassistant/.MainActivity"))
