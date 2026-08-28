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

function Write-TestReport {
    param(
        [object[]]$ServiceResults,
        [string]$Path,
        [string]$Status,
        [string]$FailureMessage,
        [string]$GradleExecutable
    )

    $summary = [ordered]@{
        Total = 0
        Passed = 0
        Failed = 0
        Skipped = 0
        Services = @()
    }

    foreach ($service in $ServiceResults) {
        $summary.Total += [int]$service.Total
        $summary.Passed += [int]$service.Passed
        $summary.Failed += [int]$service.Failed
        $summary.Skipped += [int]$service.Skipped
        $summary.Services += $service
    }

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
    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
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
    $lines += "| Services | $($ServiceResults.Name -join ', ') |"

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

function Get-ManualRunnerSource {
    @'
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class ManualJUnitRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: ManualJUnitRunner <test-classes-dir>");
        }

        Path testClassesDir = Paths.get(args[0]);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(testClassesDir)))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        for (TestExecutionSummary.Failure failure : summary.getFailures()) {
            System.out.println("FAILED TEST: " + failure.getTestIdentifier().getDisplayName());
            System.out.println("CAUSE: " + failure.getException().getClass().getName() + ": " + failure.getException().getMessage());
        }
        summary.printTo(new PrintWriter(System.out, true));
        if (summary.getTotalFailureCount() > 0) {
            System.exit(1);
        }
    }
}
'@
}

function Invoke-Gradle {
    param(
        [string]$GradleExecutable,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $GradleExecutable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle command failed in $WorkingDirectory"
        }
    }
    finally {
        Pop-Location
    }
}

function Parse-TestRunSummary {
    param([string[]]$OutputLines)

    $testsFound = 0
    $testsFailed = 0
    $testsSkipped = 0

    foreach ($line in $OutputLines) {
        if ($line -match '\[\s*(\d+)\s+tests found\s*\]') {
            $testsFound = [int]$Matches[1]
        } elseif ($line -match '\[\s*(\d+)\s+tests skipped\s*\]') {
            $testsSkipped = [int]$Matches[1]
        } elseif ($line -match '\[\s*(\d+)\s+tests failed\s*\]') {
            $testsFailed = [int]$Matches[1]
        }
    }

    $testsPassed = $testsFound - $testsFailed - $testsSkipped
    if ($testsPassed -lt 0) {
        $testsPassed = 0
    }

    return [pscustomobject]@{
        Total = $testsFound
        Passed = $testsPassed
        Failed = $testsFailed
        Skipped = $testsSkipped
    }
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
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-home-d"
$env:TEMP = Join-Path $repoRoot ".tmp-d"
$env:TMP = $env:TEMP

$runnerDir = Join-Path $repoRoot ".manual-junit-runner"
$runnerSource = Join-Path $runnerDir "ManualJUnitRunner.java"
$runnerClasspath = Join-Path $runnerDir "classes"
New-Item -ItemType Directory -Force -Path $runnerDir, $runnerClasspath, $env:GRADLE_USER_HOME, $env:TEMP | Out-Null
[System.IO.File]::WriteAllText($runnerSource, (Get-ManualRunnerSource), (New-Object System.Text.UTF8Encoding($false)))

$serviceRuns = @()
foreach ($serviceDir in $serviceDirs) {
    $serviceRoot = Join-Path $repoRoot $serviceDir
    Write-Host "Compiling and running tests for $serviceDir..."

    Invoke-Gradle $gradlePath @("--no-daemon", "classes", "testClasses") $serviceRoot

    $classpathLines = @()
    Push-Location $serviceRoot
    try {
        $classpathLines = & $gradlePath --no-daemon -q printTestRuntimeClasspath
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to read test runtime classpath for $serviceDir"
        }
    }
    finally {
        Pop-Location
    }

    $runtimeClasspath = ($classpathLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1).Trim()
    if ([string]::IsNullOrWhiteSpace($runtimeClasspath)) {
        throw "Empty test runtime classpath for $serviceDir"
    }

    & "$env:JAVA_HOME\bin\javac.exe" -encoding UTF8 -cp $runtimeClasspath -d $runnerClasspath $runnerSource
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to compile the manual JUnit runner"
    }

    $testClassesDir = Join-Path $serviceRoot "build\classes\java\test"
    if (-not (Test-Path -LiteralPath $testClassesDir)) {
        throw "No compiled test classes found for $serviceDir"
    }

    $serviceKey = ($serviceDir -replace '[^A-Za-z0-9]', '-')
    $stdoutFile = Join-Path $runnerDir "stdout-$serviceKey.txt"
    $stderrFile = Join-Path $runnerDir "stderr-$serviceKey.txt"
    $process = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
        -ArgumentList @("-cp", "$runtimeClasspath;$runnerClasspath", "ManualJUnitRunner", $testClassesDir) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile `
        -Wait `
        -PassThru
    $runOutput = @()
    if (Test-Path -LiteralPath $stdoutFile) {
        $runOutput = Get-Content -LiteralPath $stdoutFile
    }
    $summary = Parse-TestRunSummary $runOutput
    $serviceRuns += [pscustomobject]@{
        Name = Split-Path -Leaf $serviceDir
        Total = $summary.Total
        Passed = $summary.Passed
        Failed = $summary.Failed
        Skipped = $summary.Skipped
    }

    $runOutput | ForEach-Object { Write-Host $_ }
    if ($process.ExitCode -ne 0) {
        Write-TestReport $serviceRuns $ReportPath "FAILED" "Manual JUnit run failed for $serviceDir" $gradlePath
        throw "Manual JUnit run failed for $serviceDir"
    }
}

Write-TestReport $serviceRuns $ReportPath "PASSED" "" $gradlePath
Write-Host "B-side service checks passed."
