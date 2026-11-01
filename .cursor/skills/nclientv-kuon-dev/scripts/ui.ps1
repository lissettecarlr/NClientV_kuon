# Print what is on the phone screen (texts / content descriptions with bounds) via uiautomator.
# Screenshots are black because the app uses FLAG_SECURE, so read the UI tree instead.
# Usage: powershell -File .cursor/skills/nclientv-kuon-dev/scripts/ui.ps1
# Tap the center of a bounds with: adb shell input tap X Y   (long press: adb shell input swipe X Y X Y 800)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell uiautomator dump /sdcard/u.xml | Out-Null
$xml = & $adb shell cat /sdcard/u.xml
[regex]::Matches(($xml -join ''), '<node [^>]*>') | ForEach-Object {
    $n = $_.Value
    $t = if ($n -match ' text="([^"]+)"') { $Matches[1] } else { $null }
    $d = if ($n -match 'content-desc="([^"]+)"') { $Matches[1] } else { $null }
    $b = if ($n -match 'bounds="([^"]+)"') { $Matches[1] } else { '' }
    $label = if ($t) { $t } else { $d }
    if ($label) { "$label  $b" }
}
