. (Join-Path $PSScriptRoot 'config.ps1')

$env:NOPAUSE = '1'

function Stop-ByPort([string]$Name, [int]$Port) {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $connection) {
        Write-Host "${Name}: nu ruleaza"
        return
    }
    Stop-Process -Id $connection.OwningProcess
    Write-Host "${Name}: oprit (proces $($connection.OwningProcess))"
}

function Wait-PortFree([int]$Port, [int]$TimeoutSeconds = 20) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortInUse $Port)) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}
if (Test-PortInUse $TomcatPort) {
     $env:CATALINA_HOME = $TomcatHome
    & (Join-Path $TomcatHome 'bin\shutdown.bat')

    Write-Host 'Tomcat: comanda de oprire trimisa'
    if (-not (Wait-PortFree $TomcatPort 30)) {
        Stop-ByPort 'Tomcat' $TomcatPort
    }
} else {
    Write-Host 'Tomcat: nu ruleaza'
}

Stop-ByPort 'Jetty' $JettyPort

if (Test-PortInUse $WildflyPort) {
    & (Join-Path $WildflyHome 'bin\jboss-cli.bat') --connect `
        "--controller=localhost:$WildflyManagementPort" 'command=:shutdown' | Out-Null
    Write-Host 'WildFly: comanda de oprire trimisa'
    if (-not (Wait-PortFree $WildflyPort 10)) {
        Stop-ByPort 'WildFly' $WildflyPort
    }
} else {
    Write-Host 'WildFly: nu ruleaza'
}

foreach ($port in $TomcatPort, $JettyPort, $WildflyPort) {
    if (-not (Wait-PortFree $port)) {
        Write-Host "Atentie: portul $port este inca ocupat"
    }
}
Write-Host 'Servere oprite.'