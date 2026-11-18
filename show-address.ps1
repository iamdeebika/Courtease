$ProgressPreference = "SilentlyContinue"
$ips = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {
    $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|VirtualBox|VMware|Loopback|Bluetooth'
})
if ($ips.Count -eq 0) { Write-Host "No network address found. Connect the PC to the phone's hotspot first." -ForegroundColor Red; exit 1 }

Write-Host "Network addresses of this PC:"
$ips | ForEach-Object { "  {0}   ({1})" -f $_.IPAddress, $_.InterfaceAlias }

$listen = @(Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue)
if ($listen.Count -eq 0) { Write-Host "FAIL  The server is not running. Run: powershell -ExecutionPolicy Bypass -File .\courtease.ps1 start" -ForegroundColor Red }
elseif ($listen.LocalAddress -contains "0.0.0.0" -or $listen.LocalAddress -contains "::") { Write-Host "PASS  The server accepts connections from other devices" -ForegroundColor Green }
else { Write-Host "FAIL  The server only listens on this PC. It must be started with --host 0.0.0.0" -ForegroundColor Red }

$rule = Get-NetFirewallRule -DisplayName "CourtEase API" -ErrorAction SilentlyContinue
if ($rule) { Write-Host "PASS  The firewall rule exists" -ForegroundColor Green }
else { Write-Host "FAIL  No firewall rule. Do Step 1 in PowerShell as Administrator." -ForegroundColor Red }

Write-Host ""
foreach ($ip in $ips) {
    try {
        $r = Invoke-WebRequest -Uri ("http://" + $ip.IPAddress + ":8000/health") -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { Write-Host ("Type this in the app:  " + $ip.IPAddress + ":8000") -ForegroundColor Green }
    } catch { Write-Host ("No answer on " + $ip.IPAddress) -ForegroundColor Yellow }
}
