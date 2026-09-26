#!/usr/bin/env bash
# Local Stream J harness: emulator or a USB phone both work.
# Does not add a CI emulator job. Unit tests stay on tools/test-android.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_ID="com.textureflow"
TEST_ID="com.textureflow.test"
LISTENER_COMPONENT="${APP_ID}/com.textureflow.notifications.TextureNotificationListenerService"
RUNNER="${TEST_ID}/androidx.test.runner.AndroidJUnitRunner"
E2E_CLASS="com.textureflow.e2e.TextureFlowE2eTest"
FORCE_INSTALL=0
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: tools/e2e/run.sh [--reinstall] [--serial SERIAL] [--class FQCN]

Runs TextureFlow notification e2e instrumentation on a connected emulator
or USB phone.

  --reinstall   Rebuild and reinstall debug + androidTest APKs
  --serial      adb serial (or set ANDROID_SERIAL)
  --class       Instrumentation class (default: com.textureflow.e2e.TextureFlowE2eTest)

The script:
  1. Finds ANDROID_HOME / adb
  2. Grants the notification listener
  3. Installs debug + androidTest APKs if they are missing
  4. Runs only the e2e class (not Stream E's ModelBenchmarkTest)

EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reinstall) FORCE_INSTALL=1; shift ;;
    --serial)
      SERIAL="${2:-}"
      if [[ -z "${SERIAL}" ]]; then
        echo "--serial requires a device id" >&2
        exit 1
      fi
      shift 2
      ;;
    --class)
      E2E_CLASS="${2:-}"
      if [[ -z "${E2E_CLASS}" ]]; then
        echo "--class requires a fully-qualified test class" >&2
        exit 1
      fi
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    return 0
  fi
  local candidates=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "${HOME}/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}/bin/java" ]]; then
      export JAVA_HOME="${candidate}"
      return 0
    fi
  done
  echo "JAVA_HOME is not set and Android Studio's JBR was not found." >&2
  echo "Install Android Studio or export JAVA_HOME to a JDK 17+." >&2
  exit 1
}

read_sdk_dir_from_local_properties() {
  local file="${ROOT}/local.properties"
  if [[ ! -f "${file}" ]]; then
    return 1
  fi
  local line
  line="$(grep -E '^sdk\.dir=' "${file}" | head -n 1 || true)"
  if [[ -z "${line}" ]]; then
    return 1
  fi
  local value="${line#sdk.dir=}"
  value="${value//\\:/:}"
  value="${value%$'\r'}"
  if [[ -d "${value}" ]]; then
    printf '%s\n' "${value}"
    return 0
  fi
  return 1
}

resolve_android_home() {
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    printf '%s\n' "${ANDROID_HOME}"
    return 0
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}"
    return 0
  fi
  local from_props
  if from_props="$(read_sdk_dir_from_local_properties)"; then
    printf '%s\n' "${from_props}"
    return 0
  fi
  local candidate
  for candidate in "${HOME}/Library/Android/sdk" "${HOME}/Android/Sdk"; do
    if [[ -d "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_adb() {
  local sdk="$1"
  if [[ -x "${sdk}/platform-tools/adb" ]]; then
    printf '%s\n' "${sdk}/platform-tools/adb"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  return 1
}

adb_cmd() {
  if [[ -n "${SERIAL}" ]]; then
    "${ADB}" -s "${SERIAL}" "$@"
  else
    "${ADB}" "$@"
  fi
}

package_installed() {
  adb_cmd shell pm path "$1" >/dev/null 2>&1
}

pick_device() {
  adb_cmd start-server >/dev/null
  if [[ -n "${SERIAL}" ]]; then
    adb_cmd wait-for-device
    return 0
  fi

  local lines
  lines="$(adb_cmd devices | awk 'NR>1 && $2=="device" { print $1 }')"
  if [[ -z "${lines}" ]]; then
    echo "No emulator or USB phone is connected." >&2
    echo "Start an AVD or plug in a phone with USB debugging, then retry." >&2
    exit 1
  fi
  local count
  count="$(printf '%s\n' "${lines}" | grep -c .)"
  if [[ "${count}" -gt 1 ]]; then
    echo "Multiple devices are connected. Pass --serial or set ANDROID_SERIAL." >&2
    adb_cmd devices >&2
    exit 1
  fi
  SERIAL="${lines}"
  adb_cmd wait-for-device
}

install_apks_if_needed() {
  local need=0
  if [[ "${FORCE_INSTALL}" -eq 1 ]]; then
    need=1
  fi
  if ! package_installed "${APP_ID}"; then
    need=1
  fi
  if ! package_installed "${TEST_ID}"; then
    need=1
  fi
  if [[ "${need}" -eq 0 ]]; then
    echo "Debug and androidTest APKs are already installed."
    return 0
  fi
  echo "Installing debug and androidTest APKs…"
  (
    cd "${ROOT}"
    ./gradlew :app:installDebug :app:installDebugAndroidTest
  )
}

grant_runtime_permissions() {
  adb_cmd shell pm grant "${APP_ID}" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb_cmd shell pm grant "${TEST_ID}" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
}

grant_notification_listener() {
  echo "Granting notification listener: ${LISTENER_COMPONENT}"
  adb_cmd shell cmd notification allow_listener "${LISTENER_COMPONENT}"
  adb_cmd shell am start -W -n "${APP_ID}/.ui.MainActivity" >/dev/null || true
}

resolve_java_home
ANDROID_HOME="$(resolve_android_home)" || {
  echo "Could not find the Android SDK." >&2
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT, or create local.properties with sdk.dir=." >&2
  exit 1
}
export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME}}"

ADB="$(resolve_adb "${ANDROID_HOME}")" || {
  echo "adb was not found under ${ANDROID_HOME}/platform-tools or on PATH." >&2
  exit 1
}

echo "ANDROID_HOME=${ANDROID_HOME}"
echo "adb=${ADB}"
pick_device
echo "device=${SERIAL}"

install_apks_if_needed
grant_runtime_permissions
grant_notification_listener

echo "Running ${E2E_CLASS}"
adb_cmd shell am instrument -w -r \
  -e class "${E2E_CLASS}" \
  "${RUNNER}"
