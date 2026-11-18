$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = Join-Path $root "backend\venv\Scripts\python.exe"
$logDir = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$out = Join-Path $logDir ("retest-" + $stamp + ".txt")

try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 3
} catch {
    Write-Host "The server is not answering. Run: powershell -ExecutionPolicy Bypass -File .\courtease.ps1 start" -ForegroundColor Red
    exit 1
}

$tests = @("smoke_test.py", "check_manual_precedent.py", "check_privacy.py", "check_security.py", "push_rules_test.py", "check_isolation.py")
Push-Location (Join-Path $root "backend")
$summary = @()
foreach ($t in $tests) {
    Write-Host ("Running " + $t + " ...")
    if (-not (Test-Path $t)) {
        $summary += ("{0,-28} MISSING FILE" -f $t)
        continue
    }
    $text = (& $py $t 2>&1 | Out-String)
    $code = $LASTEXITCODE
    Add-Content -Path $out -Value ("===== " + $t + " (exit code " + $code + ")")
    Add-Content -Path $out -Value $text
    $last = $text -split "`n" | Where-Object { $_ -match "PASS \d+|Problems:" } | Select-Object -Last 1
    $shown = if ($last) { $last.Trim() } else { "(no summary line)" }
    $summary += ("{0,-28} exit {1}   {2}" -f $t, $code, $shown)
}
Pop-Location

Write-Host ""
Write-Host "RESULT SUMMARY"
$summary | ForEach-Object { Write-Host $_ }
Add-Content -Path $out -Value "===== SUMMARY"
Add-Content -Path $out -Value $summary
Write-Host ""
Write-Host ("Full output saved in: " + $out)

