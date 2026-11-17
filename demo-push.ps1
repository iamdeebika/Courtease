param([string]$Email = "lawyer1@courtease.in")
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = Join-Path $root "backend\venv\Scripts\python.exe"
Push-Location (Join-Path $root "backend")
Write-Host "1. A case step (this should arrive as a notification)"
& $py push_manual.py $Email alert "Case returned" "Your case was returned for correction"
Start-Sleep -Seconds 3
Write-Host "2. A judge's order (this should arrive)"
& $py push_manual.py $Email alert "Direction issued" "The judge issued a direction in your case"
Start-Sleep -Seconds 3
Write-Host "3. A chat message (stays inside the app, no notification)"
& $py push_manual.py $Email alert "New message" "Vikram sent a message"
Pop-Location
Write-Host "Done."
