param([string]$JavaHome='C:/Program Files/Java/jdk-21')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME=$JavaHome
$logs=Join-Path $repo 'verification'
New-Item -ItemType Directory -Force $logs | Out-Null
$log=Join-Path $logs ('casings-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
& (Join-Path $repo 'gradlew.bat') -p $repo '--console=plain' '-PsentrySmoke=phase3' '-PcasingSmoke' runServer 2>&1 | Tee-Object -FilePath $log
if($LASTEXITCODE -ne 0){throw "Server failed; inspect $log"}
$body=Get-Content $log -Raw
if($body -notmatch 'CASING RESULT passed=15 failed=0' -or $body -notmatch 'PHASE3 RESULT passed=68 failed=0' -or $body -match 'CASING FAIL|PHASE3 FAIL|Exception stopping the server'){
 throw "Casing validation failed; inspect $log"
}
Write-Output 'Native player/sentry casing checks passed in an isolated test world.'
