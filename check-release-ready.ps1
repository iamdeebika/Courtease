$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root "app\src\main"
$kt = @(Get-ChildItem (Join-Path $src "java") -Recurse -Filter *.kt -File).FullName
$script:levels = @()
function Say($level, $text) {
    $script:levels += $level
    $color = @{ PASS = "Green"; WARN = "Yellow"; FAIL = "Red" }[$level]
    Write-Host ("{0,-5} {1}" -f $level, $text) -ForegroundColor $color
}
function Has($pattern) { return [bool](Select-String -Path $kt -Pattern $pattern -Quiet) }

$gradle = Get-Content (Join-Path $root "app\build.gradle.kts") -Raw
function Grab($pattern) { if ($gradle -match $pattern) { return $Matches[1] } else { return "?" } }
$target = Grab 'targetSdk\s*=\s*(\d+)'
Write-Host ("INFO  applicationId " + (Grab 'applicationId\s*=\s*"([^"]+)"') + ", versionName " + (Grab 'versionName\s*=\s*"([^"]+)"') + ", versionCode " + (Grab 'versionCode\s*=\s*(\d+)') + ", targetSdk " + $target)
if ($target -ne "?" -and [int]$target -ge 36) { Say "PASS" "targetSdk is $target (Google Play needs 36 or higher)" } else { Say "FAIL" "targetSdk must be 36 or higher for Google Play" }
if ($gradle -match "signingConfigs") { Say "PASS" "A release signing configuration exists" } else { Say "WARN" "No signing configuration yet. A store build must be signed with your own key." }

$manifest = Get-Content (Join-Path $src "AndroidManifest.xml") -Raw
if ($manifest -match 'usesCleartextTraffic="true"') { Say "FAIL" "The manifest allows plain http. A store build must use https only." } else { Say "PASS" "Plain http is not allowed" }
if ($manifest -match 'allowBackup="false"') { Say "PASS" "App backup is off" } else { Say "WARN" "allowBackup is not false" }
if ($manifest -match "POST_NOTIFICATIONS") { Say "PASS" "The notification permission is declared" } else { Say "WARN" "POST_NOTIFICATIONS is not declared" }

if (Has "127\.0\.0\.1|192\.168\.|ngrok") { Say "FAIL" "A local or tunnel address is written in the app code. A store build needs the real server address." } else { Say "PASS" "No local address in the app code" }
$guardReg = [bool](Select-String -Path (Get-ChildItem (Join-Path $src "java") -Recurse -Filter RegistryDashboardScreen.kt).FullName -Pattern "FLAG_DEBUGGABLE" -Quiet); if (Has "resetCases|Reset case data") { if ($guardReg) { Say "PASS" "The Reset case data button is shown only in debug builds" } else { Say "FAIL" "The registry screen has a Reset case data button. Remove it from a store build." } } else { Say "PASS" "No data reset button" }
$guardLogin = [bool](Select-String -Path (Get-ChildItem (Join-Path $src "java") -Recurse -Filter LoginScreen.kt).FullName -Pattern "FLAG_DEBUGGABLE" -Quiet); if (Has "ServerAddressButton") { if ($guardLogin) { Say "PASS" "The server-address changer is shown only in debug builds" } else { Say "WARN" "The login screen has the server-address changer. Hide it in a store build." } } else { Say "PASS" "No server-address changer" }
if (Has "(?i)privacy policy") { Say "PASS" "A privacy policy is mentioned in the app" } else { Say "FAIL" "No privacy policy link in the app (Play requires one)" }
if (Has "(?i)delete (my )?account|deleteAccount") { Say "PASS" "Account deletion exists in the app" } else { Say "FAIL" "No account deletion in the app (Play requires it when users can create accounts)" }

$backend = Join-Path $root "backend"
$cfg = @{}
$envFile = Join-Path $backend ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $cfg[$Matches[1]] = $Matches[2].Trim() }
    }
}
if ($cfg["LAW_DEMO"] -eq "1") { Say "FAIL" "LAW_DEMO is on" } else { Say "PASS" "LAW_DEMO is off" }
if ($cfg["APP_ENV"] -eq "production") { Say "PASS" "APP_ENV is production" } else { Say "WARN" "APP_ENV is not production (the /docs page is visible)" }
if ($cfg["SECRET_KEY"] -and $cfg["SECRET_KEY"].Length -ge 32) { Say "PASS" "SECRET_KEY is long enough" } else { Say "FAIL" "SECRET_KEY is missing or short" }
$deleteRoute = Select-String -Path (Join-Path $backend "*.py") -Pattern '@app\.delete\("/(auth|users|me|account)' -Quiet
if ($deleteRoute) { Say "PASS" "The server has an account deletion route" } else { Say "FAIL" "The server has no account deletion route" }

$pass = @($script:levels | Where-Object { $_ -eq "PASS" }).Count
$warn = @($script:levels | Where-Object { $_ -eq "WARN" }).Count
$fail = @($script:levels | Where-Object { $_ -eq "FAIL" }).Count
Write-Host ""
Write-Host ("PASS {0}   WARN {1}   FAIL {2}" -f $pass, $warn, $fail)

