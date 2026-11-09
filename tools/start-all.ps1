. (Join-Path $PSScriptRoot 'config.ps1')

$env:NOPAUSE = '1'

function Wait-Healthy([string]$Name, [string]$Url, [int]$TimeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                Write-Host ('[ OK ] {0,-18} {1}' -f $Name, $Url)
                return $true
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Host ('[ !! ] {0,-18} nu raspunde: {1}' -f $Name, $Url)
    return $false
}

if (Test-PortInUse $JettyPort) {
    Write-Host "Jetty: portul $JettyPort este ocupat, sar peste"
} else {
    Write-Host 'Pornesc Jetty...'
    Start-Process -FilePath 'java' `
        -ArgumentList '-jar', "`"$JettyHome\start.jar`"" `
        -WorkingDirectory $JettyBase
}

if (Test-PortInUse $WildflyPort) {
    Write-Host "WildFly: portul $WildflyPort este ocupat, sar peste"
} else {
    Write-Host 'Pornesc WildFly...'
    Start-Process -FilePath "$WildflyHome\bin\standalone.bat" `
        -ArgumentList "-Djboss.socket.binding.port-offset=$WildflyOffset" `
        -WorkingDirectory "$WildflyHome\bin"
}

if (Test-PortInUse $TomcatPort) {
    Write-Host "Tomcat: portul $TomcatPort este ocupat, sar peste"
} else {
    Write-Host 'Pornesc Tomcat...'
    Start-Process -FilePath "$TomcatHome\bin\catalina.bat" `
        -ArgumentList 'run' `
        -WorkingDirectory "$TomcatHome\bin"
}

Write-Host ''
Write-Host 'Astept serviciile...'
$results = @(
    (Wait-Healthy 'parser (jetty)'      "http://localhost:$JettyPort/parser/health"),
    (Wait-Healthy 'detector (wildfly)'  "http://localhost:$WildflyPort/detector/health"),
    (Wait-Healthy 'gateway (tomcat)'    "http://localhost:$TomcatPort/gateway/health")
)

Write-Host ''
if ($results -contains $false) {
    Write-Host 'Cel putin un serviciu nu a pornit. Verifica ferestrele serverelor.'
    exit 1
}
Write-Host "Totul ruleaza. Interfata: http://localhost:$TomcatPort/gateway/"