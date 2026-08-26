$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$BaseUrl = if ($env:PLAYWRIGHT_BASE_URL) { $env:PLAYWRIGHT_BASE_URL } else { 'http://127.0.0.1:5173' }
$ViteEntry = Join-Path $Root 'node_modules\vite\bin\vite.js'
$PlaywrightBin = Join-Path $Root 'node_modules\.bin\playwright.cmd'
$StartedVite = $null

function Test-ServerReady {
    param([string] $Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Wait-ServerReady {
    param(
        [string] $Url,
        [int] $TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-ServerReady -Url $Url) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw "Timed out waiting for Vite at $Url"
}

try {
    if (-not (Test-ServerReady -Url $BaseUrl)) {
        $StartedVite = Start-Process `
            -FilePath 'node' `
            -ArgumentList @($ViteEntry, '--host', '127.0.0.1') `
            -WorkingDirectory $Root `
            -PassThru `
            -WindowStyle Hidden

        Wait-ServerReady -Url $BaseUrl
    }

    Push-Location $Root
    try {
        & $PlaywrightBin test @args
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} catch {
    Write-Error $_
    $exitCode = 1
} finally {
    if ($StartedVite -and -not $StartedVite.HasExited) {
        Stop-Process -Id $StartedVite.Id -Force -ErrorAction SilentlyContinue
    }
}

exit $exitCode
