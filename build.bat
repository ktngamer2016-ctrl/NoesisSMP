@echo off
setlocal enabledelayedexpansion

title Building NoesisSMP Plugin...

echo ==========================================
echo   Building NoesisSMP (Java 25)...
echo ==========================================
echo.

:: Locate Eclipse Adoptium Java 25 or Java 25 JDK
set "FOUND_JDK="

:: 1. Check Eclipse Adoptium JDK 25 explicitly
if exist "C:\Program Files\Eclipse Adoptium" (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do (
        if exist "%%D\bin\javac.exe" set "FOUND_JDK=%%D"
    )
)

:: 2. Fallback to standard Java JDK 25 if not found in Eclipse Adoptium
if not defined FOUND_JDK (
    if exist "C:\Program Files\Java" (
        for /d %%D in ("C:\Program Files\Java\jdk-25*") do (
            if exist "%%D\bin\javac.exe" set "FOUND_JDK=%%D"
        )
    )
)

:: 3. Fallback to any Eclipse Adoptium JDK
if not defined FOUND_JDK (
    if exist "C:\Program Files\Eclipse Adoptium" (
        for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk*" 2^>nul') do (
            if not defined FOUND_JDK (
                if exist "C:\Program Files\Eclipse Adoptium\%%D\bin\javac.exe" (
                    set "FOUND_JDK=C:\Program Files\Eclipse Adoptium\%%D"
                )
            )
        )
    )
)

if defined FOUND_JDK (
    set "JAVA_HOME=!FOUND_JDK!"
    set "PATH=!JAVA_HOME!\bin;%PATH%"
    echo [INFO] Using Java 25 JDK: "!JAVA_HOME!"
) else (
    echo [WARNING] Java 25 JDK not found automatically. Using default system Java.
)

echo.

:: Run Maven Wrapper or Maven Command
if exist "%~dp0mvnw.cmd" (
    call "%~dp0mvnw.cmd" clean package
) else (
    where mvn >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        call mvn clean package
    ) else (
        set "MVN_FOUND=0"
        for /d %%D in ("C:\Program Files\JetBrains\IntelliJ IDEA*") do (
            if exist "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" (
                set "MVN_FOUND=1"
                call "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
            )
        )
        if "!MVN_FOUND!"=="0" (
            echo [ERROR] Could not find Maven executable.
            exit /b 1
        )
    )
)

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
echo.
pause
