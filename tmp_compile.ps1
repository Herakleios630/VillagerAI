".\\gradlew.bat --stop 2>&1
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Stop-Process -Name javaw -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
if (Test-Path build) {
    Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 1
.\\gradlew.bat compileJava 2>&1 | Select-String 'Fehler|Error|BUILD|Symbol' | Select-Object -Last 15
"