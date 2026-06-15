Remove-Item -Recurse -Force ".\build\classes" -ErrorAction SilentlyContinue
.\gradlew.bat compileJava