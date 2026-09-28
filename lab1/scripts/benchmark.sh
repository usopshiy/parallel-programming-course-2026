#!/usr/bin/env bash
set -euo pipefail

# Switch to lab1 regardless of where the script was called from.
cd "$(dirname "$0")/.."
bash scripts/build.sh

java -cp build/classes lab1.Main
