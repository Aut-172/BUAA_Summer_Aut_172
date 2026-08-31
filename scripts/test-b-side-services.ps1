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

$LogPath = Join-Path $repoRoot "reports/testing/b-side-test-log.txt"
. (Join-Path $PSScriptRoot "test-report-metadata.ps1")

function Get-CommandOutputLine {
    param([scriptblock]$Command)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $Command 2>&1
        $line = $output |
            ForEach-Object { [string]$_ } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -First 1
        return $line -as [string]
    } catch {
        return "unavailable: $($_.Exception.Message)"
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Escape-MarkdownCell {
    param([object]$Value)

    if ($null -eq $Value) {
        return "-"
    }

    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return "-"
    }

    $text = $text -replace '\r?\n', '<br>'
    $text = $text -replace '\|', '\|'
    return $text
}

function Parse-TestCases {
    param([string[]]$OutputLines)

    $cases = @()
    foreach ($line in $OutputLines) {
        if ($line -match '^TEST_CASE\s+(\{.*\})$') {
            try {
                $cases += ($Matches[1] | ConvertFrom-Json)
            } catch {
                continue
            }
        }
    }

    return @($cases)
}

function Get-CaseSummary {
    param([object[]]$Cases)

    $caseList = @($Cases)
    return [pscustomobject]@{
        Total = $caseList.Count
        Passed = @($caseList | Where-Object { $_.Status -eq 'PASSED' }).Count
        Failed = @($caseList | Where-Object { $_.Status -eq 'FAILED' }).Count
        Skipped = @($caseList | Where-Object { $_.Status -in @('SKIPPED', 'ABORTED') }).Count
    }
}

function Get-CaseId {
    param(
        [object]$Case,
        [string]$ServiceName
    )

    $classShort = if (-not [string]::IsNullOrWhiteSpace($Case.ClassName)) {
        ($Case.ClassName -split '\.')[-1]
    } else {
        "Case"
    }

    if (-not [string]::IsNullOrWhiteSpace($Case.MethodName)) {
        return "$ServiceName.$classShort.$($Case.MethodName)"
    }

    return "$ServiceName.$classShort"
}

