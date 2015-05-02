#!/bin/bash

if ! type -P android &> /dev/null; then
    echo "Error: 'android' utility is not in your path."
    echo "  Did you forget to setup the SDK?"
    exit 1
fi

declare -a MAPFILE=('external/android-ffmpeg-java' 'external/OnionKit/libnetcipher' 'external/ODKFormParser' 'external/CacheWord/cachewordlib' 'external/google-play-services' 'external/CameraCipher')

for project in "${MAPFILE[@]}"; do
    android update lib-project --path $project -t android-19
done

cp external/CacheWord/cachewordlib/libs/iocipher.jar libs/iocipher.jar

cp libs/android-support-v4.jar external/CacheWord/cachewordlib/libs/android-support-v4.jar
cp libs/android-support-v4.jar external/OnionKit/libnetcipher/libs/android-support-v4.jar

android update project --path . -t android-19 -n InformaCamCore --subprojects
