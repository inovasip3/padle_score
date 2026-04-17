@echo off
ECHO ============================================
ECHO  Padle Score - Deep Clean + Build Script
ECHO ============================================

ECHO [1/3] Deleting Gradle build caches...
IF EXIST "app\build" RMDIR /S /Q "app\build"
IF EXIST ".gradle" RMDIR /S /Q ".gradle"
IF EXIST "build" RMDIR /S /Q "build"

ECHO [2/3] Starting fresh build...
call gradlew.bat assembleLegacyDebug assembleLegacyRelease

IF %ERRORLEVEL% NEQ 0 (
    ECHO [ERROR] Build failed! Check output above.
    pause
    exit /B 1
)

ECHO [3/3] Copying APKs to root...
XCOPY /Y "app\build\outputs\apk\legacy\debug\app-legacy-debug.apk" "..\Padle_Score_Latest_Legacy_Debug.apk*"
XCOPY /Y "app\build\outputs\apk\legacy\release\app-legacy-release.apk" "..\Padle_Score_Latest_Legacy_Release.apk*"

ECHO ============================================
ECHO  BUILD SUCCESS!
ECHO  APKs are at the Padle_Score root folder
ECHO ============================================
pause
