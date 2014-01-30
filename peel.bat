@echo off
call ant debug
adb install -r bin\Peeler-debug.apk
