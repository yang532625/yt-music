@echo off
cd /d "C:\Users\Yang\Development\APKs-Development\Android apps\Android\Metrolist-ytm"
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat assembleFossDebug
call gradlew.bat testFossDebugUnitTest --tests "com.metrolist.music.ui.YtmAppearanceTest"
adb -s R5CX40GP43D install -r app\build\outputs\apk\foss\debug\app-foss-debug.apk
adb -s R5CX40GP43D shell am force-stop com.yang.ytmusic
timeout /t 2 /nobreak > nul
adb -s R5CX40GP43D shell am start -n com.yang.ytmusic/com.metrolist.music.MainActivityAlias

