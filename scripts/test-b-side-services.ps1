param(
    [string]$GradlePath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceDirs = @(
    "services/merchant-service",
    "services/user-service",
    "services/fulfillment-service",
    "services/engagement-service"
)

$bSideServiceDirs = @(
    "services/user-service",
    "services/fulfillment-service",
    "services/engagement-service"
)

function Resolve-GradlePath {
    param([string]$ExplicitGradlePath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitGradlePath)) {
        return (Resolve-Path -LiteralPath $ExplicitGradlePath).Path
    }

    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return $gradle.Source
    }

    $zipPath = Join-Path $repoRoot "gradle-9.5.1-bin.zip"
    if (-not (Test-Path -LiteralPath $zipPath)) {
        throw "Gradle is not installed and local gradle-9.5.1-bin.zip was not found."
    }

    $runtimeDir = Join-Path $env:TEMP "codex-gradle-9.5.1"
    $gradleBat = Join-Path $runtimeDir "gradle-9.5.1/bin/gradle.bat"
    if (-not (Test-Path -LiteralPath $gradleBat)) {
        New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
        Expand-Archive -LiteralPath $zipPath -DestinationPath $runtimeDir -Force
    }
    return $gradleBat
}

Write-Host "Checking B-side service boundaries..."
foreach ($serviceDir in $bSideServiceDirs) {
    $commonSourcePath = Join-Path $repoRoot "$serviceDir/src/main/java/com/example/demo/common"
    if (Test-Path -LiteralPath $commonSourcePath) {
        throw "Common source must not be copied into $serviceDir. Depend on services/common-lib instead."
    }
}

$forbiddenPackageMatches = & rg "com\.example\.demo\.(merchant|order|coupon|payment)" `
    "services/user-service/src/main/java" `
    "services/fulfillment-service/src/main/java" `
    "services/engagement-service/src/main/java" `
    -n 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Error "Forbidden cross-service package reference found:`n$forbiddenPackageMatches"
}

$forbiddenMapperMatches = & rg "\b(OrdersMapper|OrderItemMapper|MerchantMapper|ProductMapper|CouponService|PaymentMapper)\b" `
    "services/user-service/src/main/java" `
    "services/fulfillment-service/src/main/java" `
    "services/engagement-service/src/main/java" `
    -n 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Error "Forbidden cross-service mapper/service reference found:`n$forbiddenMapperMatches"
}

$restClientMatches = & rg "RestClient|services\..*base-url|localhost:808[0-9]" `
    "services/user-service/src/main/java" `
    "services/user-service/src/main/resources" `
    "services/fulfillment-service/src/main/java" `
    "services/fulfillment-service/src/main/resources" `
    "services/engagement-service/src/main/java" `
    "services/engagement-service/src/main/resources" `
    -n 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Error "B-side services must use OpenFeign service names, not RestClient or fixed service URLs:`n$restClientMatches"
}

$gradlePath = Resolve-GradlePath $GradlePath
foreach ($serviceDir in $serviceDirs) {
    Write-Host "Running Gradle test for $serviceDir..."
    Push-Location (Join-Path $repoRoot $serviceDir)
    try {
        & $gradlePath test
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle test failed for $serviceDir"
        }
    } finally {
        Pop-Location
    }
}

Write-Host "B-side service checks passed."
