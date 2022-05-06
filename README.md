# kmp-tor
[![Kotlin](https://img.shields.io/badge/kotlin-1.6.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Kotlin Coroutines](https://img.shields.io/badge/coroutines-1.6.0-blue.svg?logo=kotlin)](https://github.com/Kotlin/kotlinx.coroutines)
[![Kotlin Atomicfu](https://img.shields.io/badge/atomicfu-0.17.1-blue.svg?logo=kotlin)](https://github.com/Kotlin/kotlinx.atomicfu)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)  

![android](https://camo.githubusercontent.com/b1d9ad56ab51c4ad1417e9a5ad2a8fe63bcc4755e584ec7defef83755c23f923/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d616e64726f69642d3645444238442e7376673f7374796c653d666c6174)
![jvm](https://camo.githubusercontent.com/700f5dcd442fd835875568c038ae5cd53518c80ae5a0cf12c7c5cf4743b5225b/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d6a766d2d4442343133442e7376673f7374796c653d666c6174)  

Kotlin Multiplatform support for embedding Tor into your application.

### Get Started

Add dependency  
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.matthewnelson.kotlin-components:kmp-tor:0.4.6.10+0.1.0")
}
```

<details>
    <summary>Configuring an Android Project</summary>

 - See the Android section of [Configuring Gradle](https://github.com/05nelsonm/kmp-tor-binary/blob/master/README.md) 
   to setup things up so the Tor binaries are properly extracted upon app install.

 - By default, `TorService` needs no configuration and runs in the background. For configuring 
   it to run as a Foreground service, see the following:
     - [Configuring Attrs](https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml)
     - [Configuring Manifest](https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml)

<!-- TODO: Add sample code for retrieving TorManager -->
 - See the [Sample App](https://github.com/05nelsonm/kmp-tor/tree/master/samples/android/src/main/java/io/matthewnelson/kmp/tor/sample/android) 
   for a basic setup of `TorManager` and your `TorConfig`.  

</details>

<details>
    <summary>Configuring a Java Project</summary>

 - See the [JavaFX Sample App Gradle Configuration](https://github.com/05nelsonm/kmp-tor/tree/master/samples/javafx/build.gradle.kts) 
   for a basic gradle/dependency configuration.  
 - See the [JavaFx Sample App](https://github.com/05nelsonm/kmp-tor/tree/master/samples/javafx/src/jvmMain/kotlin/io/matthewnelson/kmp/tor/sample/javafx/SampleApp.kt) 
   for a basic setup example.  
 - Run the JavaFx Sample via `./gradlew :samples:kotlin:javafx:run -PKMP_TARGETS=JVM` from terminal 
   or cmd prompt.
     - Note: Be sure to run `git submodule update --init` if you haven't yet so git 
       submodules are initialized.

</details>

### Git

This project utilizes git submodules. You will need to initialize them when
cloning the repository via:

```bash
$ git checkout master
$ git pull
$ git submodule update --init
```

In order to keep submodules updated when pulling the latest code, run:
```bash
$ git pull --recurse-submodules
```
