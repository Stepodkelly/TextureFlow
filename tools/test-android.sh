#!/usr/bin/env bash
# Run Android unit tests with a usable JDK when JAVA_HOME is unset.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  candidates=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "${HOME}/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"
  )
  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}/bin/java" ]]; then
      export JAVA_HOME="${candidate}"
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME is not set and Android Studio's JBR was not found." >&2
  echo "Install Android Studio or export JAVA_HOME to a JDK 17+." >&2
  exit 1
fi

cd "${ROOT}"
exec ./gradlew testDebugUnitTest "$@"
