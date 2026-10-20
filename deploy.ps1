# AetherLyric-app 一鍵編譯與部署腳本 (deploy.ps1)
# 依照計畫書執行：編譯 Debug APK -> adb 安裝 -> 啟動主畫面
$ErrorActionPreference = "Stop"

# 自動配置 Android SDK 與 ADB 路徑
if (Test-Path "D:\Android\Sdk\platform-tools") {
    $env:PATH = "D:\Android\Sdk\platform-tools;" + $env:PATH
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "D:\Android\Sdk"
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ">> 1. 開始執行 Gradle 編譯 (assembleDebug)..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

.\gradlew assembleDebug

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "編譯失敗或找不到輸出的 APK: $apkPath"
    exit 1
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ">> 2. 開始透過 ADB 安裝至目標裝置..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

adb install -r $apkPath

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ">> 3. 啟動應用程式 com.aetherlyric.app..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

adb shell am start -n com.aetherlyric.app/.MainActivity

Write-Host ">> AetherLyric 部署並啟動完成！" -ForegroundColor Green