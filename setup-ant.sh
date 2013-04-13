#!/bin/bash

if ! type -P android &> /dev/null; then
    echo "Error: 'android' utility is not in your path."
    echo "  Did you forget to setup the SDK?"
    exit 1
fi
readarray <<END
external/android-ffmpeg-java
external/OnionKit/library
external/IOCipher
external/ODKFormParser
END

for project in "${MAPFILE[@]}"; do
    android update lib-project --path $project -t 14
done

android update project --path .