function Format-CaseLogLine {
    param([object]$Case)

    $caseId = Get-CaseId $Case $Case.Service
    $status = if ([string]::IsNullOrWhiteSpace($Case.Status)) { "-" } else { $Case.Status }
    $duration = if ($null -ne $Case.DurationMs) { [math]::Round([double]$Case.DurationMs, 2) } else { 0 }
    return "[$status] $caseId $duration ms"
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

    $allCases = @()
    foreach ($service in $ServiceResults) {
        $allCases += @($service.Cases | ForEach-Object {
            $_ | Add-Member -NotePropertyName Service -NotePropertyValue $service.Name -Force -PassThru
        })
    }
    $allCases = @($allCases | Sort-Object Service, ClassName, MethodName, DisplayName)
    $summary = Get-CaseSummary $allCases

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
    foreach ($service in $ServiceResults) {
        $lines += "| $(Escape-MarkdownCell $service.Name) | $($service.Total) | $($service.Passed) | $($service.Failed) | $($service.Skipped) |"
    }
    $lines += ""
    $lines += "## Case Details"
    $lines += ""
    $lines += "| Case ID | Interface Name | Scenario | Method | URL | Request | Expected Result | Actual Result | Actual Response / Key Assertions | Test Conclusion | Failure Reason | Logs/Screenshots |"
    $lines += "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"
    foreach ($case in $allCases) {
        $caseId = Get-CaseId $case $case.Service
        $row = Get-TestCaseReportRow $case $LogPath
        $lines += "| $(Escape-MarkdownCell $caseId) | $(Escape-MarkdownCell $row.InterfaceName) | $(Escape-MarkdownCell $row.Scenario) | $(Escape-MarkdownCell $row.Method) | $(Escape-MarkdownCell $row.Url) | $(Escape-MarkdownCell $row.Request) | $(Escape-MarkdownCell $row.Expected) | $(Escape-MarkdownCell $row.Actual) | $(Escape-MarkdownCell $row.Assertions) | $(Escape-MarkdownCell $row.Conclusion) | $(Escape-MarkdownCell $row.Reason) | $(Escape-MarkdownCell $row.Evidence) |"
    }
    $lines += ""
    $lines += "## Failure Reasons"
    $lines += ""
    $failures = @($allCases | Where-Object { $_.Status -eq 'FAILED' })
    if ($failures.Count -gt 0) {
        foreach ($failure in $failures) {
            $caseName = if (-not [string]::IsNullOrWhiteSpace($failure.MethodName)) { "$($failure.ClassName)#$($failure.MethodName)" } else { $failure.DisplayName }
            $reason = if (-not [string]::IsNullOrWhiteSpace($failure.Message)) { $failure.Message } else { $FailureMessage }
            $lines += "- $($failure.Service) / $caseName : $reason"
        }
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
    $lines += "| Services | $($ServiceResults.Name -join ', ') |"
    $lines += "| Test Cases | $($allCases.Count) |"
    $lines += ""
    $lines += "## Log File"
    $lines += ""
    $lines += "[$LogPath]($LogPath)"

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class ManualJUnitRunner {
    private static final class DetailedListener implements TestExecutionListener {
        private final String serviceName;
        private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

        private DetailedListener(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            // no-op
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (testIdentifier.isTest()) {
                startTimes.put(testIdentifier.getUniqueId(), System.nanoTime());
            }
        }

        @Override
        public void executionSkipped(TestIdentifier testIdentifier, String reason) {
            if (testIdentifier.isTest()) {
                emit(testIdentifier, "SKIPPED", reason, 0L);
            }
        }

        @Override
        public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
            if (!testIdentifier.isTest()) {
                return;
            }

            String status;
            String message = null;
            switch (testExecutionResult.getStatus()) {
                case SUCCESSFUL -> status = "PASSED";
                case ABORTED -> {
                    status = "SKIPPED";
                    message = testExecutionResult.getThrowable().map(Throwable::getMessage).orElse(null);
                }
                case FAILED -> {
                    status = "FAILED";
                    message = testExecutionResult.getThrowable()
                            .map(throwable -> throwable.getClass().getName() + ": " + throwable.getMessage())
                            .orElse(null);
                }
                default -> {
                    status = "SKIPPED";
                    message = testExecutionResult.getThrowable().map(Throwable::getMessage).orElse(null);
                }
            }

            long durationNs = 0L;
            Long startedAt = startTimes.remove(testIdentifier.getUniqueId());
            if (startedAt != null) {
                durationNs = System.nanoTime() - startedAt;
            }
            emit(testIdentifier, status, message, durationNs);
        }

        private void emit(TestIdentifier testIdentifier, String status, String message, long durationNs) {
            String className = "";
            String methodName = "";
            String sourceType = "";
            if (testIdentifier.getSource().isPresent()) {
                Object source = testIdentifier.getSource().get();
                if (source instanceof MethodSource methodSource) {
                    className = methodSource.getClassName();
                    methodName = methodSource.getMethodName();
                    sourceType = "METHOD";
                } else if (source instanceof ClassSource classSource) {
                    className = classSource.getClassName();
                    sourceType = "CLASS";
                } else {
                    sourceType = source.getClass().getSimpleName();
                }
            }

            double durationMs = durationNs / 1_000_000.0d;
            System.out.println(
                    "TEST_CASE "
                            + jsonObject(
                                    "service", serviceName,
                                    "uniqueId", testIdentifier.getUniqueId(),
                                    "displayName", testIdentifier.getDisplayName(),
                                    "className", className,
                                    "methodName", methodName,
                                    "sourceType", sourceType,
                                    "status", status,
                                    "durationMs", Double.toString(durationMs),
                                    "message", message == null ? "" : message));
        }

        private String jsonObject(String... keyValues) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            for (int i = 0; i < keyValues.length; i += 2) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append('"').append(escapeJson(keyValues[i])).append('"').append(':');
                builder.append('"').append(escapeJson(keyValues[i + 1])).append('"');
            }
            builder.append('}');
            return builder.toString();
        }

        private String escapeJson(String value) {
            if (value == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '\\' -> builder.append("\\\\");
                    case '"' -> builder.append("\\\"");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            builder.append(String.format("\\u%04x", (int) ch));
                        } else {
                            builder.append(ch);
                        }
                    }
                }
            }
            return builder.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ManualJUnitRunner <service-name> <test-classes-dir>");
        }

        String serviceName = args[0];
        Path testClassesDir = Paths.get(args[1]);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(testClassesDir)))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        DetailedListener detailedListener = new DetailedListener(serviceName);
        launcher.registerTestExecutionListeners(listener);
        launcher.registerTestExecutionListeners(detailedListener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
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
$logLines = New-Object System.Collections.Generic.List[string]
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
        -ArgumentList @("-cp", "$runtimeClasspath;$runnerClasspath", "ManualJUnitRunner", (Split-Path -Leaf $serviceDir), $testClassesDir) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile `
        -Wait `
        -PassThru
    $runOutput = @()
    if (Test-Path -LiteralPath $stdoutFile) {
        $runOutput = Get-Content -LiteralPath $stdoutFile
    }
    $caseDetails = Parse-TestCases $runOutput
    $summary = Get-CaseSummary $caseDetails
    $serviceRuns += [pscustomobject]@{
        Name = Split-Path -Leaf $serviceDir
        Cases = $caseDetails
        Total = $summary.Total
        Passed = $summary.Passed
        Failed = $summary.Failed
        Skipped = $summary.Skipped
    }

    foreach ($case in $caseDetails) {
        $logLines.Add((Format-CaseLogLine $case)) | Out-Null
        Write-Host ("[{0}] {1} {2} ({3:n0} ms)" -f $case.Status, $case.ClassName, $case.MethodName, [double]$case.DurationMs)
    }
    if ($process.ExitCode -ne 0) {
        Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
        Write-TestReport $serviceRuns $ReportPath "FAILED" "Manual JUnit run failed for $serviceDir" $gradlePath
        throw "Manual JUnit run failed for $serviceDir"
    }
}

Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
Write-TestReport $serviceRuns $ReportPath "PASSED" "" $gradlePath
Write-Host "B-side service checks passed."
