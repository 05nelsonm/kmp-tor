# kmp-tor
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-atomicfu]][url-atomicfu]
[![badge-coroutines]][url-coroutines]
[![badge-encoding]][url-encoding]
[![badge-parcelize]][url-parcelize]
[![badge-kmp-tor-binary]][url-kmp-tor-binary]

![badge-platform-android]
![badge-platform-jvm]

Kotlin Multiplatform support for embedding Tor into your application.

### Get Started

<!-- TAG_VERSION -->

Add dependency  
```kotlin
// build.gradle.kts

dependencies {
    val vTor = "4.7.12-2"
    val vKmpTor = "1.3.3"

    implementation("io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor")
}
```
<!-- TAG_VERSION -->

```groovy
// build.gradle

dependencies {
    def vTor = '4.7.12-2'
    def vKmpTor = '1.3.3'

    implementation "io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor"
}
```

<details>
    <summary>Configuring an Android Project</summary>


 - See the `Android` section of the [kmp-tor-binary][url-kmp-tor-binary] project's
   `README` to set things up so the Tor binaries are properly extracted upon app install.

 - By default, `TorService` needs no configuration and runs in the background. For configuring 
   it to run as a Foreground service, see the following:
     - [Configuring Attrs][url-android-kotlin-attrs]
     - [Configuring Manifest][url-android-kotlin-manifest]

<!-- TODO: Add sample code for retrieving TorManager -->
 - See the [Sample App][url-android-kotlin-app] for a basic setup of `TorManager` and your `TorConfig`.  

</details>

<details>
    <summary>Configuring a Java Project</summary>

 - See the `Java` section of the [kmp-tor-binary][url-kmp-tor-binary] project's `README`.
 - See the [JavaFX Sample App Gradle Configuration][url-javafx-kotlin-gradle] 
   for a basic gradle/dependency configuration.
 - See the [JavaFx Sample App][url-javafx-kotlin-app] for a basic setup example.
 - Run the JavaFx Sample via `./gradlew :samples:kotlin:javafx:run -PKMP_TARGETS=JVM` from terminal 
   or cmd prompt.
     - Note: Be sure to run `git submodule update --init` if you haven't yet so git 
       submodules are initialized.

</details>

### Extensions

<details>
    <summary>Unix Domain Sockets</summary>

### Tor supports use of unix domain sockets on Darwin and Linux (also Android) for the following:
 - ControlPort
 - SocksPort
 - HiddenServicePort

### How to enable unix domain socket support for the `ControlPort`:

<!-- TAG_VERSION -->

 - For Android, nothing is needed
 - For JVM
     - If **JDK 16+**, nothing is needed
     - Otherwise, add the following dependency to your darwin/linux builds:
     ```kotlin
     // build.gradlew.kts

     dependencies {
         val vTor = "4.7.12-2"
         val vKmpTor = "1.3.3"
    
         implementation("io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor")

         if (isLinuxBuild || isDarwinBuild) {
             // Unix Domain Socket support extension (JDK 15 and below)
             implementation("io.matthewnelson.kotlin-components:kmp-tor-ext-unix-socket:$vKmpTor")
         }
     }
     ```

See the [JavaFX Sample App Gradle Configuration][url-javafx-kotlin-gradle]
`dependencies` block for more info.

If neither `TorConfig.Setting.Ports.Control` or `TorConfig.Setting.UnixSockets.Control` are expressed in 
your config, `TorConfig.Setting.UnixSockets.Control` will always be the preferred setting for establishing 
a connection to Tor's control port, **if support is had** (as noted above). To override this behavior, you 
can express the `TorConfig.Setting.Ports.Control` setting when providing your config at startup.

### How to enable unix domain socket support for the `SocksPort` and `HiddenServicePort` settings:
 - Be running on Darwin or Linux (also Android)

</details>

