param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('reset', 'phone', 'mi-outer', 'mi-inner-port', 'mi-inner-land', 'duo-outer', 'duo-inner-port', 'duo-inner-land')]
    [string]$Profile
)
# Pretend the test tablet (1600x2560 @480, VRD-W10) has the screen of a wide foldable, with
# `wm size` + `wm density`. Changing the profile while the app runs is what folding/unfolding does
# to the app (screenSize, smallestScreenSize, screenLayout, density change).
#
# Sizes are the dp size of the real screens, with pixels scaled down to fit the tablet:
#   mi-*   Xiaomi 18 Fold  outer 1168x1712 / inner 1672x2364 px at ~384 dpi (density is a guess from
#          the 385/382 ppi, the system density is not published) -> outer 487x713 dp, inner 697x985 dp
#   duo-*  iPhone Duo (iOS, the APK can not run there: only its size is used)
#          outer 466x678 pt, inner 626x890 pt
#   phone  usual phone 411x891 dp, for comparison
# ALWAYS finish with: fold.ps1 reset  (the override survives the app and even a reboot)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

$profiles = @{
    'phone'          = @(1079, 2339, 420)  # 411 x 891 dp
    'mi-outer'       = @(1168, 1712, 384)  # 487 x 713 dp
    'mi-inner-port'  = @(1533, 2167, 352)  # 697 x 985 dp
    'mi-inner-land'  = @(2167, 1533, 352)  # 985 x 697 dp
    'duo-outer'      = @(1398, 2034, 480)  # 466 x 678 dp
    'duo-inner-port' = @(1753, 2492, 448)  # 626 x 890 dp
    'duo-inner-land' = @(2225, 1565, 400)  # 890 x 626 dp
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
& $adb shell dumpsys window displays | Select-String 'init=' | Select-Object -First 1
