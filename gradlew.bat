@REM
@REM Copyright 2010 the original author or authors.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM      https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@IF EXIST "%~dp0\gradlew.bat" GOTO init
@ECHO ERROR: This batch file must be run from the directory in which it is located.
@EXIT /B 1

:init
@SETLOCAL

@REM Determine the Java command to use to start the JVM.
@IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome

@SET _JAVACMD=java.exe
@GOTO checkJava

:findJavaFromJavaHome
@SET _JAVACMD="%JAVA_HOME%\bin\java.exe"

:checkJava
@WHERE /Q %_JAVACMD%
@IF %ERRORLEVEL% NEQ 0 GOTO noJavaFound

@REM Add default JVM options here.
@SET DEFAULT_JVM_OPTS=

@SET APP_HOME=%~dp0

@REM OSP specific support to run in OSP environment
@IF EXIST "%APP_HOME%\bin\osp-tool-wrapper.bat" CALL "%APP_HOME%\bin\osp-tool-wrapper.bat"

@REM Determine the class path for the wrapper.
@SET WRAPPER_JAR="%APP_HOME%\gradle\wrapper\gradle-wrapper.jar"

@REM Determine the Gradle version.
@FOR /F "tokens=* USEBACKQ" %%G IN (`findstr /R "distributionUrl=.*-^([0-9.]*^)-bin\.zip" "%APP_HOME%\gradle\wrapper\gradle-wrapper.properties"`) DO (
    @FOR /F "tokens=2 delims=-" %%H IN ("%%G") DO (
        @SET GRADLE_VERSION=%%H
    )
)

@REM Execute Gradle.
@"%_JAVACMD%" %DEFAULT_JVM_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*

@ENDLOCAL
@EXIT /B %ERRORLEVEL%
