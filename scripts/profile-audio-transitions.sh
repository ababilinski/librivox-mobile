#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.librivox.mobile}"
ACTIVITY="${ACTIVITY:-com.librivox.mobile/.MainActivity}"
OUT_DIR="${OUT_DIR:-build/perf-captures/transition-$(date +%Y%m%d-%H%M%S)}"
RECORD_SECONDS="${RECORD_SECONDS:-6}"
START_DELAY="${START_DELAY:-0.45}"
MINI_TAP_X="${MINI_TAP_X:-250}"
MINI_TAP_Y="${MINI_TAP_Y:-1930}"
CLOSE_TAP_X="${CLOSE_TAP_X:-80}"
CLOSE_TAP_Y="${CLOSE_TAP_Y:-240}"
DISCOVER_TAP_X="${DISCOVER_TAP_X:-678}"
DISCOVER_TAP_Y="${DISCOVER_TAP_Y:-2256}"
HOME_TAP_X="${HOME_TAP_X:-127}"
HOME_TAP_Y="${HOME_TAP_Y:-2256}"
SETTINGS_TAP_X="${SETTINGS_TAP_X:-953}"
SETTINGS_TAP_Y="${SETTINGS_TAP_Y:-2256}"
BOOK_TAP_X="${BOOK_TAP_X:-240}"
BOOK_TAP_Y="${BOOK_TAP_Y:-760}"
HOME_BOOK_TAP_X="${HOME_BOOK_TAP_X:-250}"
HOME_BOOK_TAP_Y="${HOME_BOOK_TAP_Y:-900}"
CONTINUE_BOOK_TAP_X="${CONTINUE_BOOK_TAP_X:-250}"
CONTINUE_BOOK_TAP_Y="${CONTINUE_BOOK_TAP_Y:-1850}"
LIBRARY_TAP_X="${LIBRARY_TAP_X:-403}"
LIBRARY_TAP_Y="${LIBRARY_TAP_Y:-2256}"
HOME_TAB_SETTLE_SECONDS="${HOME_TAB_SETTLE_SECONDS:-0.35}"
HOME_DETAIL_SETTLE_SECONDS="${HOME_DETAIL_SETTLE_SECONDS:-1.2}"
DETAIL_TO_LIBRARY_SETTLE_SECONDS="${DETAIL_TO_LIBRARY_SETTLE_SECONDS:-0.8}"
LIBRARY_HOME_SETTLE_SECONDS="${LIBRARY_HOME_SETTLE_SECONDS:-0.35}"
HOME_BACK_SETTLE_SECONDS="${HOME_BACK_SETTLE_SECONDS:-0.35}"
HOME_BACK_TAP_X="${HOME_BACK_TAP_X:-64}"
HOME_BACK_TAP_Y="${HOME_BACK_TAP_Y:-180}"
HOME_SCROLL_FROM_Y="${HOME_SCROLL_FROM_Y:-1700}"
HOME_SCROLL_TO_Y="${HOME_SCROLL_TO_Y:-620}"
HOME_SCROLL_X="${HOME_SCROLL_X:-540}"
HOME_SCROLL_ROUNDS="${HOME_SCROLL_ROUNDS:-3}"

mkdir -p "$OUT_DIR"

adb_cmd() {
  adb "$@"
}

capture_contact_sheet() {
  local video="$1"
  local image="$2"
  if command -v ffmpeg >/dev/null 2>&1; then
    ffmpeg -y -i "$video" -vf "fps=12,scale=360:-1,tile=4x5" -frames:v 1 "$image" >/dev/null 2>&1 || true
  fi
}

collect_logs() {
  local name="$1"
  adb_cmd logcat -d -v time |
    rg -i "Choreographer|Skipped [0-9]+ frames|FATAL EXCEPTION|AndroidRuntime|AudiobookPlayback|BookCoverDownloadWorker|WorkManager" \
      > "$OUT_DIR/$name-log.txt" || true
}

