@rem
@rem Copyright 2015 the original author or authors.
@rem
@if "%DEBUG%" == "" @echo off
@setlocal

where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle %*
    goto end
)

if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
    java -cp "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
    goto end
)

echo Error: Gradle not found in PATH or gradle-wrapper.jar missing.
echo Please run: gradle buildPlugin

:end
@endlocal
