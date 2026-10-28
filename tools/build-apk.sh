#!/usr/bin/env bash
# 只出一个包：R8 已开（app/build.gradle.kts 的 debug 变体），这里跳过 lint vital。
# 本机 lint 极重（整跑 580 秒都没过），且它只是发布前的静态检查，不影响 APK。
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
exec ./gradlew :app:assembleGithubDebug \
  -x :web:buildWebUi \
  -x :app:lintVitalGithubDebug \
  -x :app:lintVitalAnalyzeGithubDebug \
  -x :app:lintVitalReportGithubDebug \
  --console=plain "$@"
