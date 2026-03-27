#!/usr/bin/env sh

##############################################################################
##
##  Gradle wrapper
##
##############################################################################

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
fi

# Determine the script directory.
SCRIPT_DIR=$(dirname "$0")

# Add default JVM options here.
DEFAULT_JVM_OPTS=""

APP_HOME="$SCRIPT_DIR"

# OSP specific support to run in OSP environment
if [ -f "$APP_HOME/bin/osp-tool-wrapper.sh" ]; then
    . "$APP_HOME/bin/osp-tool-wrapper.sh"
fi

# Determine the class path for the wrapper.
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine the Gradle version.
GRADLE_VERSION=$(grep -oP "(?<=distributionUrl=.*-)\d+\.\d+(\.\d+)?(?=-bin\.zip)" "$APP_HOME/gradle/wrapper/gradle-wrapper.properties")

# Execute Gradle.
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
