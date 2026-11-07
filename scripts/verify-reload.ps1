param(
    [string]$JavaHome='C:/Program Files/Java/jdk-21',
    [string]$WorldName=('reload-alpha9-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference='Stop'
if($WorldName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'){throw 'Use a simple world name without paths.'}
$repo=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME=$JavaHome
$logs=Join-Path $repo 'verification'
New-Item -ItemType Directory -Force $logs | Out-Null
$log=Join-Path $logs ($WorldName+'.log')
& (Join-Path $repo 'gradlew.bat') -p $repo '--console=plain' '-PsentrySmoke=reload' "-PreloadWorld=$WorldName" runServer 2>&1 | Tee-Object -FilePath $log
if($LASTEXITCODE -ne 0){throw "Server failed; inspect $log"}
$text=Get-Content $log -Raw
if($text -notmatch 'RELOAD RESULT passed=[1-9][0-9]* failed=0' -or $text -match 'RELOAD FAIL|Exception stopping the server'){
    throw "Reload validation failed; inspect $log"
}
Write-Output "Reload validation passed. Isolated world: $(Join-Path (Split-Path $repo -Parent) ('reload-worlds/'+$WorldName))"
