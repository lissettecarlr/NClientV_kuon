# Install the built debug APK on the connected phone and launch it.
# The Huawei installer shows a "继续安装" screen that must be tapped once; this script does it.
# Usage: powershell -File .cursor/skills/nclientv-kuon-dev/scripts/install.ps1 [-NoLaunch]
param([switch]$NoLaunch)
Set-Location (Resolve-Path "$PSScriptRoot\..\..\..\..")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.maxwai.nclientv3.debug'
$apk = (Get-ChildItem app\build\outputs\apk\post28\debug\*.apk | Select-Object -First 1).FullName

if (-not ((& $adb devices) -match '\tdevice')) { Write-Error 'No phone connected (adb devices is empty)'; exit 1 }

for ($try = 1; $try -le 2; $try++) {
    $out = "$env:TEMP\nclient_install.txt"
    $p = Start-Process -PassThru -NoNewWindow -RedirectStandardOutput $out -FilePath $adb -ArgumentList 'install', '-r', '-d', $apk
    $tapped = $false
    for ($i = 0; $i -lt 20 -and -not $p.HasExited; $i++) {
        Start-Sleep 2
        & $adb shell uiautomator dump /sdcard/u.xml | Out-Null
        # tap ONLY once: a second blind tap hits "cancel" ("User rejected permissions")
        if (-not $tapped -and ((& $adb shell cat /sdcard/u.xml) -match '继续安装')) {
            & $adb shell input tap 800 2428
            $tapped = $true
        }
    }
    $p.WaitForExit(30000) | Out-Null
    $result = Get-Content $out -Raw
    if ($result -match 'Success') { Write-Host "Installed: $(& $adb shell dumpsys package $pkg | Select-String lastUpdateTime)"; break }
    Write-Host "Install attempt $try failed: $result"
    & $adb shell input keyevent 4   # close a possibly stuck installer screen
}
if ($result -notmatch 'Success') { exit 1 }

if (-not $NoLaunch) {
    & $adb shell am force-stop $pkg
    & $adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
}
