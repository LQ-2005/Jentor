$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
Push-Location -LiteralPath $project
try {
    & (Join-Path $project 'gradlew.bat') --no-daemon --offline clean run
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
