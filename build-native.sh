cd external/android-ffmpeg-java/external/android-ffmpeg/
./configure_make_everything.sh

cd ../../../..
make -C external/IOCipher/external/
ndk-build -C external/IOCipher/

ndk-build

