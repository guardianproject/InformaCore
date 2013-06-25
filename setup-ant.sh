#!/bin/bash

if ! type -P android &> /dev/null; then
    echo "Error: 'android' utility is not in your path."
    echo "  Did you forget to setup the SDK?"
    exit 1
fi
readarray <<END
external/android-ffmpeg-java
external/OnionKit/libonionkit
external/IOCipher
external/ODKFormParser
external/CacheWord/cachewordlib
END

for project in "${MAPFILE[@]}"; do
    android update lib-project --path $project -t android-17
done

android update project --path . -t android-17 --subprojects

cp libs/android-support-v4.jar external/CacheWord/cachewordlib/libs/android-support-v4.jar
