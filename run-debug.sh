#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "▶ Building & installing..."
./gradlew installDebug

echo "▶ Launching app..."
adb shell am start -n com.nil.mopitube/.MainActivity

echo "▶ Waiting for app PID..."
until PID=$(adb shell pidof -s com.nil.mopitube); do sleep 0.5; done

echo "▶ PID=$PID"
adb logcat --pid="$PID"
