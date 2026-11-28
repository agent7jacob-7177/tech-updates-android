#!/usr/bin/env bash
set -euo pipefail

SDK="$HOME/Android/Sdk"
AVD_NAME="tu_api35"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
PACKAGE="com.code7.techupdates"
ACTIVITY="${PACKAGE}/.MainActivity"
RUNS=5

# Ensure adb is in PATH
export PATH="$PATH:$SDK/platform-tools"

# ------------------------------------------------------------------
# 1. Make sure an emulator is running
# ------------------------------------------------------------------
ensure_emulator() {
    if adb devices | grep -qE 'device$'; then
        echo "Emulator already running."
        return
    fi
    echo "No device found. Booting AVD '$AVD_NAME'..."
    emulator -avd "$AVD_NAME" -no-snapshot -no-window &
    echo "Waiting for emulator to boot (up to 120s)..."
    for i in $(seq 1 120); do
        if adb devices | grep -qE 'device$'; then
            echo "Emulator booted after ${i}s."
            return
        fi
        sleep 1
    done
    echo "ERROR: Emulator did not boot in 120s." >&2
    exit 1
}

# ------------------------------------------------------------------
# 2. Build release APK
# ------------------------------------------------------------------
echo "=== Building release APK ==="
(cd "$(dirname "$0")/.." && ./gradlew assembleRelease -q)
APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null)
echo "APK size: ${APK_SIZE} bytes"

# ------------------------------------------------------------------
# 3. Install APK
# ------------------------------------------------------------------
echo "=== Installing APK ==="
adb install -r "$APK_PATH"

# ------------------------------------------------------------------
# 4. Measure startup (5 runs)
# ------------------------------------------------------------------
echo "=== Measuring startup ($RUNS runs) ==="
TIMES_FILE=$(mktemp)
for i in $(seq 1 $RUNS); do
    adb -s emulator-5554 shell am force-stop "$PACKAGE" || true
    OUTPUT=$(adb -s emulator-5554 shell am start -W -n "$ACTIVITY" 2>&1)
    TOTAL_TIME=$(echo "$OUTPUT" | grep 'TotalTime:' | head -1 | sed 's/.*TotalTime://' | tr -d '; ' | tr -d '[:space:]')
    if [ -z "$TOTAL_TIME" ]; then
        echo "Run $i: FAILED to parse TotalTime"
        echo "FAIL" >> "$TIMES_FILE"
    else
        echo "Run $i: TotalTime=${TOTAL_TIME}ms"
        echo "$TOTAL_TIME" >> "$TIMES_FILE"
    fi
done

# ------------------------------------------------------------------
# 5. Compute median
# ------------------------------------------------------------------
echo ""
echo "=== Results ==="
echo "APK size: ${APK_SIZE} bytes"
echo "Individual times (ms):"
cat "$TIMES_FILE"
echo ""

# Sort numeric values for median (skip FAILs)
NUMERIC_TIMES=$(grep -E '^[0-9]+$' "$TIMES_FILE" | sort -n)
COUNT=$(echo "$NUMERIC_TIMES" | wc -l)
if [ "$COUNT" -gt 0 ]; then
    MID=$(( (COUNT + 1) / 2 ))
    MEDIAN=$(echo "$NUMERIC_TIMES" | sed -n "${MID}p")
    echo "Median TotalTime: ${MEDIAN}ms (${COUNT} valid runs)"
else
    echo "No valid runs."
fi

rm -f "$TIMES_FILE"
