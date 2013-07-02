# InformaCam

## Setting up Development Environment

**Prerequisites:**

* [Android SDK](https://developer.android.com/sdk/installing/index.html)
* Working [Android NDK](https://developer.android.com/tools/sdk/ndk/index.html) toolchain

Follow these steps to setup your dev environment:

1. Checkout InformaCam git repo
2. Init and update git submodules

    git submodule update --init --recursive

3. Ensure `NDK_BASE` env variable is set to the location of your NDK, example:

    export NDK_BASE=/path/to/android-ndk

4. Build android-ffmpeg

    cd external/android-ffmpeg-java/external/android-ffmpeg/
    ./configure_make_everything.sh

5. Build IOCipher

    make -C external/IOCipher/external/
    ndk-build -C external/IOCipher/

 Note: the 'make' build is setup to work with the Android NDK r8e. If you are
 using an older version, or you are using the 32-bit NDK on a 64-bit system,
 then you might need to set some variables manually as part of the command
 line.  For example, using the 32-bit NDK on a 64-bit system:

    make -C external/IOCipher/external NDK_PROCESSOR=x86

 Or using an older compiler version:

    make -C external/IOCipher/external NDK_COMPILER_VERSION=4.4.3

6. Build JNI for JpegRedaction

    ndk-build

7. **Using Eclipse**

    Import into Eclipse (using the "existing projects" option) the projects in this order:

        external/OnionKit/library
        external/android-ffmpeg-java/
        external/IOCipher/
        external/ODKFormParser/

   **Using ANT**

        ./setup-ant.sh
        ant clean debug

