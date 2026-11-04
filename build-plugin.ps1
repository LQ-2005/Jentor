$ErrorActionPreference = "Stop"

function Get-JavaMajorVersion([string]$JdkPath) {
    if ([string]::IsNullOrWhiteSpace($JdkPath)) {
        return 0
    }
    $javaExe = Join-Path $JdkPath "bin\java.exe"
    $releaseFile = Join-Path $JdkPath "release"
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf) -or
            -not (Test-Path -LiteralPath $releaseFile -PathType Leaf)) {
        return 0
    }
    $versionLine = Get-Content -LiteralPath $releaseFile |
            Select-String '^JAVA_VERSION="(\d+)' |
            Select-Object -First 1
    if ($versionLine) {
        return [int]$versionLine.Matches[0].Groups[1].Value
    }
    return 0
}

function Find-GradleJdk([string]$ConfiguredPath, [string]$CurrentPath, [string]$CompilerPath) {
    # Gradle 8.14.2 cannot run on Java 25. Keep its JVM separate from javac.
    if (-not [string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        $version = Get-JavaMajorVersion $ConfiguredPath
        if ($version -lt 17 -or $version -gt 24) {
            throw "gradleJdkHome must point to Java 17-24 (JDK 21 recommended): $ConfiguredPath"
        }
        return (Resolve-Path -LiteralPath $ConfiguredPath).ProviderPath
    }

    foreach ($candidate in @($CurrentPath, $CompilerPath)) {
        $version = Get-JavaMajorVersion $candidate
        if ($version -ge 17 -and $version -le 24) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }

    $roots = @(
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Amazon Corretto"),
        (Join-Path $env:USERPROFILE ".jdks")
    )
    $installed = foreach ($root in $roots) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            foreach ($directory in Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue) {
                $version = Get-JavaMajorVersion $directory.FullName
                if ($version -ge 17 -and $version -le 24) {
                    [PSCustomObject]@{ Path = $directory.FullName; Version = $version }
                }
            }
        }
    }
    $selected = $installed | Sort-Object `
            @{ Expression = { if ($_.Version -eq 21) { 0 } else { 1 } } }, `
            @{ Expression = { $_.Version }; Descending = $true }, `
            @{ Expression = { $_.Path }; Descending = $true } |
            Select-Object -First 1
    if ($selected) {
        return $selected.Path
    }
    throw "Gradle 8.14.2 needs Java 17-24 to run. Install JDK 21 and set gradleJdkHome in local.properties; keep jdkHome pointing to the plugin compiler JDK."
}

$project = $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
Push-Location -LiteralPath $project
try {
    $configuration = @{}
    $localProperties = Join-Path $project "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        Get-Content -LiteralPath $localProperties | ForEach-Object {
            if ($_ -match '^\s*(jdkHome|gradleJdkHome)\s*=\s*(.+?)\s*$') {
                $configuration[$Matches[1]] = $Matches[2]
            }
        }
    }

    $compilerPath = $configuration['jdkHome']
    if ([string]::IsNullOrWhiteSpace($compilerPath)) {
        $compilerPath = $previousJavaHome
    }
    $javaVersion = Get-JavaMajorVersion $compilerPath
    if ($javaVersion -lt 21) {
        throw "Set jdkHome in local.properties (or JAVA_HOME) to JDK 21, or JDK 25 for IntelliJ IDEA 2026.2."
    }
    $compilerPath = (Resolve-Path -LiteralPath $compilerPath).ProviderPath
    $gradlePath = Find-GradleJdk $configuration['gradleJdkHome'] $previousJavaHome $compilerPath
    $gradleVersion = Get-JavaMajorVersion $gradlePath

    Write-Host "Gradle JVM: Java $gradleVersion ($gradlePath)"
    Write-Host "Compiler toolchain: Java $javaVersion ($compilerPath)"

    $env:JAVA_HOME = $gradlePath
    $gradleArguments = @(
        'buildPlugin', '--offline', '--stacktrace',
        "-Dorg.gradle.java.home=$gradlePath",
        "-PjavaVersion=$javaVersion",
        "-Porg.gradle.java.installations.paths=$compilerPath"
    )
    & (Join-Path $project 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }

    $zip = Join-Path $project "build\distributions\jentor-2.0.0.zip"
    if (Test-Path -LiteralPath $zip) {
        Write-Host ""
        Write-Host "Plugin ZIP: $zip"
        Write-Host "Install with Settings -> Plugins -> Install Plugin from Disk."
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
