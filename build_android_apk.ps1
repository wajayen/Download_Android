$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidApp = $Root
$Toolchain = "C:\antigravity\ai_test\toolchain"
$JavaHome = Join-Path $Toolchain "jdk17\jdk-17.0.19+10"
$AndroidSdk = Join-Path $Toolchain "android-sdk"
$GradleHome = Join-Path $Toolchain "gradle\gradle-8.10.2"
$GradleCache = Join-Path $Toolchain "gradle-cache"
$JavaTemp = Join-Path $Toolchain "tmp"

$GradleBat = Join-Path $GradleHome "bin\gradle.bat"

foreach ($Path in @($JavaHome, $AndroidSdk, $GradleBat)) {
    if (-not (Test-Path $Path)) {
        throw "Missing Android build dependency: $Path"
    }
}

New-Item -ItemType Directory -Force -Path $GradleCache, $JavaTemp | Out-Null

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:GRADLE_USER_HOME = $GradleCache
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$JavaTemp"

& $GradleBat -p $AndroidApp assembleDebug --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Gradle assembleDebug failed with exit code $LASTEXITCODE"
}

$ApkPath = Join-Path $AndroidApp "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkPath)) {
    throw "Gradle finished but APK was not found: $ApkPath"
}

$Dist = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $Dist | Out-Null
$VersionName = "unknown"
$VersionCode = 0
$AppBuildFile = Join-Path $Root "app\build.gradle"
$VersionMatch = Select-String -Path $AppBuildFile -Pattern 'versionName\s+"([^"]+)"' | Select-Object -First 1
if ($VersionMatch -and $VersionMatch.Matches.Count -gt 0) {
    $VersionName = $VersionMatch.Matches[0].Groups[1].Value
}
$VersionCodeMatch = Select-String -Path $AppBuildFile -Pattern 'versionCode\s+(\d+)' | Select-Object -First 1
if ($VersionCodeMatch -and $VersionCodeMatch.Matches.Count -gt 0) {
    $VersionCode = [int]$VersionCodeMatch.Matches[0].Groups[1].Value
}
$DistApk = Join-Path $Dist "downloader-android-$VersionName-debug.apk"
Copy-Item $ApkPath $DistApk -Force

Write-Host "APK built: $ApkPath"
Write-Host "APK copied: $DistApk"

$PublishScript = Join-Path $Root "publish_github.ps1"
if ($VersionCode -gt 0 -and (($VersionCode % 10) -eq 0)) {
    if (-not (Test-Path -LiteralPath $PublishScript)) {
        throw "Publish script not found: $PublishScript"
    }
    & $PublishScript -VersionCode $VersionCode -VersionName $VersionName -ApkPath $DistApk -TagName ("v{0}" -f $VersionCode)
} elseif ($VersionCode -gt 0) {
    Write-Host ("Android versionCode {0} did not reach an automatic GitHub sync point." -f $VersionCode)
}
