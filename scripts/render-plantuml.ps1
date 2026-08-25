param(
    [string]$MarkdownPath = "frontend/参考文档/参考文档/软件详细设计说明书.md",
    [string]$OutputDir,
    [string]$PlantUmlJar,
    [string]$PlantUmlCommand = "plantuml",
    [ValidateSet("png", "svg")]
    [string]$Format = "png",
    [switch]$SkipMarkdownUpdate
)

$ErrorActionPreference = "Stop"

function Resolve-ExistingPath([string]$PathValue) {
    $item = Get-Item -LiteralPath $PathValue -ErrorAction Stop
    return $item.FullName
}

function ConvertTo-SafeFileName([string]$Value) {
    $safe = $Value -replace '[\\/:*?"<>|]', '-'
    $safe = $safe -replace '\s+', '-'
    $safe = $safe.Trim(' ', '.', '-')
    if ([string]::IsNullOrWhiteSpace($safe)) {
        return "diagram"
    }
    return $safe
}

function ConvertTo-MarkdownPath([string]$FromDirectory, [string]$TargetPath) {
    $fromPath = (Resolve-Path -LiteralPath $FromDirectory).Path.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar))
    $fromUri = [Uri]($fromPath + [IO.Path]::DirectorySeparatorChar)
    $targetUri = [Uri](Resolve-Path -LiteralPath $TargetPath).Path
    return [Uri]::UnescapeDataString($fromUri.MakeRelativeUri($targetUri).ToString())
}

function Get-NearestHeading([string]$TextBeforeBlock) {
    $headingMatches = [regex]::Matches($TextBeforeBlock, '(?m)^#{2,6}\s+(.+?)\s*$')
    if ($headingMatches.Count -eq 0) {
        return "PlantUML 图"
    }
    return $headingMatches[$headingMatches.Count - 1].Groups[1].Value.Trim()
}

$markdownFullPath = Resolve-ExistingPath $MarkdownPath
$markdownDir = Split-Path -Parent $markdownFullPath
$markdownBaseName = [IO.Path]::GetFileNameWithoutExtension($markdownFullPath)

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $markdownDir ("{0}.assets\plantuml" -f $markdownBaseName)
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$outputFullPath = (Resolve-Path -LiteralPath $OutputDir).Path

$content = Get-Content -LiteralPath $markdownFullPath -Raw -Encoding UTF8
$blockPattern = [regex]'(?ms)```plantuml\s*\r?\n(?<code>.*?)\r?\n```'
$matches = $blockPattern.Matches($content)

if ($matches.Count -eq 0) {
    Write-Host "No plantuml code blocks found in $markdownFullPath"
    exit 0
}

$renderer = $null
if (-not [string]::IsNullOrWhiteSpace($PlantUmlJar)) {
    $plantUmlJarFullPath = Resolve-ExistingPath $PlantUmlJar
    $renderer = "jar"
} else {
    $commandInfo = Get-Command $PlantUmlCommand -ErrorAction SilentlyContinue
    if ($commandInfo) {
        $renderer = "command"
        $PlantUmlCommand = $commandInfo.Source
    }
}

if (-not $renderer) {
    Write-Error @"
PlantUML renderer not found.

Install one of the following, then rerun this script:
  1. choco install plantuml
  2. scoop install plantuml
  3. Download plantuml.jar and run:
     .\scripts\render-plantuml.ps1 -PlantUmlJar C:\path\to\plantuml.jar
"@
}

$diagrams = @()
$index = 0
foreach ($match in $matches) {
    $index++
    $heading = Get-NearestHeading $content.Substring(0, $match.Index)
    $stem = "{0:D2}-{1}" -f $index, (ConvertTo-SafeFileName $heading)
    $pumlPath = Join-Path $outputFullPath "$stem.puml"
    $imagePath = Join-Path $outputFullPath "$stem.$Format"

    Set-Content -LiteralPath $pumlPath -Value $match.Groups['code'].Value -Encoding UTF8

    Write-Host ("Rendering {0:D2}/{1}: {2}" -f $index, $matches.Count, $heading)

    if ($renderer -eq "jar") {
        & java "-Dfile.encoding=UTF-8" -jar $plantUmlJarFullPath "-t$Format" $pumlPath
    } else {
        & $PlantUmlCommand "-t$Format" $pumlPath
    }

    if ($LASTEXITCODE -ne 0) {
        throw "PlantUML failed while rendering $pumlPath"
    }
    if (-not (Test-Path -LiteralPath $imagePath)) {
        throw "Expected image was not generated: $imagePath"
    }

    $diagrams += [pscustomobject]@{
        Index = $index
        Heading = $heading
        Marker = "plantuml-image: $stem"
        ImagePath = $imagePath
        MarkdownImagePath = (ConvertTo-MarkdownPath $markdownDir $imagePath).Replace('\\', '/')
    }
}

if (-not $SkipMarkdownUpdate) {
    $diagramQueue = [System.Collections.Queue]::new()
    foreach ($diagram in $diagrams) {
        $diagramQueue.Enqueue($diagram)
    }

    $updatePattern = [regex]'(?ms)(?<block>```plantuml\s*\r?\n.*?\r?\n```)(?<existing>\r?\n\s*<!--\s*plantuml-image:\s*.*?\s*-->\r?\n\s*!\[.*?\]\([^\r\n]+\))?'
    $updated = $updatePattern.Replace($content, {
        param($m)
        $diagram = $diagramQueue.Dequeue()
        $imageMarkdown = "<!-- $($diagram.Marker) -->`r`n![$($diagram.Heading)]($($diagram.MarkdownImagePath))"
        return "$($m.Groups['block'].Value)`r`n$imageMarkdown"
    })

    Set-Content -LiteralPath $markdownFullPath -Value $updated -Encoding UTF8
}

Write-Host "Rendered $($diagrams.Count) PlantUML diagram(s)."
foreach ($diagram in $diagrams) {
    Write-Host ("{0:D2}: {1} -> {2}" -f $diagram.Index, $diagram.Heading, $diagram.MarkdownImagePath)
}
