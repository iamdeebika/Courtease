param([string]$Name, [int]$Part = 0)
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root "app\src\main\java\com\simats\courtease"
$f = Get-ChildItem $src -Recurse -File -Filter $Name | Select-Object -First 1
if (-not $f) { Write-Host "File not found: $Name" -ForegroundColor Red; exit 1 }
$lines = @(Get-Content $f.FullName -Encoding UTF8)
$size = 200
$parts = [math]::Ceiling($lines.Count / $size)
if ($Part -eq 0 -and $lines.Count -gt 230) {
    Write-Host ("$Name has " + $lines.Count + " lines. Copy it in $parts parts: run again with -Part 1, -Part 2 ...") -ForegroundColor Yellow
    exit 0
}
if ($Part -eq 0) { $chunk = $lines; $label = "whole file" }
else { $chunk = $lines | Select-Object -Skip (($Part - 1) * $size) -First $size; $label = "part $Part of $parts" }
Set-Clipboard -Value ("===== FILE: " + $f.Name + " (" + $label + ")`r`n" + ($chunk -join "`r`n"))
Write-Host ("Copied " + $f.Name + " (" + $label + "). Paste it into the chat with Ctrl+V.")

