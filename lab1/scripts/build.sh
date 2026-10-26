#!/usr/bin/env bash
set -euo pipefail

# Switch to lab1 regardless of where the script was called from.
cd "$(dirname "$0")/.."

mkdir -p build/classes

javac --release 21 -Xlint:all -d build/classes \
    src/lab1/*.java \
    src/lab1/collectors/*.java \
    src/lab1/benchmark/*.java
