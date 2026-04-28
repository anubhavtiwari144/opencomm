@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%
set MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_MAIN=org.apache.maven.wrapper.MavenWrapperMain

pushd "%MAVEN_PROJECTBASEDIR%" >nul
java -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_WRAPPER_JAR%" %MAVEN_WRAPPER_MAIN% %*
set MAVEN_EXIT_CODE=%ERRORLEVEL%
popd >nul

exit /B %MAVEN_EXIT_CODE%
