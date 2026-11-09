$random = New-Object System.Random 42
$culture = [System.Globalization.CultureInfo]::InvariantCulture
$offset = [TimeSpan]::FromHours(2)
$day = [DateTimeOffset]::new(2026, 10, 26, 0, 0, 0, $offset)
$entries = New-Object System.Collections.Generic.List[object]

function Add-Entry($ip, [DateTimeOffset]$time, $method, $path, $status, $bytes) {
    $stamp = $time.ToString('dd/MMM/yyyy:HH:mm:ss', $culture) + ' ' +
             $time.ToString('zzz', $culture).Replace(':', '')
    $line = '{0} - - [{1}] "{2} {3} HTTP/1.1" {4} {5} "-" "Mozilla/5.0"' -f `
            $ip, $stamp, $method, $path, $status, $bytes
    $entries.Add([pscustomobject]@{ Time = $time; Line = $line })
}

# Trafic normal: 30 adrese, 8-40 cereri fiecare, ~3% pagini lipsa
$paths = '/', '/index.html', '/about', '/contact', '/products', '/blog',
         '/css/site.css', '/js/app.js', '/images/logo.png'
for ($i = 1; $i -le 30; $i++) {
    $ip = "192.168.1.$i"
    $count = $random.Next(8, 41)
    for ($n = 0; $n -lt $count; $n++) {
        $time = $day.AddHours(8).AddSeconds($random.Next(0, 36000))
        $path = $paths[$random.Next(0, $paths.Count)]
        $status = 200
        if ($random.Next(0, 100) -lt 3) { $status = 404; $path = '/old-page' }
        Add-Entry $ip $time 'GET' $path $status ($random.Next(200, 50000))
    }
}

# Atac 1: brute-force, 60 de 401 in 60 de secunde
$start = $day.AddHours(14).AddMinutes(20)
for ($n = 0; $n -lt 60; $n++) {
    Add-Entry '203.0.113.45' $start.AddSeconds($n) 'POST' '/login' 401 312
}

# Atac 2: scanare, 110 de 404 in 110 secunde
$scanPaths = '/admin', '/wp-login.php', '/.env', '/phpmyadmin', '/backup.zip',
             '/config.php', '/.git/config', '/server-status', '/login.php', '/db.sql'
$start = $day.AddHours(11).AddMinutes(5)
for ($n = 0; $n -lt 110; $n++) {
    Add-Entry '198.51.100.7' $start.AddSeconds($n) 'GET' `
        ('{0}-{1}' -f $scanPaths[$n % $scanPaths.Count], $n) 404 198
}

# Atac 3: tipare de atac in URL (spatiile sunt codificate %20, ca intr-un log real)
$attackPaths = '/download?file=../../etc/passwd',
               "/search?q='%20OR%201=1%20--",
               '/search?q=<script>alert(1)</script>',
               '/item?id=1%20UNION%20SELECT%20username,password%20FROM%20users',
               '/files/%2e%2e%2f%2e%2e%2fetc%2fpasswd',
               '/page?x=..\..\windows\win.ini'
$start = $day.AddHours(16).AddMinutes(40)
for ($n = 0; $n -lt $attackPaths.Count; $n++) {
    Add-Entry '192.0.2.99' $start.AddSeconds($n * 7) 'GET' $attackPaths[$n] 403 150
}

# Atac 4: volum anormal, 600 de cereri de la o singura adresa
for ($n = 0; $n -lt 600; $n++) {
    Add-Entry '203.0.113.200' $day.AddHours(15).AddSeconds($random.Next(0, 600)) `
        'GET' '/index.html' 200 5123
}

$sorted = @($entries | Sort-Object Time | ForEach-Object { $_.Line })
$junk = 'aceasta linie nu este un rand de log',
        '10.0.0.1 - - [26/Oct/2026:19:20:16 +0200] "GET /index.html HTTP/1.1" 999 5123',
        '[error] aplicatia a pornit'
$lines = $sorted + $junk

$target = Join-Path $PSScriptRoot '..\gateway-tomcat\src\main\resources\sample-access.log'
Set-Content -Path $target -Value $lines -Encoding ascii
Write-Host ('Scris {0} randuri in {1}' -f $lines.Count, (Resolve-Path $target))