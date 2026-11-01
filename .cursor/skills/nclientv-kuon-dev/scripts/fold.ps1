param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('reset', 'phone', 'mi-outer', 'mi-inner-port', 'mi-inner-land', 'real-outer')]
    [string]$Profile
)
# Pretend the connected device has the screen of the Xiaomi 18 Fold, with `wm size` + `wm density`.
# Changing the profile while the app runs is what folding/unfolding does to the app (screenSize,
# smallestScreenSize, screenLayout, density change). Real screen data: ../devices/xiaomi-18-fold.md
#
# Profiles keep the dp size of the real screens (outer 425x623 dp = nrml, inner 608x860 dp = lrg):
#   mi-outer / mi-inner-port / mi-inner-land   for the test tablet (1600x2560 @480): px scaled to fit
#   real-outer   ONLY on the real Xiaomi 18 Fold: its cover screen resolution 1168x1712 @440 (the
#                cover screen can not be used by adb while the phone is open)
#   phone        usual phone 411x891 dp (1079x2339 @420), for comparison
# ALWAYS finish with: fold.ps1 reset  (the override survives the app and even a reboot)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

$profiles = @{
    'phone'         = @(1079, 2339, 420)  # 411 x 891 dp
    'mi-outer'      = @(1275, 1869, 480)  # 425 x 623 dp
    'mi-inner-port' = @(1520, 2150, 400)  # 608 x 860 dp
    'mi-inner-land' = @(2150, 1520, 400)  # 860 x 608 dp
    'real-outer'    = @(1168, 1712, 440)  # 425 x 623 dp
}

if ($Profile -eq 'reset') {
    & $adb shell wm size reset
    & $adb shell wm density reset
} else {
    $p = $profiles[$Profile]
    & $adb shell wm size "$($p[0])x$($p[1])"
    & $adb shell wm density $p[2]
}
Start-Sleep -Seconds 3
& $adb shell wm size
& $adb shell wm density
& $adb shell dumpsys window displays | Select-String 'init=' | ForEach-Object { $_.Line.Trim() }
