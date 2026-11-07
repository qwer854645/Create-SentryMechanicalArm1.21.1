param(
    [string]$JavaHome='C:/Program Files/Java/jdk-21',
    [string]$WorldName=('restart-alpha8-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference='Stop'
if($WorldName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'){throw 'Use a simple world name without paths.'}
$repo=Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME=$JavaHome
$logs=Join-Path $repo 'verification'
New-Item -ItemType Directory -Force $logs | Out-Null
foreach($phase in @('prepare','recover','verify')){
    $log=Join-Path $logs ($WorldName+'-'+$phase+'.log')
    & (Join-Path $repo 'gradlew.bat') -p $repo '--console=plain' '-PsentrySmoke=restart' "-PrestartPhase=$phase" "-PrestartWorld=$WorldName" runServer 2>&1 | Tee-Object -FilePath $log
    if($LASTEXITCODE -ne 0){throw "Server failed in phase $phase; inspect $log"}
    $text=Get-Content $log -Raw
    if($text -notmatch "RESTART RESULT phase=$phase passed=[1-9][0-9]* failed=0" -or $text -match 'RESTART FAIL|Exception stopping the server'){
        throw "Restart validation failed in phase $phase; inspect $log"
    }
}
Write-Output "Three independent JVM runs passed. Test world: $(Join-Path (Split-Path $repo -Parent) ('restart-worlds/'+$WorldName))"
