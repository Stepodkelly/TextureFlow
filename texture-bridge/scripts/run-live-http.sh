#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
exec node --env-file="$ROOT_DIR/.env.local" "$ROOT_DIR/texture-bridge/dist/http.js"
