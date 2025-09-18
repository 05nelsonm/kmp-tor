# kmp-tor
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-coroutines]][url-coroutines]
[![badge-encoding]][url-encoding]
[![badge-immutable]][url-immutable]
[![badge-kmp-process]][url-kmp-process]
[![badge-kmp-tor-common]][url-kmp-tor-common]
[![badge-kotlincrypto-bitops]][url-kotlincrypto-bitops]
[![badge-kotlincrypto-hash]][url-kotlincrypto-hash]
[![badge-kotlincrypto-random]][url-kotlincrypto-random]
[![badge-androidx-startup]][url-androidx-startup]

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-js-node]
![badge-platform-wasm]
![badge-platform-linux]
![badge-platform-ios]
![badge-platform-macos]
![badge-support-android-native]
![badge-support-linux-arm]

Kotlin Multiplatform support for embedding Tor into your application.

```kotlin
val runtime = TorRuntime.Builder(myEnvironment) {
    RuntimeEvent.entries().forEach { event ->
        observerStatic(event, OnEvent.Executor.Immediate) { data ->
            println(data.toString())
        }
    }
    TorEvent.entries().forEach { event ->
        observerStatic(event, OnEvent.Executor.Immediate) { data ->
            println(data)
        }
    }
    config { environment ->
        TorOption.SocksPort.configure { auto() }
        // ...
    }
    required(TorEvent.ERR)
    required(TorEvent.WARN)
}
```

```kotlin
// Asynchronous APIs
myScope.launch {
    runtime.startDaemonAsync()
    runtime.restartDaemonAsync()
    runtime.executeAsync(TorCmd.Signal.NewNym)
    runtime.stopDaemonAsync()
}
```

```kotlin
// Synchronous APIs (Android/Jvm/Native)
runtime.startDaemonSync()
runtime.restartDaemonSync()
runtime.executeSync(TorCmd.Signal.NewNym)
runtime.stopDaemonSync()
```

```kotlin
// Callback APIs
runtime.enqueue(
    Action.StartDaemon,
    OnFailure.noOp(),
    OnSuccess {
        runtime.enqueue(
            Action.RestartDaemon,
            OnFailure.noOp(),
            OnSuccess {
                runtime.enqueue(
                    TorCmd.Signal.NewNym,
                    OnFailure.noOp(),
                    OnSuccess {
                        runtime.enqueue(
                            Action.StopDaemon,
                            OnFailure.noOp(),
                            OnSuccess.noOp(),
                        )  
                    },
                )
            },
        )
    },
)
```

### Get Started

<!-- TAG_VERSION -->

- Add runtime dependency
  ```kotlin
  dependencies {
      implementation("io.matthewnelson.kmp-tor:runtime:2.4.1")
  }
  ```

- If using Java9+, ensure you have defined module `java.management` for your application.

- For Java or Android, see the `NOTE` for [OnEvent.Executor.Main][url-executor-main] for when the 
  `kotlinx-coroutines-{android/javafx/swing}` UI dependency is needed.

- Configure tor resources
    - See [kmp-tor-resource#Get Started][url-kmp-tor-resource-start]

- Configure optional service dependency
    - See [runtime-service][docs-runtime-service]

- Refer to the API docs
    - See [https://kmp-tor.matthewnelson.io][docs-root]

### Samples

See [kmp-tor-samples][url-kmp-tor-samples]

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-2.4.1-5d2f68.svg?logo=torproject&style=flat&logoColor=5d2f68
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-androidx-startup]: https://img.shields.io/badge/androidx.startup-1.1.1-6EDB8D.svg?logo=android
[badge-coroutines]: https://img.shields.io/badge/kotlinx.coroutines-1.10.2-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-2.5.0--SNAPSHOT-blue.svg?style=flat
[badge-immutable]: https://img.shields.io/badge/immutable-0.3.0--SNAPSHOT-blue.svg?style=flat
[badge-kmp-process]: https://img.shields.io/badge/kmp--process-0.4.0--SNAPSHOT-blue.svg?style=flat
[badge-kmp-tor-common]: https://img.shields.io/badge/kmp--tor--common-2.4.0--SNAPSHOT-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin
[badge-kotlincrypto-bitops]: https://img.shields.io/badge/kotlincrypto.bitops-0.3.0--SNAPSHOT-blue.svg?style=flat
[badge-kotlincrypto-hash]: https://img.shields.io/badge/kotlincrypto.hash-0.8.0--SNAPSHOT-blue.svg?style=flat
[badge-kotlincrypto-random]: https://img.shields.io/badge/kotlincrypto.random-0.6.0--SNAPSHOT-blue.svg?style=flat

<!-- TAG_PLATFORMS -->
[badge-platform-android]: https://img.shields.io/badge/-android-6EDB8D.svg?style=flat
[badge-platform-jvm]: https://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-platform-js]: https://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat
[badge-platform-linux]: https://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-platform-macos]: https://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-platform-ios]: https://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
[badge-platform-tvos]: https://img.shields.io/badge/-tvos-808080.svg?style=flat
[badge-platform-watchos]: https://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-platform-windows]: https://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-support-android-native]: https://img.shields.io/badge/support-[AndroidNative]-6EDB8D.svg?style=flat
[badge-support-linux-arm]: https://img.shields.io/badge/support-[LinuxArm]-2D3F6C.svg?style=flat

[docs-root]: https://kmp-tor.matthewnelson.io
[docs-runtime-service]: https://kmp-tor.matthewnelson.io/library/runtime-service/index.html

[url-latest-release]: https://github.com/05nelsonm/kmp-tor/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0
[url-androidx-startup]: https://developer.android.com/jetpack/androidx/releases/startup
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-encoding]: https://github.com/05nelsonm/encoding
[url-immutable]: https://github.com/05nelsonm/immutable
[url-kmp-process]: https://github.com/05nelsonm/kmp-process
[url-kmp-tor-common]: https://github.com/05nelsonm/kmp-tor-common
[url-kmp-tor-samples]: https://github.com/05nelsonm/kmp-tor-samples
[url-kmp-tor-resource-start]: https://github.com/05nelsonm/kmp-tor-resource?tab=readme-ov-file#get-started
[url-kotlin]: https://kotlinlang.org
[url-kotlincrypto-bitops]: https://github.com/KotlinCrypto/bitops
[url-kotlincrypto-hash]: https://github.com/KotlinCrypto/hash
[url-kotlincrypto-random]: https://github.com/KotlinCrypto/random
[url-executor-main]: https://kmp-tor.matthewnelson.io/library/runtime-core/io.matthewnelson.kmp.tor.runtime.core/-on-event/-executor/-main/index.html
