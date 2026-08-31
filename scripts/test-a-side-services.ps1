param(
    [string]$GradlePath,
    [string]$ReportPath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot "reports/testing/a-side-test-report.md"
}

$params = @{
    ReportPath = $ReportPath
    ReportTitle = "A-side Test Report"
    LogPath = (Join-Path $repoRoot "reports/testing/a-side-test-log.txt")
    ServiceDirs = @(
        "services/merchant-service",
        "services/order-service",
        "services/settlement-service"
    )
}

if (-not [string]::IsNullOrWhiteSpace($GradlePath)) {
    $params["GradlePath"] = $GradlePath
}

& (Join-Path $PSScriptRoot "test-microservices.ps1") @params
exit $LASTEXITCODE
