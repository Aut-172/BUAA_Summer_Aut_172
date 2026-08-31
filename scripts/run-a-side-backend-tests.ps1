$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradleHome = Join-Path $root '.gradle'
$gradleExe = Get-ChildItem -Path (Join-Path $gradleHome 'wrapper\dists\gradle-9.5.1-bin') -Recurse -Filter gradle.bat |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $gradleExe) {
    throw 'Cannot find local Gradle distribution under .gradle/wrapper/dists/gradle-9.5.1-bin'
}

Set-Location $root
& $gradleExe ":merchant-service:printTestRuntimeClasspath" --no-daemon
