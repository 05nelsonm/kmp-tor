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
    val vTor = "4.7.11-0"
    val vKmpTor = "1.3.1"

    implementation("io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor")
}
```
<!-- TAG_VERSION -->

```groovy
// build.gradle

dependencies {
    def vTor = '4.7.11-0'
    def vKmpTor = '1.3.1'

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

### Tor supports use of unix domain sockets on Linux (and Android) for the following:
 - ControlPort
 - SocksPort
 - HiddenServicePort

### How to enable unix domain socket support for the `ControlPort`:
 - For Android, **nothing is needed**.
 - For JVM, you will need to add the following dependency to your Linux distributions:

<!-- TAG_VERSION -->

```kotlin
// build.gradlew.kts

dependencies {
    val vTor = "4.7.11-0"
    val vKmpTor = "1.3.1"
    
    implementation("io.matthewnelson.kotlin-components:kmp-tor:$vTor-$vKmpTor")
    
    if (isLinuxBuild) {
        // Add the Unix Domain Socket support extension
        implementation("io.matthewnelson.kotlin-components:kmp-tor-ext-unix-socket:$vKmpTor")
    }
}
```

See the [JavaFX Sample App Gradle Configuration][url-javafx-kotlin-gradle]
`dependencies` block for more info.

If neither `TorConfig.Setting.Ports.Control` or `TorConfig.Setting.UnixSockets.Control` are expressed in 
your config, `TorConfig.Setting.UnixSockets.Control` will always be the preferred setting for establishing 
a connection to Tor's control port, **if support is had (JVM + Linux + extension, or on Android)**. To 
override this behavior, you can express the `TorConfig.Setting.Ports.Control` setting when providing your 
config at startup.

### How to enable unix domain socket support for the `SocksPort` and `HiddenServicePort` settings:
 - Be running on Linux (or Android)

</details>

<details>
    <summary>Callbacks (non-kotlin consumers)</summary>

 - For Java projects (who can't use coroutines), you can "wrap" `TorManager` in an implementation 
   that uses callbacks (ie. `CallbackTorManager`).

<!-- TAG_VERSION -->

```groovy
// build.gradle

dependencies {
    def vTor = '4.7.11-0'
    def vKmpTor = '1.3.1'

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

[badge-latest-release]: https://img.shields.io/badge/latest--release-4.7.11--0--1.3.1-5d2f68.svg?logo=torproject&style=flat&logoColor=5d2f68
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-kotlin]: https://img.shields.io/badge/kotlin-1.6.21-blue.svg?logo=kotlin
[badge-atomicfu]: https://img.shields.io/badge/atomicfu-0.17.3-blue.svg?logo=kotlin
[badge-coroutines]: https://img.shields.io/badge/coroutines-1.6.3-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-1.1.3-blue.svg?style=flat
[badge-parcelize]: https://img.shields.io/badge/parcelize-0.1.0-blue.svg?style=flat
[badge-kmp-tor-binary]: https://img.shields.io/badge/kmp--tor--binary-4.7.10--1-5d2f68.svg?logo=torproject&style=flat&logoColor=5d2f68

<!-- TAG_PLATFORMS -->
[badge-platform-android]: https://camo.githubusercontent.com/b1d9ad56ab51c4ad1417e9a5ad2a8fe63bcc4755e584ec7defef83755c23f923/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d616e64726f69642d3645444238442e7376673f7374796c653d666c6174
[badge-platform-jvm]: https://camo.githubusercontent.com/700f5dcd442fd835875568c038ae5cd53518c80ae5a0cf12c7c5cf4743b5225b/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d6a766d2d4442343133442e7376673f7374796c653d666c6174

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
[url-android-java-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/android/build.gradle
[url-javafx-kotlin-app]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/javafx/src/jvmMain/kotlin/io/matthewnelson/kmp/tor/sample/kotlin/javafx/SampleApp.kt
[url-javafx-kotlin-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/kotlin/javafx/build.gradle.kts
[url-javafx-java-app]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/javafx/src/main/java/io/matthewnelson/kmp/tor/sample/java/javafx/App.java
[url-javafx-java-gradle]: https://github.com/05nelsonm/kmp-tor/blob/master/samples/java/javafx/build.gradle
