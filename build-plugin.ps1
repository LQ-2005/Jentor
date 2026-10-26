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
    throw "JDK 21 was not found. Set jdkHome in local.properties or set JAVA_HOME."
}

.\gradlew.bat buildPlugin --offline --stacktrace

$zip = Join-Path $project "build\distributions\cst3492-course-ta-0.4.3.zip"
if (Test-Path -LiteralPath $zip) {
    Write-Host ""
    Write-Host "Plugin ZIP: $zip"
    Write-Host "Install with Settings -> Plugins -> Install Plugin from Disk."
}
