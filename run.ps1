param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Chapter
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not (Test-Path "out")) {
    & "$root\build.ps1"
}

& java -cp out $Chapter
exit $LASTEXITCODE
