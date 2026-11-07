param(
    [string]$JavaHome,
    [string]$ProxyUrl,
    [switch]$UseWindowsProxy,
    [switch]$VerifyStaticSentry,
    [switch]$VerifyGunGeometry,
    [switch]$VerifyMovingSentry,
    [switch]$VerifyTrainSentry,
    [switch]$VerifyAmmoDepot,
    [switch]$VerifyTrainDepot,
    [string]$SableJar
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$gradleArgs = @('-p', $repo, '--console=plain')
if ($UseWindowsProxy -and !$ProxyUrl) {
    $settings = Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings'
    if ($settings.ProxyEnable -eq 1 -and $settings.ProxyServer -notmatch '=') {
        $ProxyUrl = 'http://' + $settings.ProxyServer
    }
}
if ($ProxyUrl) {
    $proxy = [uri]$ProxyUrl
    if (!$proxy.Host -or $proxy.Port -le 0 -or $proxy.UserInfo) {
        throw 'Use a proxy URL with host and port, without credentials.'
    }
    $gradleArgs += @("-Dhttps.proxyHost=$($proxy.Host)", "-Dhttps.proxyPort=$($proxy.Port)",
                    "-Dhttp.proxyHost=$($proxy.Host)", "-Dhttp.proxyPort=$($proxy.Port)")
}
if (@($VerifyStaticSentry,$VerifyGunGeometry,$VerifyMovingSentry,$VerifyTrainSentry,$VerifyAmmoDepot,$VerifyTrainDepot).Where({$_}).Count -gt 1) { throw 'Choose one verification suite per run.' }
if ($VerifyTrainDepot) { $gradleArgs += '-PtrainDepotSmoke' }
if ($SableJar) { $gradleArgs += "-PsableJar=$((Resolve-Path -LiteralPath $SableJar).Path)" }
if ($VerifyStaticSentry -or $VerifyGunGeometry -or $VerifyMovingSentry -or $VerifyTrainSentry -or $VerifyAmmoDepot -or $VerifyTrainDepot) {
    $suite = if ($VerifyAmmoDepot) { 'depot' } elseif ($VerifyTrainSentry -or $VerifyTrainDepot) { 'train' } elseif ($VerifyMovingSentry) { 'compat' } elseif ($VerifyGunGeometry) { 'phase3' } else { 'phase2' }
    $marker = $suite.ToUpperInvariant()
    $logDir = Join-Path $repo 'verification'
    New-Item -ItemType Directory -Force $logDir | Out-Null
    $log = Join-Path $logDir ($(if ($VerifyTrainDepot) { 'train-depot' } else { $suite }) + '.log')
    & (Join-Path $repo 'gradlew.bat') @gradleArgs "-PsentrySmoke=$suite" clean runServer 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Server test failed: $LASTEXITCODE" }
    $result = Get-Content $log -Raw
    if ($result -notmatch "$marker RESULT passed=[1-9][0-9]* failed=0" -or $result -match "$marker FAIL|$marker tick failure|$marker setup fail|Exception stopping the server") {
        throw "Sentry regression failed; inspect $log"
    }
} else {
    & (Join-Path $repo 'gradlew.bat') @gradleArgs clean build
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
}
