param(
    [string]$GradleUserHome = "$env:TEMP\life-assistant-gradle-home",
    [string]$BuildDir = "$env:TEMP\life-assistant-backend-build"
)

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$gradlew = Join-Path $backend "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found: $gradlew"
}

New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

Push-Location $backend
try {
    & $gradlew --gradle-user-home $GradleUserHome --no-daemon "-PbuildDir=$BuildDir" test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
