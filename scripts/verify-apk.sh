#!/usr/bin/env bash
set -Eeuo pipefail

APK=${1:-"TITAN PULSE.apk"}
ROOTS=()

add_root() {
  local candidate=$1
  [[ -d "$candidate" ]] || return 0
  [[ " ${ROOTS[*]} " == *" $candidate "* ]] || ROOTS+=("$candidate")
}

if [[ -n "${ANDROID_HOME:-}" ]]; then add_root "$ANDROID_HOME"; fi
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then add_root "$ANDROID_SDK_ROOT"; fi
if command -v sdkmanager >/dev/null 2>&1; then
  sdkmanager_path=$(command -v sdkmanager)
  add_root "$(cd "$(dirname "$sdkmanager_path")/../../.." && pwd)"
fi
if command -v adb >/dev/null 2>&1; then
  adb_path=$(command -v adb)
  add_root "$(cd "$(dirname "$adb_path")/.." && pwd)"
fi
for candidate in /home/codespace/android-sdk /usr/local/lib/android/sdk /opt/android-sdk "$HOME/Android/Sdk"; do
  add_root "$candidate"
done

SDK_ROOT=""
for candidate in "${ROOTS[@]}"; do
  if [[ -d "$candidate/build-tools" || -d "$candidate/platform-tools" || -d "$candidate/cmdline-tools" ]]; then
    SDK_ROOT=$candidate
    break
  fi
done

if [[ -z "$SDK_ROOT" ]]; then
  echo "FAIL: Android SDK was not found" >&2
  exit 1
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

mapfile -t build_tool_dirs < <(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort -V)
BUILD_TOOLS_VERSION=""
BUILD_TOOLS=""
for version in "${build_tool_dirs[@]}"; do
  if [[ -x "$SDK_ROOT/build-tools/$version/aapt" && -x "$SDK_ROOT/build-tools/$version/apksigner" ]]; then
    BUILD_TOOLS_VERSION=$version
    BUILD_TOOLS="$SDK_ROOT/build-tools/$version"
  fi
done

if [[ -z "$BUILD_TOOLS" ]]; then
  if ! command -v sdkmanager >/dev/null 2>&1; then
    echo "FAIL: sdkmanager is unavailable and Build Tools are missing" >&2
    exit 1
  fi
  available=$(sdkmanager --sdk_root="$SDK_ROOT" --list 2>/dev/null | sed -n 's/^[[:space:]]*build-tools;\([^[:space:]]*\).*/\1/p' | sort -V | tail -1)
  [[ -n "$available" ]] || { echo "FAIL: no Build Tools package is available" >&2; exit 1; }
  yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true
  sdkmanager --sdk_root="$SDK_ROOT" "build-tools;$available"
  BUILD_TOOLS_VERSION=$available
  BUILD_TOOLS="$SDK_ROOT/build-tools/$available"
fi

export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$BUILD_TOOLS:$PATH"
AAPT="$BUILD_TOOLS/aapt"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"

failures=0
pass() { printf 'PASS: %s\n' "$1"; }
fail() { printf 'FAIL: %s\n' "$1"; failures=$((failures + 1)); }

[[ -x "$AAPT" ]] && pass "AAPT FOUND: $AAPT" || fail "AAPT NOT FOUND"
[[ -x "$APKSIGNER" ]] && pass "APKSIGNER FOUND: $APKSIGNER" || fail "APKSIGNER NOT FOUND"
[[ -f "$APK" ]] && pass "APK FOUND: $APK" || fail "APK NOT FOUND: $APK"
if [[ ! -f "$APK" ]]; then exit 1; fi

echo "Android SDK: $SDK_ROOT"
echo "Build Tools: $BUILD_TOOLS_VERSION"
echo "$("$AAPT" version | head -1)"
echo "$(sdkmanager --version)"

badging=$("$AAPT" dump badging "$APK") || { fail "AAPT could not read APK"; exit 1; }
package_line=$(grep -m1 '^package:' <<<"$badging" || true)
label_line=$(grep -m1 '^application-label:' <<<"$badging" || true)
sdk_line=$(grep -m1 "^sdkVersion:" <<<"$badging" || true)
target_line=$(grep -m1 "^targetSdkVersion:" <<<"$badging" || true)

echo "$package_line"
echo "$label_line"
echo "$sdk_line"
echo "$target_line"
grep -q "name='com.titanpulse.app'" <<<"$package_line" && pass "PACKAGE OK" || fail "unexpected package"
grep -q "versionName='1.0.0'" <<<"$package_line" && pass "VERSION OK" || fail "unexpected version"
grep -q "application-label:'TITAN PULSE'" <<<"$label_line" && pass "APP LABEL OK" || fail "unexpected application label"

if unzip -tq "$APK" >/dev/null 2>&1; then pass "APK VALID ZIP"; else fail "APK CORRUPT"; fi
if "$APKSIGNER" verify --verbose "$APK"; then pass "SIGNATURE VERIFIED"; else fail "SIGNATURE FAILED"; fi
if [[ -x "$ZIPALIGN" ]]; then
  if "$ZIPALIGN" -c -v 4 "$APK" >/dev/null; then pass "ALIGNMENT OK"; else fail "ALIGNMENT FAILED"; fi
else
  fail "ZIPALIGN NOT FOUND"
fi

echo "Native libraries:"
unzip -l "$APK" | sed -n 's/^[[:space:]]*[0-9][^ ]*[[:space:]]*[0-9-]*[[:space:]]*[0-9:]*[[:space:]]*\(lib\/[^ ]*\.so\)$/\1/p' | sort -u || true

if (( failures > 0 )); then
  echo "APK CHECK: FAIL ($failures issue(s))"
  exit 1
fi
echo "APK CHECK: PASS"