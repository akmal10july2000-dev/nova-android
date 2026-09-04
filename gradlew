#!/bin/sh

# Lightweight Gradle wrapper entry point for Nova.
# Android Studio can also import this project directly.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "Gradle wrapper JAR is missing. Open nova-android in Android Studio, or install Gradle 8.9." >&2
  exit 1
fi

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"