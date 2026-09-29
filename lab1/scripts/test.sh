#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
bash scripts/build.sh

java -cp build/classes lab1.Main "${1:-striped}" 4 stress
