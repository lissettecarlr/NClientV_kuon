param(
    [string]$Out = "$env:TEMP\shot.png",
    [int]$Width = 800
)
# Screenshot of the display that is ON (a foldable has two: plain `screencap` gives a corrupt PNG),
# scaled down so that it can be read. Needs hide_multitasking=false in the app prefs (FLAG_SECURE).
# Usage: powershell -File .cursor/skills/nclientv-kuon-dev/scripts/shot.ps1 [-Out file.png] [-Width 800]
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$id = $null
foreach ($l in (& $adb shell dumpsys display)) {
    if ($l -match 'mBaseDisplayInfo=.* state ON, .*address StablePhysical\{id=(\d+)') { $id = $Matches[1]; break }
}
$raw = "$env:TEMP\shot_raw.png"
if ($id) { cmd /c "`"$adb`" exec-out screencap -d $id -p > `"$raw`"" }
else { cmd /c "`"$adb`" exec-out screencap -p > `"$raw`"" }

Add-Type -AssemblyName System.Drawing
$i = [Drawing.Image]::FromFile($raw)
$h = [int]($i.Height * $Width / $i.Width)
$b = New-Object Drawing.Bitmap $Width, $h
$g = [Drawing.Graphics]::FromImage($b)
$g.InterpolationMode = 'HighQualityBicubic'
$g.DrawImage($i, 0, 0, $Width, $h)
$b.Save($Out, [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $b.Dispose(); $i.Dispose()
Write-Host "display=$id real=$($i.Width)x$($i.Height) -> $Out ($Width x $h)"
