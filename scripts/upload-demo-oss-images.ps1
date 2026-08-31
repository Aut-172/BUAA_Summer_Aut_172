param(
    [string]$Bucket = $(if ($env:OSS_BUCKET) { $env:OSS_BUCKET } else { "buaa-summer-life-assistant" }),
    [string]$Prefix = $(if ($env:OSS_UPLOAD_PREFIX) { $env:OSS_UPLOAD_PREFIX } else { "life-assistant" }),
    [string]$OssUtil = $(if ($env:OSSUTIL_PATH) { $env:OSSUTIL_PATH } else { "ossutil" }),
    [switch]$SkipUpload
)

$ErrorActionPreference = "Stop"

function New-DemoSvg {
    param(
        [string]$Label,
        [string]$Subtitle,
        [string]$Background,
        [string]$Accent
    )

    return @"
<svg xmlns="http://www.w3.org/2000/svg" width="800" height="520" viewBox="0 0 800 520">
  <rect width="800" height="520" rx="42" fill="$Background"/>
  <circle cx="675" cy="95" r="92" fill="$Accent" opacity="0.26"/>
  <circle cx="130" cy="440" r="140" fill="#ffffff" opacity="0.13"/>
  <path d="M120 350c80-92 136-136 190-136 43 0 72 31 108 66 32 32 64 61 111 61 43 0 89-28 145-82v132H120z" fill="#ffffff" opacity="0.22"/>
  <rect x="120" y="118" width="560" height="118" rx="26" fill="#ffffff" opacity="0.17"/>
  <text x="400" y="170" text-anchor="middle" font-family="Arial, sans-serif" font-size="44" font-weight="700" fill="#ffffff">$Label</text>
  <text x="400" y="215" text-anchor="middle" font-family="Arial, sans-serif" font-size="24" fill="#ffffff" opacity="0.82">$Subtitle</text>
</svg>
"@
}

$items = @(
    @{ Key = "demo/merchants/campus-kitchen.svg"; Label = "Campus Kitchen"; Subtitle = "Rice Bowls"; Background = "#2f6f73"; Accent = "#f2b84b" },
    @{ Key = "demo/merchants/tea-corner.svg"; Label = "Tea Corner"; Subtitle = "Tea And Dessert"; Background = "#536d8e"; Accent = "#f2b84b" },
    @{ Key = "demo/products/braised-pork-rice.svg"; Label = "Braised Pork Rice"; Subtitle = "Campus Lunch"; Background = "#795548"; Accent = "#55b9a8" },
    @{ Key = "demo/products/kung-pao-chicken-rice.svg"; Label = "Kung Pao Chicken"; Subtitle = "Hot Rice Bowl"; Background = "#8a4f3d"; Accent = "#f2b84b" },
    @{ Key = "demo/products/bubble-milk-tea.svg"; Label = "Bubble Milk Tea"; Subtitle = "Fresh Tea"; Background = "#526c49"; Accent = "#d7c56d" },
    @{ Key = "demo/products/tiramisu.svg"; Label = "Tiramisu"; Subtitle = "Dessert Cup"; Background = "#6f5a7e"; Accent = "#f1c88b" }
)

$normalizedPrefix = $Prefix.Trim("/")
$outputDir = Join-Path $PSScriptRoot "generated-demo-images"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

foreach ($item in $items) {
    $relativePath = $item.Key.Replace("/", [IO.Path]::DirectorySeparatorChar)
    $localPath = Join-Path $outputDir $relativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $localPath -Parent) | Out-Null

    $svg = New-DemoSvg `
        -Label $item.Label `
        -Subtitle $item.Subtitle `
        -Background $item.Background `
        -Accent $item.Accent
    [IO.File]::WriteAllText($localPath, $svg, [Text.UTF8Encoding]::new($false))

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