<details>
    <summary>Callbacks (non-kotlin consumers)</summary>

 - For Java projects (who can't use coroutines), you can "wrap" `TorManager` in an implementation 
   that uses callbacks (ie. `CallbackTorManager`).

<!-- TAG_VERSION -->

```groovy
// build.gradle

dependencies {
    def vTor = '4.7.12-2'
    def vKmpTor = '1.3.3'

    implementation "io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor"
    // Add the callback extension
    implementation "io.matthewnelson.kotlin-components:kmp-tor-ext-callback-manager:$vKmpTor"

    // You will also need to add the Kotlin Gradle Plugin, and Coroutines dependency.
    
    // If not Android, you will also need to import the binaries for the platforms you wish to
    // support.
}
```

```java
// Wrapping TorManager instance in its Callback instance (Java)
public class Example1 {
    
    // ..
    TorManager instance = TorManager.newInstance(/* ... */);

    // Wrap that mug...
    CallbackTorManager torManager = new CallbackTorManager(
        instance,
        uncaughtException -> {
            Log.e("MyJavaApp", "Some TorCallback isn't handling an exception...", uncaughtException);
        }
     );
}
```

 - All requests use coroutines under the hood and are Main thread safe. 
   Results will be dispatched to the supplied callback on the Main thread.

```java
// Multiple callbacks of different styles (Java)
public class Example2 {
    
    // ...
    Task startTask = torManager.start(
        t -> Log.e(TAG, "Failed to start Tor", t),
        startSuccess -> {

            Log.d(TAG, "Tor started successfully");

            Task restartTask = torManager.restart(
                null, // fail silently by omitting failure callback
                (TorCallback<Object>) restartSuccess -> {

                    Log.d(TAG, "Tor restarted successfully");

                    Task restartTask2 = torManager.restart(
                        // Use the provided instance that will automatically throw
                        // the exception, which will pipe it to the handler.
                        TorCallback.THROW,

                        new TorCallback<Object>() {
                            @Override
                            public void invoke(Object o) {
                                Log.d(TAG, "Tor restarted successfully");
                            }
                        }
                    );
                }
            );
        }
    );
}
```

 - Android (Java):
     - [Android Sample App][url-android-java-app]
     - [Android Sample Gradle][url-android-java-gradle]
 - JavaFx (Java):
     - [JavaFx Sample App][url-javafx-java-app]
     - [JavaFx Sample Gradle][url-javafx-java-gradle]
     - Run the sample via `./gradlew :samples:java:javafx:run -PKMP_TARGETS=JVM` from terminal
       or cmd prompt.
         - Note: Be sure to run `git submodule update --init` if you haven't yet so git
           submodules are initialized.

</details>

### Git

This project utilizes git submodules. You will need to initialize them when
cloning the repository via:

```
git checkout master
git pull
git submodule update --init
```

In order to keep submodules updated when pulling the latest code, run:
```
git pull --recurse-submodules
```

<!-- TAG_VERSION -->
<!-- If Tor version was updated, don't forget to update [badge-kmp-tor-binary] -->

[badge-latest-release]: https://img.shields.io/badge/latest--release-4.7.12--2--1.3.3-5d2f68.svg?logo=torproject&style=flat&logoColor=5d2f68
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-kotlin]: https://img.shields.io/badge/kotlin-1.8.0-blue.svg?logo=kotlin
[badge-atomicfu]: https://img.shields.io/badge/atomicfu-0.19.0-blue.svg?logo=kotlin
[badge-coroutines]: https://img.shields.io/badge/coroutines-1.6.4-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-1.2.1-blue.svg?style=flat
[badge-parcelize]: https://img.shields.io/badge/parcelize-0.1.2-blue.svg?style=flat
[badge-kmp-tor-binary]: https://img.shields.io/badge/kmp--tor--binary-4.7.12--2-5d2f68.svg?logo=torproject&style=flat&logoColor=5d2f68

<!-- TAG_PLATFORMS -->
[badge-platform-android]: http://img.shields.io/badge/-android-6EDB8D.svg?style=flat
[badge-platform-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-platform-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat
[badge-platform-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-platform-macos]: http://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-platform-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
[badge-platform-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
[badge-platform-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-platform-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-support-android-native]: http://img.shields.io/badge/support-[AndroidNative]-6EDB8D.svg?style=flat
[badge-support-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg?style=flat
[badge-support-js-ir]: https://img.shields.io/badge/support-[js--IR]-AAC4E0.svg?style=flat

[url-latest-release]: https://github.com/05nelsonm/kmp-tor/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0
[url-kotlin]: https://kotlinlang.org
[url-atomicfu]: https://github.com/Kotlin/kotlinx.atomicfu
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-encoding]: https://github.com/05nelsonm/component-encoding
[url-parcelize]: https://github.com/05nelsonm/component-parcelize
[url-kmp-tor-binary]: https://github.com/05nelsonm/kmp-tor-binary
[url-android-kotlin-app]: https://github.com/05nelsonm/kmp-tor/tree/master/samples/kotlin/android/src/main/java/io/matthewnelson/kmp/tor/sample/kotlin/android
[url-android-kotlin-attrs]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/android/src/main/res/values/attrs.xml
[url-android-kotlin-manifest]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/android/src/main/AndroidManifest.xml
[url-android-java-app]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/android/src/main/java/io/matthewnelson/kmp/tor/sample/java/android/App.java
[url-android-java-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/android/build.gradle.kts
[url-javafx-kotlin-app]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/javafx/src/jvmMain/kotlin/io/matthewnelson/kmp/tor/sample/kotlin/javafx/SampleApp.kt
[url-javafx-kotlin-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/javafx/build.gradle.kts
[url-javafx-java-app]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/javafx/src/main/java/io/matthewnelson/kmp/tor/sample/java/javafx/App.java
[url-javafx-java-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/javafx/build.gradle.kts
