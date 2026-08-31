param(
    [string]$GradlePath,
    [string]$ReportPath,
    [string[]]$ServiceDirs,
    [string]$ReportTitle = "Microservice Test Report",
    [string]$LogPath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$defaultServiceDirs = @(
    "services/api-gateway",
    "services/merchant-service",
    "services/user-service",
    "services/order-service",
    "services/settlement-service",
    "services/fulfillment-service",
    "services/engagement-service"
)

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot "reports/testing/microservice-test-report.md"
}

if ($null -eq $ServiceDirs -or $ServiceDirs.Count -eq 0) {
    $ServiceDirs = $defaultServiceDirs
}

if ([string]::IsNullOrWhiteSpace($LogPath)) {
    $LogPath = Join-Path $repoRoot "reports/testing/microservice-test-log.txt"
}
. (Join-Path $PSScriptRoot "test-report-metadata.ps1")

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

function Parse-GradleTestResults {
    param(
        [string]$ServiceName,
        [string]$ServiceRoot,
        [string]$EvidencePath
    )

    $resultsDir = Join-Path $ServiceRoot "build/test-results/test"
    if (-not (Test-Path -LiteralPath $resultsDir)) {
        return @()
    }

    $cases = @()
    $xmlFiles = @(Get-ChildItem -LiteralPath $resultsDir -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
    foreach ($xmlFile in $xmlFiles) {
        try {
            [xml]$document = Get-Content -LiteralPath $xmlFile.FullName -Raw
        } catch {
            continue
        }

        $suiteName = if ($document.testsuite -and $document.testsuite.name) {
            [string]$document.testsuite.name
        } else {
            [System.IO.Path]::GetFileNameWithoutExtension($xmlFile.Name) -replace '^TEST-', ''
        }

        foreach ($testCase in @($document.SelectNodes("//testcase"))) {
            $className = if (-not [string]::IsNullOrWhiteSpace($testCase.classname)) {
                [string]$testCase.classname
            } else {
                $suiteName
            }
            $methodName = [string]$testCase.name
            if ($methodName -match '^([A-Za-z0-9_$]+)\(.*\)$') {
                $methodName = $Matches[1]
            }
            $durationMs = 0
            if (-not [string]::IsNullOrWhiteSpace($testCase.time)) {
                $durationMs = [double]$testCase.time * 1000
            }

            $status = "PASSED"
            $message = ""
            if ($testCase.failure) {
                $status = "FAILED"
                $message = if (-not [string]::IsNullOrWhiteSpace($testCase.failure.message)) {
                    [string]$testCase.failure.message
                } else {
                    [string]$testCase.failure.InnerText
                }
            } elseif ($testCase.error) {
                $status = "FAILED"
                $message = if (-not [string]::IsNullOrWhiteSpace($testCase.error.message)) {
                    [string]$testCase.error.message
                } else {
                    [string]$testCase.error.InnerText
                }
            } elseif ($testCase.skipped) {
                $status = "SKIPPED"
                $message = if (-not [string]::IsNullOrWhiteSpace($testCase.skipped.message)) {
                    [string]$testCase.skipped.message
                } else {
                    [string]$testCase.skipped.InnerText
                }
            }

            $cases += [pscustomobject]@{
                Service = $ServiceName
                UniqueId = "$ServiceName.$className.$methodName"
                DisplayName = $methodName
                ClassName = $className
                MethodName = $methodName
                SourceType = "GRADLE_XML"
                Status = $status
                DurationMs = $durationMs
                Message = $message
                Evidence = "$EvidencePath; $($xmlFile.FullName)"
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
    $lines += "# $ReportTitle"
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

$runnerDir = Join-Path $repoRoot ".manual-junit-runner"
$runnerSource = Join-Path $runnerDir "ManualJUnitRunner.java"
$runnerClasspath = Join-Path $runnerDir "classes"

function Resolve-GradlePath {
    param(
        [string]$ExplicitGradlePath,
        [string]$ServiceDir
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitGradlePath)) {
        return (Resolve-Path -LiteralPath $ExplicitGradlePath).Path
    }

    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return $gradle.Source
    }

    $zipPath = Join-Path $repoRoot "gradle-9.5.1-bin.zip"
    if (Test-Path -LiteralPath $zipPath) {
        $runtimeDir = Join-Path $env:TEMP "codex-gradle-9.5.1"
        $gradleExecutableName = if ($IsWindows -or $env:OS -eq "Windows_NT") { "gradle.bat" } else { "gradle" }
        $gradleExecutable = Join-Path $runtimeDir "gradle-9.5.1/bin/$gradleExecutableName"
        if (-not (Test-Path -LiteralPath $gradleExecutable)) {
            New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
            Expand-Archive -LiteralPath $zipPath -DestinationPath $runtimeDir -Force
        }
        if (-not ($IsWindows -or $env:OS -eq "Windows_NT")) {
            & chmod +x $gradleExecutable
        }
        return $gradleExecutable
    }

    if (-not [string]::IsNullOrWhiteSpace($ServiceDir)) {
        $wrapperName = if ($IsWindows -or $env:OS -eq "Windows_NT") { "gradlew.bat" } else { "gradlew" }
        $wrapperPath = Join-Path (Join-Path $repoRoot $ServiceDir) $wrapperName
        if (Test-Path -LiteralPath $wrapperPath) {
            return (Resolve-Path -LiteralPath $wrapperPath).Path
        }
    }

    throw "Gradle is not installed, local gradle-9.5.1-bin.zip was not found, and no service Gradle wrapper was found."
}

function Resolve-JavaTool {
    param([string]$ToolName)

    $extension = if ($IsWindows -or $env:OS -eq "Windows_NT") { ".exe" } else { "" }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHomeTool = Join-Path $env:JAVA_HOME "bin/$ToolName$extension"
        if (Test-Path -LiteralPath $javaHomeTool) {
            return (Resolve-Path -LiteralPath $javaHomeTool).Path
        }
    }

    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Cannot find $ToolName. Install JDK 21 or set JAVA_HOME."
}

function Use-Jdk21IfAvailable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME) -and $env:JAVA_HOME -match '(^|[\\/])jdk-?21|(^|[\\/])java-?21|21\.[0-9]') {
        return
    }

    $candidateHomes = @(
        $env:JAVA_HOME_21_X64,
        "C:\Program Files\Java\jdk-21.0.12.1",
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.7-hotspot",
        "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"
    )

    foreach ($candidateHome in $candidateHomes) {
        if ([string]::IsNullOrWhiteSpace($candidateHome)) {
            continue
        }

        $javaExecutableName = if ($IsWindows -or $env:OS -eq "Windows_NT") { "java.exe" } else { "java" }
        $javaPath = Join-Path $candidateHome "bin/$javaExecutableName"
        if (Test-Path -LiteralPath $javaPath) {
            $env:JAVA_HOME = $candidateHome
            $env:PATH = "$(Join-Path $candidateHome 'bin')$([System.IO.Path]::PathSeparator)$env:PATH"
            return
        }
    }
}

