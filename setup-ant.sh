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
external/google-play-services_lib
END

for project in "${MAPFILE[@]}"; do
    android update lib-project --path $project -t android-17
done

android update project --path . -t android-17 -n InformaCamCore --subprojects
