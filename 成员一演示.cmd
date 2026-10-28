@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-member01-demo.ps1"
if errorlevel 1 pause
