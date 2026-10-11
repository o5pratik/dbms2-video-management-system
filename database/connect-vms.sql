SET ECHO OFF VERIFY OFF
ACCEPT vms_password CHAR PROMPT 'Oracle app password: ' HIDE
CONNECT VMS_APP/"&vms_password"@//127.0.0.1:1522/XE
UNDEFINE vms_password
