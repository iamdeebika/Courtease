$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Say($ok, $text) {
    if ($ok) { Write-Host "PASS  $text" -ForegroundColor Green } else { Write-Host "FAIL  $text" -ForegroundColor Red }
}

$mysql = Test-NetConnection 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
Say $mysql "MySQL is listening on port 3306"

$health = $false
try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 3
    $health = ($r.StatusCode -eq 200)
} catch { }
Say $health "The CourtEase server answers on this PC (http://127.0.0.1:8000/health)"

$devices = & $adb devices
$phone = (($devices | Select-String -Pattern "\tdevice$") | Measure-Object).Count -gt 0
Say $phone "A phone is connected and authorised (adb devices)"

if ($phone) {
    $rev = (& $adb reverse --list) -join " "
    Say ($rev -match "tcp:8000") "The USB tunnel exists (adb reverse)"

    $saved = (& $adb shell "run-as com.simats.courtease cat shared_prefs/courtease_server.xml" 2>&1) -join " "
    if ($saved -match 'name="url">([^<]+)<') {
        Write-Host "INFO  The address saved in the app is: $($Matches[1])"
    } elseif ($saved -match "not debuggable|Unknown package|No such file") {
        Write-Host "INFO  No saved address was found, so the app uses its default: http://127.0.0.1:8000/"
    } else {
        Write-Host "INFO  Could not read the saved address: $saved"
    }
}

Write-Host ""
if (-not $mysql) {
    Write-Host "Fix this first: MySQL is down. In PowerShell as Administrator run: net start MySQL80"
} elseif (-not $health) {
    Write-Host "Fix this first: the server is not running. In a Terminal: cd backend; .\venv\Scripts\Activate.ps1; uvicorn main:app --host 0.0.0.0 --port 8000 --reload"
} elseif (-not $phone) {
    Write-Host "Fix this first: unlock the phone, set USB mode to File transfer, replug, and tap Allow on the USB debugging popup."
} else {
    Write-Host "The PC side is fine. If the tunnel line failed, run: adb reverse tcp:8000 tcp:8000"
    Write-Host "With the cable the saved address must be 127.0.0.1:8000. On a hotspot it must be your PC's current address from ipconfig."
}