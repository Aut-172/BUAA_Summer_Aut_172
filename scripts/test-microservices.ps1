param(
    [string]$GradlePath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceDirs = @(
    "services/api-gateway",
    "services/merchant-service",
    "services/user-service",
    "services/order-service",
    "services/settlement-service",
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

Write-Host "Microservice checks passed."
