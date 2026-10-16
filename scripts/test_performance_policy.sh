#!/usr/bin/env bash
# No Android SDK or network required.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
javac -d "$WORK" "$ROOT/apps/AmpyCore/app/src/main/java/dev/ampybios/core/PerformancePolicy.java" "$ROOT/tests/PerformancePolicyTest.java"
java -cp "$WORK" dev.ampybios.core.PerformancePolicyTest