record_tap() {
  local name="$1"
  local x="$2"
  local y="$3"
  local remote="/sdcard/$name.mp4"
  adb_cmd shell rm -f "$remote"
  adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "$remote" > "$OUT_DIR/$name-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep "$START_DELAY"
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
  adb_cmd shell input tap "$x" "$y"
  wait "$recorder_pid" || true
  adb_cmd pull "$remote" "$OUT_DIR/$name.mp4" >/dev/null
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
  collect_logs "$name"
  capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
}

record_home_tab_from_settings() {
  local name="home-tab-from-settings"
  local remote="/sdcard/$name.mp4"
  adb_cmd shell am start -n "$ACTIVITY" >/dev/null
  adb_cmd shell input tap "$SETTINGS_TAP_X" "$SETTINGS_TAP_Y"
  sleep "$HOME_TAB_SETTLE_SECONDS"
  adb_cmd shell rm -f "$remote"
  adb_cmd logcat -c
  adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "$remote" > "$OUT_DIR/$name-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep "$START_DELAY"
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
  adb_cmd shell input tap "$HOME_TAP_X" "$HOME_TAP_Y"
  wait "$recorder_pid" || true
  adb_cmd pull "$remote" "$OUT_DIR/$name.mp4" >/dev/null
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
  collect_logs "$name"
  capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
}

record_home_book_open() {
  local name="home-book-open"
  local remote="/sdcard/$name.mp4"
  adb_cmd shell am start -n "$ACTIVITY" >/dev/null
  adb_cmd shell input tap "$HOME_TAP_X" "$HOME_TAP_Y"
  sleep "$HOME_TAB_SETTLE_SECONDS"
  adb_cmd shell rm -f "$remote"
  adb_cmd logcat -c
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
  adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "$remote" > "$OUT_DIR/$name-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep "$START_DELAY"
  adb_cmd shell input tap "$HOME_BOOK_TAP_X" "$HOME_BOOK_TAP_Y"
  wait "$recorder_pid" || true
  adb_cmd pull "$remote" "$OUT_DIR/$name.mp4" >/dev/null
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
  collect_logs "$name"
  capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
}

record_home_scroll_after_detail() {
  local name="home-scroll-after-detail"
  local remote="/sdcard/$name.mp4"
  adb_cmd shell rm -f "$remote"
  adb_cmd logcat -c
  adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "$remote" > "$OUT_DIR/$name-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep "$START_DELAY"
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
  adb_cmd shell input tap "$HOME_BOOK_TAP_X" "$HOME_BOOK_TAP_Y"
  sleep "$HOME_DETAIL_SETTLE_SECONDS"
  adb_cmd shell input tap "$HOME_BACK_TAP_X" "$HOME_BACK_TAP_Y"
  sleep 0.12
  adb_cmd shell input keyevent BACK
  sleep "$HOME_BACK_SETTLE_SECONDS"
  for _ in $(seq 1 "$HOME_SCROLL_ROUNDS"); do
    adb_cmd shell input swipe "$HOME_SCROLL_X" "$HOME_SCROLL_FROM_Y" "$HOME_SCROLL_X" "$HOME_SCROLL_TO_Y" 450
    sleep 0.12
    adb_cmd shell input swipe "$HOME_SCROLL_X" "$HOME_SCROLL_TO_Y" "$HOME_SCROLL_X" "$HOME_SCROLL_FROM_Y" 450
    sleep 0.12
  done
  wait "$recorder_pid" || true
  adb_cmd pull "$remote" "$OUT_DIR/$name.mp4" >/dev/null
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
  collect_logs "$name"
  capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
}