function Get-ManualRunnerSource {
    @'
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

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

$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-home-d"
$env:TEMP = Join-Path $repoRoot ".tmp-d"
$env:TMP = $env:TEMP
Use-Jdk21IfAvailable

New-Item -ItemType Directory -Force -Path $runnerDir, $runnerClasspath, $env:GRADLE_USER_HOME, $env:TEMP | Out-Null

$serviceRuns = @()
$logLines = New-Object System.Collections.Generic.List[string]
foreach ($serviceDir in $ServiceDirs) {
    $serviceRoot = Join-Path $repoRoot $serviceDir
    $gradlePath = Resolve-GradlePath $GradlePath $serviceDir
    Write-Host "Compiling and running tests for $serviceDir..."

    $serviceName = Split-Path -Leaf $serviceDir
    $testResultsDir = Join-Path $serviceRoot "build/test-results/test"
    if (Test-Path -LiteralPath $testResultsDir) {
        Remove-Item -LiteralPath $testResultsDir -Recurse -Force
    }

    $serviceKey = ($serviceDir -replace '[^A-Za-z0-9]', '-')
    $stdoutFile = Join-Path $runnerDir "stdout-$serviceKey.txt"
    $stderrFile = Join-Path $runnerDir "stderr-$serviceKey.txt"

    $process = Start-Process -FilePath $gradlePath `
        -ArgumentList @("--no-daemon", "test") `
        -WorkingDirectory $serviceRoot `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile `
        -Wait `
        -PassThru
    $gradleExitCode = $process.ExitCode

    $runOutput = @()
    if (Test-Path -LiteralPath $stdoutFile) {
        $runOutput = Get-Content -LiteralPath $stdoutFile
    }
    $runErrors = @()
    if (Test-Path -LiteralPath $stderrFile) {
        $runErrors = Get-Content -LiteralPath $stderrFile
    }
    $caseDetails = Parse-GradleTestResults $serviceName $serviceRoot $LogPath
    $summary = Get-CaseSummary $caseDetails
    $serviceRuns += [pscustomobject]@{
        Name = $serviceName
        Cases = $caseDetails
        Total = $summary.Total
        Passed = $summary.Passed
        Failed = $summary.Failed
        Skipped = $summary.Skipped
    }

    foreach ($line in $runOutput) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            Write-Host $line
        }
    }
    foreach ($line in $runErrors) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $logLines.Add("[STDERR] $serviceName $line") | Out-Null
            Write-Host "[STDERR] $serviceName $line"
        }
    }

    foreach ($case in $caseDetails) {
        $logLines.Add((Format-CaseLogLine $case)) | Out-Null
        Write-Host ("[{0}] {1} {2} ({3:n0} ms)" -f $case.Status, $case.ClassName, $case.MethodName, [double]$case.DurationMs)
    }

    if ($gradleExitCode -ne 0) {
        Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
        Write-TestReport $serviceRuns $ReportPath "FAILED" "Gradle test failed for $serviceDir" $gradlePath
        throw "Gradle test failed for $serviceDir"
    }
}

Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
Write-TestReport $serviceRuns $ReportPath "PASSED" "" $gradlePath
Write-Host "Microservice checks passed."
