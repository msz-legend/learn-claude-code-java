param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if ($Clean -and (Test-Path "out")) {
    Remove-Item -Recurse -Force "out"
}
New-Item -ItemType Directory -Force "out" | Out-Null

$files = Get-ChildItem -Recurse -Filter *.java |
    Where-Object { $_.FullName -notmatch '\\out\\' } |
    ForEach-Object { $_.FullName }

if ($files.Count -eq 0) {
    Write-Error "No Java sources found."
}

& javac -encoding UTF-8 -d out @files
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Compiled $($files.Count) Java files to out/"
Write-Host "Try: java -cp out harness.SelfTest"
