param(
    [string]$JavaHome,
    [string]$ProxyUrl,
    [switch]$UseWindowsProxy
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
& (Join-Path $repo 'gradlew.bat') @gradleArgs clean build
if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
