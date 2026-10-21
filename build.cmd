@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b *.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
echo Compiled to out\
echo Try: java -cp out harness.SelfTest
