# Build the debug APK (post28 flavor) and check that it is complete.
# Usage: powershell -File .cursor/skills/nclientv-kuon-dev/scripts/build.ps1
Set-Location (Resolve-Path "$PSScriptRoot\..\..\..\..")

# --rerun-tasks: an incremental/sandboxed build once produced a truncated 3 MB APK (no classes.dex)
# JDK = the one bundled with Android Studio (no system JDK on this machine)
$env:JAVA_HOME = 'E:\Android studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$log = ./gradlew.bat assemblePost28Debug --console=plain --rerun-tasks -x lint 2>&1 | ForEach-Object { "$_" }
$code = $LASTEXITCODE
$log | Select-Object -Last 8
if ($code -ne 0) { Write-Host 'Gradle build failed'; exit 1 }

$apk = Get-ChildItem app\build\outputs\apk\post28\debug\*.apk | Select-Object -First 1
$mb = [math]::Round($apk.Length / 1MB, 1)
Write-Host "APK: $($apk.FullName) ($mb MB)"
if ($apk.Length -lt 15MB) { Write-Error 'APK looks truncated (expected about 20 MB), rebuild'; exit 1 }
