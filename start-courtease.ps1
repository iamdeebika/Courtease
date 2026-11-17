$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "1. Checking MySQL..."
$mysql = Test-NetConnection 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $mysql) {
    try {
        Start-Service MySQL80 -ErrorAction Stop
        Start-Sleep -Seconds 5
    } catch {
        Write-Host "MySQL is not running and could not be started. Run this script in PowerShell as Administrator, or run: net start MySQL80"
        exit 1
    }
}
Write-Host "   MySQL is running."

Write-Host "2. Starting the CourtEase server in a new window..."
$cmd = "cd '$root\backend'; .\venv\Scripts\Activate.ps1; uvicorn main:app --host 0.0.0.0 --port 8000 --reload"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd

$up = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch { }
}
if ($up) { Write-Host "   Server is answering." } else { Write-Host "   Server did not answer in 30 seconds. Look at the new window for errors." }

Write-Host "3. Connecting the phone..."
& $adb start-server
& $adb devices
& $adb reverse tcp:8000 tcp:8000
Write-Host "Done. Open the app and tap again."
