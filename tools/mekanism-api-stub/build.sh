#!/bin/sh
# Builds libs/mekanism-api-stub-compileonly.jar from the stub sources here (compile-only: build.gradle's
# compileOnlySC configuration, never packed into the mod's jar). Run from the project root.
JDK=${JAVA_HOME:-/c/Users/Aleksandr/Documents/MineMod/.build-tools/jdk8/jdk8u504-b01}
FORGE=$(ls ~/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forgeBin-*.jar | head -1)
OUT=build/mekanism-api-stub
rm -rf "$OUT" && mkdir -p "$OUT"
"$JDK/bin/javac" -source 1.6 -target 1.6 -nowarn -cp "$FORGE" -d "$OUT" tools/mekanism-api-stub/src/mekanism/api/energy/*.java || exit 1
"$JDK/bin/jar" cf libs/mekanism-api-stub-compileonly.jar -C "$OUT" mekanism
