. (Join-Path $PSScriptRoot 'config.ps1')
$ErrorActionPreference = 'Stop'

Write-Host '[1/4] Build'
Push-Location $ProjectRoot
try {
    & mvn clean package
    if ($LASTEXITCODE -ne 0) {
        throw 'Build esuat. Serverele nu au fost modificate.'
    }
} finally {
    Pop-Location
}

$parserWar   = Join-Path $ProjectRoot 'parser-jetty\target\parser.war'
$detectorWar = Join-Path $ProjectRoot 'detector-wildfly\target\detector.war'
$gatewayWar  = Join-Path $ProjectRoot 'gateway-tomcat\target\gateway.war'
foreach ($war in $parserWar, $detectorWar, $gatewayWar) {
    if (-not (Test-Path $war)) { throw "Lipseste $war" }
}

Write-Host '[2/4] Opresc serverele'
& (Join-Path $PSScriptRoot 'stop-all.ps1')

Write-Host '[3/4] Copiez fisierele .war'
Copy-Item $parserWar (Join-Path $JettyBase 'webapps') -Force

$deployments = Join-Path $WildflyHome 'standalone\deployments'
Remove-Item (Join-Path $deployments 'detector.war*') -Force -ErrorAction SilentlyContinue
Copy-Item $detectorWar $deployments -Force

$tomcatWebapps = Join-Path $TomcatHome 'webapps'
Remove-Item (Join-Path $tomcatWebapps 'gateway') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $tomcatWebapps 'gateway.war') -Force -ErrorAction SilentlyContinue
Copy-Item $gatewayWar $tomcatWebapps -Force

Write-Host '[4/4] Pornesc serverele'
& (Join-Path $PSScriptRoot 'start-all.ps1')