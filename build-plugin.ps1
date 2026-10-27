$ErrorActionPreference = "Stop"

$project = $PSScriptRoot
Set-Location $project

$localProperties = Join-Path $project "local.properties"
if (Test-Path -LiteralPath $localProperties) {
    Get-Content -LiteralPath $localProperties | ForEach-Object {
        if ($_ -match "^\s*jdkHome\s*=\s*(.+?)\s*$") {
            $env:JAVA_HOME = $Matches[1]
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw "A JDK was not found. Set jdkHome in local.properties or set JAVA_HOME."
}

# The compiler toolchain must be able to read the IntelliJ jars it compiles
# against: 2025.2-2026.1 ship Java 21 class files, 2026.2 ships Java 25 ones.
$javaVersion = "21"
$releaseFile = Join-Path $env:JAVA_HOME "release"
if (Test-Path -LiteralPath $releaseFile) {
    $match = Get-Content -LiteralPath $releaseFile |
            Select-String '^JAVA_VERSION="(\d+)' |
            Select-Object -First 1
    if ($match) {
        $javaVersion = $match.Matches[0].Groups[1].Value
    }
}

Write-Host "Toolchain: Java $javaVersion ($env:JAVA_HOME)"

.\gradlew.bat buildPlugin --offline --stacktrace "-PjavaVersion=$javaVersion"
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$zip = Join-Path $project "build\distributions\cst3492-course-ta-0.5.0.zip"
if (Test-Path -LiteralPath $zip) {
    Write-Host ""
    Write-Host "Plugin ZIP: $zip"
    Write-Host "Install with Settings -> Plugins -> Install Plugin from Disk."
}
