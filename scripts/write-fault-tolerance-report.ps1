param(
    [string]$ProbeRoot,
    [string]$ObservationDir,
    [string]$OutputPath,
    [string]$ExperimentName = "Cloud Native Fault Tolerance Experiment",
    [string]$ProtectedService = "merchant-service",
    [string]$DependencyService = "order-service",
    [string]$FaultMode = "scale-zero",
    [string]$GatewayUrl = "http://47.120.37.61:30081"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($ProbeRoot)) {
    $ProbeRoot = Join-Path $repoRoot "reports\fault"
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repoRoot "reports\fault\fault-tolerance-experiment-report.md"
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

function Format-Number {
    param([double]$Value)
    return [math]::Round($Value, 2)
}

$probeFiles = @()
if (Test-Path -LiteralPath $ProbeRoot) {
    $probeFiles = @(Get-ChildItem -LiteralPath $ProbeRoot -Recurse -Filter "probe-results.csv" -File)
}

$probeRows = New-Object System.Collections.Generic.List[object]
foreach ($file in $probeFiles) {
    foreach ($row in (Import-Csv -LiteralPath $file.FullName)) {
        $row | Add-Member -NotePropertyName SourceFile -NotePropertyValue $file.FullName -Force
        $probeRows.Add($row) | Out-Null
    }
}

$deploymentRows = @()
if (-not [string]::IsNullOrWhiteSpace($ObservationDir)) {
    $deploymentCsv = Join-Path $ObservationDir "deployments.csv"
    if (Test-Path -LiteralPath $deploymentCsv) {
        $deploymentRows = @(Import-Csv -LiteralPath $deploymentCsv)
    }
}

$total = $probeRows.Count
$passed = @($probeRows | Where-Object { $_.passed -eq "True" -or $_.passed -eq "true" }).Count
$failed = $total - $passed
$dashboardRows = @($probeRows | Where-Object { $_.name -eq "merchant-dashboard" })
$faultDashboardRows = @($dashboardRows | Where-Object { $_.phase -eq "fault" })
$degradedRows = @($dashboardRows | Where-Object { $_.degraded -eq "True" -or $_.degraded -eq "true" })
$avgDashboard = if ($dashboardRows.Count -gt 0) { Format-Number (($dashboardRows | Measure-Object -Property elapsed_ms -Average).Average) } else { 0 }
$maxDashboard = if ($dashboardRows.Count -gt 0) { Format-Number (($dashboardRows | Measure-Object -Property elapsed_ms -Maximum).Maximum) } else { 0 }

$lines = @()
$lines += "# $ExperimentName"
$lines += ""
$lines += "## Overview"
$lines += ""
$lines += "This experiment stops a dependency service and verifies that the protected service returns a designed fallback response while unrelated APIs remain available."
$lines += ""
$lines += "| Item | Value |"
$lines += "| --- | --- |"
$lines += "| Generated At | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') |"
$lines += "| Protected Service | $ProtectedService |"
$lines += "| Dependency Service | $DependencyService |"
$lines += "| Fault Mode | $FaultMode |"
$lines += "| Gateway URL | $GatewayUrl |"
$lines += "| Probe Result Files | $($probeFiles.Count) |"
$lines += ""
$lines += "## Key Metrics"
$lines += ""
$lines += "| Metric | Value |"
$lines += "| --- | ---: |"
$lines += "| Total Probes | $total |"
$lines += "| Passed | $passed |"
$lines += "| Failed | $failed |"
$lines += "| Dashboard Probes | $($dashboardRows.Count) |"
$lines += "| Fault-Phase Dashboard Probes | $($faultDashboardRows.Count) |"
$lines += "| Dashboard Fallback Count | $($degradedRows.Count) |"
$lines += "| Dashboard Average Latency ms | $avgDashboard |"
$lines += "| Dashboard Maximum Latency ms | $maxDashboard |"
$lines += ""
$lines += "## Phase Results"
$lines += ""
$lines += "| Phase | API | Count | Passed | Failed | Average Latency ms | Fallback Count |"
$lines += "| --- | --- | ---: | ---: | ---: | ---: | ---: |"
foreach ($group in ($probeRows | Group-Object phase, name | Sort-Object Name)) {
    $rows = @($group.Group)
    $phase = $rows[0].phase
    $name = $rows[0].name
    $groupPassed = @($rows | Where-Object { $_.passed -eq "True" -or $_.passed -eq "true" }).Count
    $groupFailed = $rows.Count - $groupPassed
    $avg = if ($rows.Count -gt 0) { Format-Number (($rows | Measure-Object -Property elapsed_ms -Average).Average) } else { 0 }
    $groupDegraded = @($rows | Where-Object { $_.degraded -eq "True" -or $_.degraded -eq "true" }).Count
    $lines += "| $(Escape-MarkdownCell $phase) | $(Escape-MarkdownCell $name) | $($rows.Count) | $groupPassed | $groupFailed | $avg | $groupDegraded |"
}
$lines += ""
$lines += "## Kubernetes Observation"
$lines += ""
if ($deploymentRows.Count -eq 0) {
    $lines += "No Kubernetes observation directory was provided, or deployments.csv was not found."
} else {
    $lines += "| Deployment | Samples | Minimum Ready Replicas | Maximum Ready Replicas | Minimum Desired Replicas | Maximum Desired Replicas |"
    $lines += "| --- | ---: | ---: | ---: | ---: | ---: |"
    foreach ($group in ($deploymentRows | Group-Object deployment | Sort-Object Name)) {
        $rows = @($group.Group)
        $readyValues = @($rows | ForEach-Object { if ($_.ready_replicas -match '^\d+$') { [int]$_.ready_replicas } })
        $desiredValues = @($rows | ForEach-Object { if ($_.desired_replicas -match '^\d+$') { [int]$_.desired_replicas } })
        $minReady = if ($readyValues.Count -gt 0) { ($readyValues | Measure-Object -Minimum).Minimum } else { 0 }
        $maxReady = if ($readyValues.Count -gt 0) { ($readyValues | Measure-Object -Maximum).Maximum } else { 0 }
        $minDesired = if ($desiredValues.Count -gt 0) { ($desiredValues | Measure-Object -Minimum).Minimum } else { 0 }
        $maxDesired = if ($desiredValues.Count -gt 0) { ($desiredValues | Measure-Object -Maximum).Maximum } else { 0 }
        $lines += "| $(Escape-MarkdownCell $group.Name) | $($rows.Count) | $minReady | $maxReady | $minDesired | $maxDesired |"
    }
}
$lines += ""
$lines += "## Conclusion"
$lines += ""
if ($failed -eq 0 -and $faultDashboardRows.Count -gt 0 -and $degradedRows.Count -gt 0) {
    $lines += "During the fault phase, the dashboard API returned the designed fallback response and isolation probes stayed available. The experiment target was met."
} elseif ($total -eq 0) {
    $lines += "No probe result files were found. Run baseline, fault, and recovery probes first."
} else {
    $lines += "Probe files were collected, but failures remain or fallback samples are insufficient. Check the phase results and Kubernetes observation files."
}
$lines += ""
$lines += "## Data Sources"
$lines += ""
if ($probeFiles.Count -eq 0) {
    $lines += "- No probe-results.csv files found"
} else {
    foreach ($file in $probeFiles) {
        $lines += "- $($file.FullName)"
    }
}
if (-not [string]::IsNullOrWhiteSpace($ObservationDir)) {
    $lines += "- Kubernetes observation: $ObservationDir"
}

$outputDir = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Set-Content -LiteralPath $OutputPath -Value $lines -Encoding UTF8
Write-Host "Fault tolerance report: $OutputPath"
