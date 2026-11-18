param([int]$N = 1)
$dir = Join-Path $env:USERPROFILE "Desktop\round5"
$file = Join-Path $dir ("part{0}.txt" -f $N)
if (-not (Test-Path $file)) { Write-Host "There is no part $N. The parts are in $dir" -ForegroundColor Red; exit 1 }
Get-Content $file -Raw | Set-Clipboard
$total = (Get-ChildItem $dir -Filter "part*.txt").Count
Write-Host "Part $N of $total is copied. Paste it into the chat with Ctrl+V and send it."
if ($N -lt $total) { Write-Host ("Then run:  powershell -ExecutionPolicy Bypass -File .\copy-part.ps1 " + ($N + 1)) }