record_home_after_continue_detail_library() {
  local name="home-after-continue-detail-library"
  local remote="/sdcard/$name.mp4"
  adb_cmd shell am start -n "$ACTIVITY" >/dev/null
  adb_cmd shell input tap "$HOME_TAP_X" "$HOME_TAP_Y"
  sleep "$HOME_TAB_SETTLE_SECONDS"
  adb_cmd shell input tap "$CONTINUE_BOOK_TAP_X" "$CONTINUE_BOOK_TAP_Y"
  sleep "$HOME_DETAIL_SETTLE_SECONDS"
  adb_cmd shell input tap "$LIBRARY_TAP_X" "$LIBRARY_TAP_Y"
  sleep "$DETAIL_TO_LIBRARY_SETTLE_SECONDS"
  adb_cmd shell rm -f "$remote"
  adb_cmd logcat -c
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
  adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "$remote" > "$OUT_DIR/$name-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep "$START_DELAY"
  adb_cmd shell input tap "$HOME_TAP_X" "$HOME_TAP_Y"
  sleep "$LIBRARY_HOME_SETTLE_SECONDS"
  wait "$recorder_pid" || true
  adb_cmd pull "$remote" "$OUT_DIR/$name.mp4" >/dev/null
  adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
  collect_logs "$name"
  capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
}

case "${1:-mini-open}" in
  startup)
    adb_cmd shell am force-stop "$PACKAGE"
    adb_cmd logcat -c
    adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/startup-gfx-reset.txt" || true
    adb_cmd shell am start -W -n "$ACTIVITY" > "$OUT_DIR/startup-am-start.txt"
    sleep 3
    adb_cmd shell screencap -p "/sdcard/${PACKAGE}-startup.png"
    adb_cmd pull "/sdcard/${PACKAGE}-startup.png" "$OUT_DIR/startup.png" >/dev/null
    adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/startup-gfxinfo.txt" || true
    collect_logs startup
    ;;
  mini-open)
    record_tap now-playing-open "$MINI_TAP_X" "$MINI_TAP_Y"
    ;;
  mini-close)
    record_tap now-playing-close "$CLOSE_TAP_X" "$CLOSE_TAP_Y"
    ;;
  discover-tab)
    record_tap discover-tab "$DISCOVER_TAP_X" "$DISCOVER_TAP_Y"
    ;;
  home-tab)
    record_tap home-tab "$HOME_TAP_X" "$HOME_TAP_Y"
    ;;
  home-tab-from-settings)
    record_home_tab_from_settings
    ;;
  book-open)
    record_tap book-open "$BOOK_TAP_X" "$BOOK_TAP_Y"
    ;;
  home-book-open)
    record_home_book_open
    ;;
  home-scroll-after-detail)
    record_home_scroll_after_detail
    ;;
  home-after-continue-detail-library)
    record_home_after_continue_detail_library
    ;;
  manual)
    name="${2:-manual-transition}"
    adb_cmd shell rm -f "/sdcard/$name.mp4"
    adb_cmd shell dumpsys gfxinfo "$PACKAGE" reset > "$OUT_DIR/$name-gfx-reset.txt" || true
    adb_cmd shell screenrecord --time-limit "$RECORD_SECONDS" "/sdcard/$name.mp4"
    adb_cmd pull "/sdcard/$name.mp4" "$OUT_DIR/$name.mp4" >/dev/null
    adb_cmd shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/$name-gfxinfo.txt" || true
    collect_logs "$name"
    capture_contact_sheet "$OUT_DIR/$name.mp4" "$OUT_DIR/$name-contact.png"
    ;;
  *)
    echo "Usage: $0 {startup|mini-open|mini-close|discover-tab|home-tab|home-tab-from-settings|book-open|home-book-open|home-scroll-after-detail|home-after-continue-detail-library|manual [name]}" >&2
    echo "Override coordinates with MINI_TAP_X/Y, CLOSE_TAP_X/Y, DISCOVER_TAP_X/Y, HOME_TAP_X/Y, SETTINGS_TAP_X/Y, BOOK_TAP_X/Y, HOME_BOOK_TAP_X/Y, CONTINUE_BOOK_TAP_X/Y, LIBRARY_TAP_X/Y." >&2
    exit 64
    ;;
esac

echo "Saved captures in $OUT_DIR"
