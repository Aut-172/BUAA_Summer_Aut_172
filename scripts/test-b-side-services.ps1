param(
    [string]$GradlePath,
    [string]$ReportPath
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

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot "reports/testing/b-side-test-report.md"
}

function Get-CommandOutputLine {
    param([scriptblock]$Command)

    try {
        $output = & $Command 2>&1
        $line = $output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
        return $line -as [string]
    } catch {
        return "unavailable: $($_.Exception.Message)"
    }
}

function Search-Text {
    param(
        [string]$Pattern,
        [string[]]$Paths
    )

    $existingPaths = @($Paths | ForEach-Object { Join-Path $repoRoot $_ } | Where-Object { Test-Path -LiteralPath $_ })
    if ($existingPaths.Count -eq 0) {
        return @()
    }

    $rg = Get-Command rg -ErrorAction SilentlyContinue
    if ($rg) {
        $matches = & $rg.Source $Pattern @existingPaths -n 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @($matches)
        }
        return @()
    }

    return @(Get-ChildItem -LiteralPath $existingPaths -Recurse -File -Include *.java,*.yml,*.yaml,*.properties 2>$null |
        Select-String -Pattern $Pattern -AllMatches |
        ForEach-Object { "$($_.Path):$($_.LineNumber):$($_.Line.Trim())" })
}

function Get-TestSummary {
    param([string[]]$Dirs)

    $summary = [ordered]@{
        Total = 0
        Passed = 0
        Failed = 0
        Skipped = 0
        Services = @()
        FailureReasons = @()
    }

    foreach ($serviceDir in $Dirs) {
        $resultDir = Join-Path $repoRoot "$serviceDir/build/test-results/test"
        $service = [ordered]@{
            Name = Split-Path -Leaf $serviceDir
            Total = 0
            Passed = 0
            Failed = 0
            Skipped = 0
        }

        if (Test-Path -LiteralPath $resultDir) {
            $files = Get-ChildItem -LiteralPath $resultDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue
            foreach ($file in $files) {
                [xml]$xml = Get-Content -LiteralPath $file.FullName
                $suite = $xml.testsuite
                $tests = [int]$suite.tests
                $failures = [int]$suite.failures + [int]$suite.errors
                $skipped = [int]$suite.skipped
                $passed = $tests - $failures - $skipped

                $service.Total += $tests
                $service.Passed += $passed
                $service.Failed += $failures
                $service.Skipped += $skipped

                foreach ($case in $suite.testcase) {
                    $nodes = @($case.failure) + @($case.error) | Where-Object { $null -ne $_ }
                    foreach ($node in $nodes) {
                        $message = $node.message
                        if ([string]::IsNullOrWhiteSpace($message)) {
                            $message = $node.InnerText.Split("`n")[0]
                        }
                        $summary.FailureReasons += "- $($service.Name) :: $($case.classname).$($case.name): $message"
                    }
                }
            }
        }

        $summary.Total += $service.Total
        $summary.Passed += $service.Passed
        $summary.Failed += $service.Failed
        $summary.Skipped += $service.Skipped
        $summary.Services += [pscustomobject]$service
    }

    return [pscustomobject]$summary
}

function Write-TestReport {
    param(
        [string[]]$Dirs,
        [string]$Path,
        [string]$Status,
        [string]$FailureMessage,
        [string]$GradleExecutable
    )

    $summary = Get-TestSummary $Dirs
    $reportDir = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

    $branch = Get-CommandOutputLine { git branch --show-current }
    $commit = Get-CommandOutputLine { git rev-parse --short HEAD }
    $javaVersion = Get-CommandOutputLine { java -version }
    $gradleVersion = Get-CommandOutputLine { & $GradleExecutable --version | Where-Object { $_ -match "^Gradle " } }
    $os = [System.Environment]::OSVersion.VersionString
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

    $lines = @()
    $lines += "# B-side Test Report"
    $lines += ""
    $lines += "## Summary"
    $lines += ""
    $lines += "| Status | Total | Passed | Failed | Skipped |"
    $lines += "| --- | ---: | ---: | ---: | ---: |"
    $lines += "| $Status | $($summary.Total) | $($summary.Passed) | $($summary.Failed) | $($summary.Skipped) |"
    $lines += ""
    $lines += "## Service Results"
    $lines += ""
    $lines += "| Service | Total | Passed | Failed | Skipped |"
    $lines += "| --- | ---: | ---: | ---: | ---: |"
    foreach ($service in $summary.Services) {
        $lines += "| $($service.Name) | $($service.Total) | $($service.Passed) | $($service.Failed) | $($service.Skipped) |"
    }
    $lines += ""
    $lines += "## Failure Reasons"
    $lines += ""
    if ($summary.FailureReasons.Count -gt 0) {
        $lines += $summary.FailureReasons
    } elseif (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        $lines += "- $FailureMessage"
    } else {
        $lines += "- None"
    }
    $lines += ""
    $lines += "## Runtime Environment"
    $lines += ""
    $lines += "| Item | Value |"
    $lines += "| --- | --- |"
    $lines += "| Generated At | $timestamp |"
    $lines += "| OS | $os |"
    $lines += "| Java | $javaVersion |"
    $lines += "| Gradle | $gradleVersion |"
    $lines += "| Branch | $branch |"
    $lines += "| Commit | $commit |"
    $lines += "| Services | $($Dirs -join ', ') |"

    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
    Write-Host "Test report written to $Path"
}

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

$forbiddenPackageMatches = Search-Text "com\.example\.demo\.(merchant|order|coupon|payment)" @(
    "services/user-service/src/main/java",
    "services/fulfillment-service/src/main/java",
    "services/engagement-service/src/main/java"
)
if ($forbiddenPackageMatches.Count -gt 0) {
    Write-Error "Forbidden cross-service package reference found:`n$forbiddenPackageMatches"
}

$forbiddenMapperMatches = Search-Text "\b(OrdersMapper|OrderItemMapper|MerchantMapper|ProductMapper|CouponService|PaymentMapper)\b" @(
    "services/user-service/src/main/java",
    "services/fulfillment-service/src/main/java",
    "services/engagement-service/src/main/java"
)
if ($forbiddenMapperMatches.Count -gt 0) {
    Write-Error "Forbidden cross-service mapper/service reference found:`n$forbiddenMapperMatches"
}

$restClientMatches = Search-Text "RestClient|services\..*base-url|localhost:808[0-9]" @(
    "services/user-service/src/main/java",
    "services/user-service/src/main/resources",
    "services/fulfillment-service/src/main/java",
    "services/fulfillment-service/src/main/resources",
    "services/engagement-service/src/main/java",
    "services/engagement-service/src/main/resources"
)
if ($restClientMatches.Count -gt 0) {
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
    } catch {
        Write-TestReport $serviceDirs $ReportPath "FAILED" $_.Exception.Message $gradlePath
        throw
    } finally {
        Pop-Location
    }
}

Write-TestReport $serviceDirs $ReportPath "PASSED" "" $gradlePath
Write-Host "B-side service checks passed."
