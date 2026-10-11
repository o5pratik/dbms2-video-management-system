@echo off
setlocal
set "TNS_ADMIN=%~dp0database\oracle-net"
set "SQLPLUS_EXE=D:\app\oracle\product\11.2.0\server\bin\sqlplus.exe"
if not exist "%SQLPLUS_EXE%" (
  echo Oracle SQL*Plus was not found at %SQLPLUS_EXE%
  exit /b 1
)
echo Connecting to the Video Management System schema in Oracle XE.
echo Enter the DB_PASSWORD value from the project's .env file when prompted.
"%SQLPLUS_EXE%" -L /nolog @"%~dp0database\connect-vms.sql"
