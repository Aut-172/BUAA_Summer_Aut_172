param(
    [Parameter(Mandatory = $true)]
    [string]$Module,

    [Parameter(Mandatory = $true)]
    [string]$TestClass
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradleHome = Join-Path $root '.gradle'
$gradleExe = Get-ChildItem -Path (Join-Path $gradleHome 'wrapper\dists\gradle-9.5.1-bin') -Recurse -Filter gradle.bat |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $gradleExe) {
    throw 'Cannot find local Gradle distribution under .gradle/wrapper/dists/gradle-9.5.1-bin'
}

Set-Location $root

$runtimeClasspath = (& $gradleExe -q ":$Module:printTestRuntimeClasspath" --no-daemon | Out-String).Trim()

$tmpDir = Join-Path $root '.tmp-junit-runner'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$source = @'
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionSummary;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public class SingleJUnitRunner {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Missing test class name");
            System.exit(2);
        }

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(args[0]))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        if (summary.getTotalFailureCount() > 0) {
            System.exit(1);
        }
    }
}
'@

$sourcePath = Join-Path $tmpDir 'SingleJUnitRunner.java'
Set-Content -Path $sourcePath -Value $source -Encoding UTF8

$compileClasspath = $runtimeClasspath
& javac -encoding UTF8 -cp $compileClasspath -d $tmpDir $sourcePath

$runClasspath = "$runtimeClasspath;$tmpDir"
& java -cp $runClasspath SingleJUnitRunner $TestClass
