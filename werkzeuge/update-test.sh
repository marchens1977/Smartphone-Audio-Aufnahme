#!/bin/bash
# Prüft die Update-Suche ohne Android-Werkzeuge (nur Java 17 + Internet für die Hilfsdateien).
# Aufruf aus dem Projektordner:  bash werkzeuge/update-test.sh
# Lädt einmalig: Android-Klassen (robolectric android-all), JUnit, org.json von Maven Central.
set -e
cd "$(dirname "$0")/.."
LAGER="${TMPDIR:-/tmp}/aufnahme-testlager"
mkdir -p "$LAGER"
M=https://repo1.maven.org/maven2
hole() { [ -s "$LAGER/$2" ] || curl -sSfL --retry 5 --retry-delay 10 -o "$LAGER/$2" "$M/$1/$2"; }
hole org/robolectric/android-all/14-robolectric-10818077 android-all-14-robolectric-10818077.jar
hole junit/junit/4.13.2 junit-4.13.2.jar
hole org/hamcrest/hamcrest-core/1.3 hamcrest-core-1.3.jar
hole org/json/json/20240303 json-20240303.jar
CP="$LAGER/json-20240303.jar:$LAGER/android-all-14-robolectric-10818077.jar:$LAGER/junit-4.13.2.jar:$LAGER/hamcrest-core-1.3.jar"
AUS="$LAGER/klassen"; rm -rf "$AUS"; mkdir -p "$AUS"
# 1. Die ganze App muss sich übersetzen lassen
javac -nowarn --release 17 -encoding UTF-8 -cp "$CP" -d "$AUS" app/src/main/java/de/smartphone/aufnahme/*.java
# 2. Tests übersetzen und starten (Arbeitsordner app/, wie bei Gradle)
javac --release 17 -encoding UTF-8 -cp "$CP:$AUS" -d "$AUS" app/src/test/java/de/smartphone/aufnahme/*.java
cd app && java -cp "$AUS:$CP" org.junit.runner.JUnitCore de.smartphone.aufnahme.UpdateSucheTest
