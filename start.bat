@echo off
setlocal EnableExtensions
cd /d "%~dp0"
if exist ".env" (
  for /f "usebackq tokens=1,* delims== eol=#" %%A in (".env") do if not "%%A"=="" set "%%A=%%B"
)
if not defined APP_PORT set APP_PORT=8080
if not defined APP_HOST set APP_HOST=127.0.0.1
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
if exist ".tools\apache-maven-3.9.16\bin\mvn.cmd" set "PATH=%~dp0.tools\apache-maven-3.9.16\bin;%PATH%"
if not exist "database" mkdir "database"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 17 or newer was not found. Install a JDK and add it to PATH.
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Install Maven and add it to PATH.
  exit /b 1
)
echo Starting Video Management System on http://%APP_HOST%:%APP_PORT%/
cd backend
if /i "%DB_MODE%"=="ODBC" (
  call mvn -Dmaven.repo.local=../.tools/m2 -Pexternal-odbc-bridge spring-boot:run
) else if /i "%DB_MODE%"=="ORACLE" (
  call mvn -Dmaven.repo.local=../.tools/m2 -Poracle-jdbc spring-boot:run
) else (
  call mvn -Dmaven.repo.local=../.tools/m2 spring-boot:run
)

