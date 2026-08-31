param(
    [string]$Bucket = $(if ($env:OSS_BUCKET) { $env:OSS_BUCKET } else { "buaa-summer-life-assistant" }),
    [string]$Prefix = $(if ($env:OSS_UPLOAD_PREFIX) { $env:OSS_UPLOAD_PREFIX } else { "life-assistant" }),
    [string]$OssUtil = $(if ($env:OSSUTIL_PATH) { $env:OSSUTIL_PATH } else { "ossutil" }),
    [switch]$SkipUpload
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Convert-HexColor {
    param([string]$Hex)

    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function New-DemoPng {
    param(
        [string]$Label,
        [string]$Subtitle,
        [string]$Background,
        [string]$Accent,
        [string]$Path
    )

    $bitmap = New-Object System.Drawing.Bitmap 800, 520
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    try {
        $backgroundBrush = New-Object System.Drawing.SolidBrush (Convert-HexColor $Background)
        $accentColor = Convert-HexColor $Accent
        $accentBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(66, $accentColor))
        $softWhiteBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(36, [System.Drawing.Color]::White))
        $panelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(46, [System.Drawing.Color]::White))
        $textBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
        $subtitleBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(210, [System.Drawing.Color]::White))
        $titleFont = New-Object System.Drawing.Font "Arial", 44, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
        $subtitleFont = New-Object System.Drawing.Font "Arial", 24, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center

        $graphics.FillRectangle($backgroundBrush, 0, 0, 800, 520)
        $graphics.FillEllipse($accentBrush, 583, 3, 184, 184)
        $graphics.FillEllipse($softWhiteBrush, -10, 300, 280, 280)
        $graphics.FillRectangle($panelBrush, 120, 118, 560, 118)
        $graphics.DrawString($Label, $titleFont, $textBrush, [System.Drawing.RectangleF]::new(120, 124, 560, 56), $format)
        $graphics.DrawString($Subtitle, $subtitleFont, $subtitleBrush, [System.Drawing.RectangleF]::new(120, 184, 560, 40), $format)

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$items = @(
    @{ Key = "demo/merchants/campus-kitchen.png"; Label = "Campus Kitchen"; Subtitle = "Rice Bowls"; Background = "#2f6f73"; Accent = "#f2b84b" },
    @{ Key = "demo/merchants/tea-corner.png"; Label = "Tea Corner"; Subtitle = "Tea And Dessert"; Background = "#536d8e"; Accent = "#f2b84b" },
    @{ Key = "demo/products/braised-pork-rice.png"; Label = "Braised Pork Rice"; Subtitle = "Campus Lunch"; Background = "#795548"; Accent = "#55b9a8" },
    @{ Key = "demo/products/kung-pao-chicken-rice.png"; Label = "Kung Pao Chicken"; Subtitle = "Hot Rice Bowl"; Background = "#8a4f3d"; Accent = "#f2b84b" },
    @{ Key = "demo/products/bubble-milk-tea.png"; Label = "Bubble Milk Tea"; Subtitle = "Fresh Tea"; Background = "#526c49"; Accent = "#d7c56d" },
    @{ Key = "demo/products/tiramisu.png"; Label = "Tiramisu"; Subtitle = "Dessert Cup"; Background = "#6f5a7e"; Accent = "#f1c88b" }
)

$normalizedPrefix = $Prefix.Trim("/")
$outputDir = Join-Path $PSScriptRoot "generated-demo-images"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

foreach ($item in $items) {
    $relativePath = $item.Key.Replace("/", [IO.Path]::DirectorySeparatorChar)
    $localPath = Join-Path $outputDir $relativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $localPath -Parent) | Out-Null

    New-DemoPng `
        -Label $item.Label `
        -Subtitle $item.Subtitle `
        -Background $item.Background `
        -Accent $item.Accent `
        -Path $localPath

    $objectKey = if ($normalizedPrefix) { "$normalizedPrefix/$($item.Key)" } else { $item.Key }
    Write-Host "Generated $localPath -> oss://$Bucket/$objectKey"

    if (-not $SkipUpload) {
        & $OssUtil cp $localPath "oss://$Bucket/$objectKey" -f
        if ($LASTEXITCODE -ne 0) {
            throw "ossutil upload failed for $objectKey"
        }
    }
}

Write-Host "Demo image assets are ready."
