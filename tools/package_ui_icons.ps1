param([string]$Manifest = 'output/imagegen/ios-icons/manifest.json')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifestPath = Join-Path $taskRoot $Manifest
$manifestDir = Split-Path -Parent $manifestPath
$iconEntries = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
foreach ($entry in $iconEntries.icons) {
    $original = Join-Path $taskRoot "output/imagegen/ios-icons/originals/$($entry.name).png"
    $sourcePath = if ([System.IO.Path]::IsPathRooted($entry.source)) { $entry.source } else { Join-Path $manifestDir $entry.source }
    if ([System.IO.Path]::GetFullPath($sourcePath) -ne [System.IO.Path]::GetFullPath($original)) {
        Copy-Item -LiteralPath $sourcePath -Destination $original -Force
    }
    $source = [System.Drawing.Bitmap]::new($original)
    $left = $source.Width; $top = $source.Height; $right = 0; $bottom = 0
    # Only trim empty alpha margins; preserve the generated icon pixels and material.
    for ($y = 0; $y -lt $source.Height; $y += 2) {
        for ($x = 0; $x -lt $source.Width; $x += 2) {
            if ($source.GetPixel($x, $y).A -gt 16) {
                $left = [Math]::Min($left, $x); $right = [Math]::Max($right, $x)
                $top = [Math]::Min($top, $y); $bottom = [Math]::Max($bottom, $y)
            }
        }
    }
    if ($right -le $left -or $bottom -le $top) { throw "Empty icon: $($entry.name)" }
    $cropWidth = $right - $left + 2; $cropHeight = $bottom - $top + 2
    $ratio = 336.0 / [Math]::Max($cropWidth, $cropHeight)
    $width = [int]($cropWidth * $ratio); $height = [int]($cropHeight * $ratio)
    $target = [System.Drawing.Bitmap]::new(384, 384, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $destRect = [System.Drawing.Rectangle]::new([int]((384-$width)/2), [int]((384-$height)/2), $width, $height)
    $graphics.DrawImage($source, $destRect, $left, $top, $cropWidth, $cropHeight, [System.Drawing.GraphicsUnit]::Pixel)
    $target.Save((Join-Path $taskRoot "app/src/main/res/drawable-nodpi/art_$($entry.name).png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $target.Dispose(); $source.Dispose()
    Write-Output "Packaged $($entry.name)"
}
