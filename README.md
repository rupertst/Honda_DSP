# Honda_DSP
Simple Honda amplifire control app

It is a simple android app to control OEM Honda Accord aplifier from aftermareet Android Headunit 
I was designed for Honda Accord VIII 2012 Europe with Premium Sound System, but I think it shoud work with some models of Acura TSX (If someone succeed, give the feetback please).
In this car the aplifier is connected to radio with only two channels (left and right) and with two data wires (Data + and Data -).

To use this app You need RS485 converter (I used Waveshare, eg.: https://www.waveshare.com/usb-to-rs485.htm) connected to Data wires.
Check the car documentation to find apropriate wires.

App uses localization services to get GPS speed (to increase volume) 
The service monitors Android Headunit Volume to set proper volume to amplifier

Tested on Android Headunit with Android 10 (SDK 29)

PROBLEMS:
1) After waking device from sleep, service starts, but nothing is send trough RS485 - temporary solution is to restart service (there is a loop that restarts service when no responce from amplifier)
2) There is double volume control. If changing volume of headunit, also the volume of amplituner is changing. I do not know solution to fik the volume of headunit, but to preserve the system volume bar and buttons to controll amp volume. There is a switch tha separates amp volume from system volume. You can fix the amp volume in this way.

You use it on Your own risk, no guarantee.
If it does not work, I can try to help - no guarantee also
If You want change ot adapt - feel free to do it - just publish it.

## Build APK

Prerequisites:
- JDK 17
- Android SDK installed locally
- `ANDROID_SDK_ROOT` pointing at your SDK, for example `~/Library/Android/sdk` on macOS

Build a debug APK:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_SDK_ROOT=~/Library/Android/sdk
./gradlew --no-daemon assembleDebug
```

APK output:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

If your machine is behind TLS inspection or a corporate proxy, Gradle may fail to download dependencies until Java trusts your local network certificates.

Links to builded APK:
https://www.dropbox.com/s/zwvs0g42nb78gf4/HondaDSP.apk?dl=0
or:
https://wrzucajpliki.pl/j6wa9asf9tiz/HondaDSP.apk
