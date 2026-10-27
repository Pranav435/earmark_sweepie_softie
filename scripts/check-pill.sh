#!/usr/bin/env bash
# Earmark on-device check: a message that comes in while the phone is in use, then a feed app,
# must end in a pill that is actually drawn on screen. Run with the phone unlocked:
#   ./scripts/check-pill.sh            (one device connected)
#   ANDROID_SERIAL=XYZ ./scripts/check-pill.sh
# It posts a test message from the adb shell ("Reply to Test" lands on today's list),
# opens Instagram or YouTube, waits for the pill, and checks the pill's window was drawn.
set -uo pipefail

PKG="com.sweepiesoftie.earmark"
FEEDS=("com.instagram.android" "com.google.android.youtube")
WAIT_SECONDS=150

fail() { printf '\033[31mFAIL\033[0m %s\n' "$1"; cleanup; exit 1; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
cleanup() {
  # Force-stopping the shell app removes its test notification, which closes the test loop.
  adb shell am force-stop com.android.shell >/dev/null 2>&1
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
}
log() { adb logcat -d -s Earmark:D 2>/dev/null; }

command -v adb >/dev/null || fail "adb isn't on PATH."
[[ "$(adb get-state 2>/dev/null)" == "device" ]] || fail "No single device: connect one, or set ANDROID_SERIAL."
adb shell pm path "$PKG" >/dev/null 2>&1 || fail "Earmark isn't installed."
adb shell settings get secure enabled_notification_listeners | grep -q "$PKG" || fail "Notification access is off."
adb shell appops get "$PKG" GET_USAGE_STATS | grep -q allow || fail "Usage access is off."
adb shell appops get "$PKG" SYSTEM_ALERT_WINDOW | grep -q allow || fail "Display over other apps is off."
adb shell dumpsys window | grep -q "isKeyguardShowing=false" || fail "Unlock the phone first."
FEED=""
for f in "${FEEDS[@]}"; do adb shell pm path "$f" >/dev/null 2>&1 && { FEED="$f"; break; }; done
[[ -n "$FEED" ]] || fail "Neither Instagram nor YouTube is installed."
ok "Earmark $(adb shell dumpsys package "$PKG" | grep -m1 versionName | tr -d ' \r'), permissions on, feed app $FEED"

cleanup
sleep 1
adb logcat -c
adb shell "cmd notification post -S messaging --conversation 'Earmark check' --message 'Test:free at 5?' earmark_check 'free at 5?'" >/dev/null
sleep 2
log | grep -q "came in during use and is now the guess" \
  || fail "The message didn't become the guess. Log: $(log | grep -E 'earmark_check|com.android.shell|came in' | tail -3)"
ok "The message became the guess"

adb shell monkey -p "$FEED" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
TIMER=$(log | grep -o "pill after [0-9]* s ([^)]*)" | tail -1)
[[ -n "$TIMER" ]] || fail "Earmark didn't see $FEED come to the front. Log: $(log | grep 'in front' | tail -3)"
ok "Earmark saw $FEED: $TIMER"

for ((i = 0; i < WAIT_SECONDS; i++)); do
  log | grep -q "pill at" && break
  HELD=$(log | grep -o "held: .*" | tail -1)
  sleep 1
done
log | grep -q "pill at" || fail "No pill after ${WAIT_SECONDS} s. ${HELD:-}"
ok "The engine showed the pill: $(log | grep -o 'pill at .*' | tail -1)"

sleep 1
HEIGHT=$(adb shell dumpsys window windows | awk '/u0 Earmark\}:/{f=1} f&&/Requested w=/{sub("h=","",$3); print $3; exit}')
[[ -n "$HEIGHT" ]] || fail "The pill's window isn't attached."
(( HEIGHT > 100 )) || fail "The pill's window is ${HEIGHT} px tall: attached but not drawn (ADR 0022)."
ok "The pill is drawn on screen (${HEIGHT} px tall)"

cleanup
printf '\033[32mPASS\033[0m a message during use came back as a pill over %s\n' "$FEED"
