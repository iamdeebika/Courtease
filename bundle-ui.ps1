$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root "app\src\main\java\com\simats\courtease"
$mine = @("DocumentsScreen.kt","SavedPrecedentsScreen.kt","ManualPrecedentDialog.kt","AnalysisScreen.kt","SummaryPanel.kt","UpcomingPanel.kt","PapersPanel.kt","ReportPanel.kt","HistoryPanel.kt","ReadingPanel.kt","TimelinePanel.kt","IssuesPanel.kt","FlagsPanel.kt","LawyerDashboardScreen.kt","RegistryDashboardScreen.kt","JudgeDashboardScreen.kt","PushSettingsDialog.kt","UiKit.kt","UiDash.kt","UiCase.kt","Common.kt","CommonForms.kt","AuthComponents.kt","IntegrityBadge.kt","PushRegistrar.kt","ServerAddress.kt","LoginScreen.kt")

$out = Join-Path $env:USERPROFILE "Desktop\round5"
New-Item -ItemType Directory -Force -Path $out | Out-Null
Get-ChildItem $out -File | Remove-Item

$files = @()
$files += Get-ChildItem (Join-Path $src "ui\screens"), (Join-Path $src "ui\components") -Filter *.kt -File | Where-Object { $mine -notcontains $_.Name }
$files += Get-ChildItem $src -Recurse -File -Filter *.kt | Where-Object { @("Models.kt","CaseOps.kt","JudgeOps.kt") -contains $_.Name }
$files = $files | Sort-Object FullName -Unique

$all = New-Object System.Collections.Generic.List[string]
foreach ($f in $files) {
    $all.Add("===== FILE: " + $f.FullName.Replace($root + "\", ""))
    foreach ($l in (Get-Content $f.FullName)) { $all.Add($l) }
    $all.Add("")
}
Set-Content -Path (Join-Path $out "all.txt") -Value $all -Encoding UTF8

$parts = New-Object System.Collections.Generic.List[string]
$sb = New-Object System.Text.StringBuilder
foreach ($l in $all) {
    if ($sb.Length + $l.Length -gt 14000) { $parts.Add($sb.ToString()); [void]$sb.Clear() }
    [void]$sb.AppendLine($l)
}
if ($sb.Length -gt 0) { $parts.Add($sb.ToString()) }
for ($i = 0; $i -lt $parts.Count; $i++) {
    $n = $i + 1
    $text = ("##### PART {0} OF {1} #####`r`n" -f $n, $parts.Count) + $parts[$i]
    Set-Content -Path (Join-Path $out ("part{0}.txt" -f $n)) -Value $text -Encoding UTF8
}

Write-Host "Files included:"
$files | ForEach-Object { "{0,5} lines  {1}" -f (Get-Content $_.FullName).Count, $_.FullName.Replace($root + "\", "") }
Write-Host ""
Write-Host ("Bundle: {0} files, {1} KB, split into {2} part(s). Folder: {3}" -f $files.Count, [math]::Round((Get-Item (Join-Path $out "all.txt")).Length / 1024), $parts.Count, $out)

$hits = Select-String -Path (Join-Path $out "*.txt") -Pattern "PRIVATE KEY|AIza|adminsdk|SECRET_KEY"
if ($hits) { Write-Host "WARNING: something that looks like a key was found. Do not send these files:" -ForegroundColor Red; $hits | Select-Object -First 5 | ForEach-Object { $_.Line.Trim() } }
else { Write-Host "No keys or secrets found in the bundle. It is safe to send." -ForegroundColor Green }
