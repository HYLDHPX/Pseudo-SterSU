# Non-destructive device smoke test for an already enabled stealth recovery page.
# Never deletes /data/adb or sends the correct code. Run the isolated Rust/Kotlin
# tests for the directory-loss case instead.
param(
    [string]$Serial,
    [string]$Package = 'io.github.fixz.stersu',
    [switch]$ForceStopManager,
    [switch]$CheckInvalidCodes,
    [switch]$RebootDevice,
    [string]$UpdateApk
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb([string[]]$Arguments) {
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed: $($output -join ' ')"
    }
    return ($output -join "`n")
}

function Get-Response([string]$Method, [string]$Path, [string]$Body = '') {
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        "http://127.0.0.1:10241$Path"
    )
    $request.Headers.Host = '127.0.0.1:10241'
    if ($Method -eq 'POST') {
        $request.Headers.TryAddWithoutValidation('Origin', 'http://127.0.0.1:10241') | Out-Null
        $request.Headers.TryAddWithoutValidation('X-SterSU-Recovery', '1') | Out-Null
        $request.Content = [System.Net.Http.StringContent]::new(
            $Body,
            [System.Text.Encoding]::UTF8,
            'application/json'
        )
    }
    try {
        return $script:client.SendAsync($request).GetAwaiter().GetResult()
    } finally {
        $request.Dispose()
    }
}

function Assert-Health {
    $response = Get-Response 'GET' '/health'
    try {
        $marker = $response.Headers.Contains('X-SterSU-Recovery')
        if ([int]$response.StatusCode -ne 200 -or -not $marker) {
            throw "Recovery health check failed: HTTP $([int]$response.StatusCode), marker=$marker"
        }
        Write-Host 'PASS: recovery page responds independently on device loopback.'
    } finally {
        $response.Dispose()
    }
}

if (-not $Serial) {
    $devices = @(& adb devices | Where-Object { $_ -match '^([^\s]+)\s+device$' } |
        ForEach-Object { $Matches[1] })
    if ($devices.Count -ne 1) { throw 'Specify -Serial when zero or multiple devices are connected.' }
    $Serial = $devices[0]
}

Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(5)
$forwarded = $false
try {
    $installed = Invoke-Adb -Arguments @('shell', 'pm', 'path', $Package)
    if ($installed -notmatch 'package:') { throw "$Package is not installed on $Serial." }
    $mode = Invoke-Adb -Arguments @('shell', "su -c 'test -f /data/adb/ksu/hansu_mode && echo enabled || echo disabled'")
    if ($mode.Trim() -ne 'enabled') {
        Write-Host 'SKIP: stealth mode is off. No recovery listener or destructive test was started.'
        return
    }

    $forwardList = Invoke-Adb -Arguments @('forward', '--list')
    if ($forwardList -match 'tcp:10241\s+') {
        throw 'Host tcp:10241 is already forwarded; refusing to replace the existing mapping.'
    }
    Invoke-Adb -Arguments @('forward', 'tcp:10241', 'tcp:10241') | Out-Null
    $forwarded = $true
    Assert-Health

    if ($CheckInvalidCodes) {
        $wrongCode = '__invalid_' + [Guid]::NewGuid().ToString('N')
        $response = Get-Response 'POST' '/disable' ('{"code":"' + $wrongCode + '"}')
        try {
            if ([int]$response.StatusCode -ne 403) {
                throw "Wrong code returned HTTP $([int]$response.StatusCode), expected 403."
            }
        } finally { $response.Dispose() }
        $response = Get-Response 'POST' '/disable' ('{"code":"' + ('x' * 4086) + '"}')
        try {
            if ([int]$response.StatusCode -ne 400) {
                throw "Oversized code returned HTTP $([int]$response.StatusCode), expected 400."
            }
        } finally { $response.Dispose() }
        Assert-Health
        Write-Host 'PASS: wrong and oversized codes were rejected without disabling stealth.'
    }

    if ($ForceStopManager) {
        Invoke-Adb -Arguments @('shell', 'am', 'force-stop', $Package) | Out-Null
        Assert-Health
        Write-Host 'PASS: recovery remains available after Manager force-stop.'
    }

    if ($UpdateApk) {
        if (-not (Test-Path -LiteralPath $UpdateApk -PathType Leaf)) {
            throw "APK not found: $UpdateApk"
        }
        Invoke-Adb -Arguments @('install', '-r', (Resolve-Path -LiteralPath $UpdateApk).Path) | Out-Null
        Assert-Health
        Write-Host 'PASS: recovery remains available after APK replacement.'
    }

    if ($RebootDevice) {
        Invoke-Adb -Arguments @('reboot') | Out-Null
        Invoke-Adb -Arguments @('wait-for-device') | Out-Null
        $deadline = [DateTime]::UtcNow.AddMinutes(3)
        do {
            Start-Sleep -Seconds 2
            try {
                $booted = Invoke-Adb -Arguments @('shell', 'getprop', 'sys.boot_completed')
            } catch {
                $booted = ''
            }
        } until ($booted.Trim() -eq '1' -or [DateTime]::UtcNow -gt $deadline)
        if ($booted.Trim() -ne '1') { throw 'Device did not finish booting within 3 minutes.' }
        Invoke-Adb -Arguments @('forward', 'tcp:10241', 'tcp:10241') | Out-Null
        $healthy = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            try { Assert-Health; $healthy = $true; break } catch { Start-Sleep -Seconds 2 }
        }
        if (-not $healthy) { throw 'Recovery did not come back after reboot.' }
        Write-Host 'PASS: recovery returns after reboot.'
    }
} finally {
    if ($forwarded) { & adb -s $Serial forward --remove tcp:10241 | Out-Null }
    $client.Dispose()
    $handler.Dispose()
}
