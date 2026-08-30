@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ==========================================
echo   Building NoesisSMP (Java 25) ...
echo ==========================================
call .\mvnw.cmd clean package
echo.
if %ERRORLEVEL% equ 0 (
    echo ==========================================
    echo   BUILD SUCCESSFUL!
    echo   Output JAR: target\NoesisSMP-1.0.1.jar
    echo ==========================================
) else (
    echo ==========================================
    echo   BUILD FAILED! Check error output above.
    echo ==========================================
)
pause
