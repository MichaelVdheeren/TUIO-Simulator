#!/bin/sh
javac -O -Xlint:unchecked -source 1.5 -target 1.5 -classpath src src/*.java
jar cfm TuioSimulator.jar src/manifest.inc -C src .
rm -f src/*.class src/com/illposed/osc/*.class src/com/illposed/osc/utility/*.class
