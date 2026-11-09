$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ServersRoot = 'C:\servers'

$TomcatHome  = Join-Path $ServersRoot 'apache-tomcat-11.0.18'
$JettyHome   = Join-Path $ServersRoot 'jetty-home-12.1.14'
$JettyBase   = Join-Path $ServersRoot 'jetty-base'
$WildflyHome = Join-Path $ServersRoot 'wildfly-42.0.0.Final'

$TomcatPort  = 8080
$JettyPort   = 8081
$WildflyPort = 8082

$WildflyOffset         = $WildflyPort - 8080
$WildflyManagementPort = 9990 + $WildflyOffset

function Test-PortInUse([int]$Port) {
    $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}