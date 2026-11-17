param([string]$Command = "status")

$ProgressPreference = "SilentlyContinue"
$script = $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $script
$backend = Join-Path $root "backend"
$venvPy = Join-Path $backend "venv\Scripts\python.exe"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$logDir = Join-Path $root "logs"
$serverOut = Join-Path $logDir "server-out.log"
$serverLog = Join-Path $logDir "server.log"
$watchLog = Join-Path $logDir "watch.log"

function Log($file, $text) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Add-Content -Path $file -Value ("{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $text)
}

function Say($pass, $text) {
    if ($pass) { Write-Host "PASS  $text" -ForegroundColor Green } else { Write-Host "FAIL  $text" -ForegroundColor Red }
}

function Test-Health {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 3
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

function Test-Mysql {
    return [bool](Test-NetConnection 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue)
}

function Stop-Server {
    Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}

function Start-Server {
    if (-not (Test-Path $venvPy)) {
        Write-Host "Python was not found at $venvPy" -ForegroundColor Red
        return $false
    }
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Stop-Server
    Start-Sleep -Seconds 1
    Start-Process -FilePath $venvPy -ArgumentList "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000" -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $serverOut -RedirectStandardError $serverLog
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Health) { return $true }
    }
    return $false
}

function Get-WatcherProcs {
    Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'courtease\.ps1"?\s+watch' -and $_.ProcessId -ne $PID }
}

function Start-Watcher {
    if (Get-WatcherProcs) { return }
    Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", "`"$script`"", "watch" -WindowStyle Hidden
}

function Stop-Watcher {
    Get-WatcherProcs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

function Get-Devices {
    $found = @()
    foreach ($line in (& $adb devices 2>$null)) {
        if ($line -match "^(\S+)\s+device$") { $found += $Matches[1] }
    }
    return $found
}

function Watch-Loop {
    Log $watchLog "Watcher started"
    $fails = 0
    $restarts = @()
    $tick = 0
    while ($true) {
        try {
            foreach ($serial in (Get-Devices)) {
                $list = (& $adb -s $serial reverse --list 2>$null) -join " "
                if ($list -notmatch "tcp:8000") {
                    & $adb -s $serial reverse tcp:8000 tcp:8000 | Out-Null
                    Log $watchLog "Tunnel restored for $serial"
                }
            }
        } catch { }
        $tick++
        if ($tick % 3 -eq 0) {
            if (Test-Health) { $fails = 0 } else { $fails++ }
            if ($fails -ge 3) {
                $now = Get-Date
                $restarts = @($restarts | Where-Object { ($now - $_).TotalMinutes -lt 10 })
                if ($restarts.Count -lt 3) {
                    Log $watchLog "The server was not answering, restarting it"
                    $restarts += $now
                    [void](Start-Server)
                } else {
                    Log $watchLog "The server keeps failing, not restarting it again for now. Read logs\server.log"
                }
                $fails = 0
            }
        }
        Start-Sleep -Seconds 3
    }
}

function Show-Status {
    Say (Test-Mysql) "MySQL is listening on port 3306"
    Say (Test-Health) "The CourtEase server answers (http://127.0.0.1:8000/health)"
    $devices = @(Get-Devices)
    Say ($devices.Count -gt 0) "A phone or emulator is connected (adb devices)"
    $tunnel = $false
    foreach ($serial in $devices) {
        $list = (& $adb -s $serial reverse --list 2>$null) -join " "
        if ($list -match "tcp:8000") { $tunnel = $true }
    }
    Say $tunnel "The tunnel to the phone exists (adb reverse)"
    Say ([bool](Get-WatcherProcs)) "The watcher that keeps everything alive is running"
}

function Invoke-Start {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    if (-not (Test-Mysql)) {
        try { Start-Service MySQL80 -ErrorAction Stop } catch { }
        for ($i = 0; $i -lt 20; $i++) {
            if (Test-Mysql) { break }
            Start-Sleep -Seconds 2
        }
    }
    if (-not (Test-Mysql)) {
        Write-Host "MySQL is not running. Open PowerShell as Administrator and run: net start MySQL80" -ForegroundColor Red
    }
    if (Test-Health) {
        Write-Host "The server is already running."
    } elseif (Start-Server) {
        Write-Host "The server started."
    } else {
        Write-Host "The server did not answer. Read: logs\server.log" -ForegroundColor Red
    }
    Start-Watcher
    Show-Status
}

switch ($Command.ToLower()) {
    "start" { Invoke-Start }
    "stop" { Stop-Watcher; Stop-Server; Write-Host "Stopped the server and the watcher." }
    "restart" { Stop-Watcher; Stop-Server; Start-Sleep -Seconds 2; Invoke-Start }
    "watch" { Watch-Loop }
    "status" { Show-Status }
    "autostart" {
        $cmdFile = Join-Path ([Environment]::GetFolderPath("Startup")) "CourtEase.cmd"
        $text = "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" start`r`n"
        Set-Content -Path $cmdFile -Value $text -Encoding ASCII
        Write-Host "Auto-start installed: $cmdFile"
    }
    "noautostart" {
        Remove-Item (Join-Path ([Environment]::GetFolderPath("Startup")) "CourtEase.cmd") -ErrorAction SilentlyContinue
        Write-Host "Auto-start removed."
    }
    default { Write-Host "Use: start, stop, restart, status, autostart or noautostart" }
}
