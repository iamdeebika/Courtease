$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb start-server
& $adb devices
& $adb reverse tcp:8000 tcp:8000
& $adb reverse --list





