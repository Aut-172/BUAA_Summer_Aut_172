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

$runnerDir = Join-Path $repoRoot ".manual-junit-runner"
$runnerSource = Join-Path $runnerDir "ManualJUnitRunner.java"
$runnerClasspath = Join-Path $runnerDir "classes"

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
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

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

$gradlePath = Resolve-GradlePath $GradlePath

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-home-d"
$env:TEMP = Join-Path $repoRoot ".tmp-d"
$env:TMP = $env:TEMP

New-Item -ItemType Directory -Force -Path $runnerDir, $runnerClasspath, $env:GRADLE_USER_HOME, $env:TEMP | Out-Null
[System.IO.File]::WriteAllText($runnerSource, (Get-ManualRunnerSource), (New-Object System.Text.UTF8Encoding($false)))

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
        Write-Host "No compiled test classes found for $serviceDir, skipping execution."
        continue
    }

    & "$env:JAVA_HOME\bin\java.exe" -cp "$runtimeClasspath;$runnerClasspath" ManualJUnitRunner $testClassesDir
    if ($LASTEXITCODE -ne 0) {
        throw "Manual JUnit run failed for $serviceDir"
    }
}

Write-Host "Microservice checks passed."
