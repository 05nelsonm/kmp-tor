# CHANGELOG

## Version 4.7.13-2-1.4.0 (2023-03-14)
 - Updates KmpTorBinary `4.7.13-1` -> `4.7.13-2`
     - Intermediate fix for JVM `macOS aarch64` binaries failing to execute
     -  More information can be found in [[#285]][issue-285] and 
       [[kmp-tor-binary-#66]][issue-kmp-tor-binary-66]

## Version 4.7.13-1-1.4.0 (2023-03-14)
 - Update Kotlin `1.8.0` -> `1.8.10`
 - Update AtomicFu `0.19.0` -> `0.20.0`
 - Adds ability for library consumers to package and load their own
   Tor binary resources (Jvm/JS) [[#284]][pr-284]
 - Refactors gradle build system to use composite builds [[#283]][pr-283]
     - Removes submodule `kotlin-components`

## Version 4.7.13-0-1.3.4 (2023-01-31)
 - Fixes `Destroyeable.onDestroy` invocation of `onCompletion` lambda only
   being invoked if `TorManager` was not yet destroyed. Now `onCompletion`
   is **always** invoked, regardless of whether `TorManager` is already
   destroyed.
 - Updates KmpTorBinary `4.7.12-2` -> `4.7.13-0`
 - Updates `kotlin-components` submodule
     - Encoding `1.1.5` -> `1.2.1`
         - Updates deprecated encoding APIs
     - Publication improvements
 - Adds caching to CI

## Version 4.7.12-2-1.3.3 (2023-01-09)
 - Updates `kotlin-components` submodule
     - Kotlin `1.6.21` -> `1.8.0`
     - Android Gradle Plugin `7.0.4` -> `7.3.1`
     - AtomicFu `0.17.3` -> `0.19.0`
     - Coroutines `1.6.3` -> `1.6.4`
     - KmpTorBinary `4.7.12-0` -> `4.7.12-2`
     - Encoding `1.1.3` -> `1.1.5`
     - Parcelize `0.1.0` -> `0.1.2`
     - JnrUnixSocket `0.38.17` -> `0.38.19`
 - Android
     - Increases `compileSdk` from `32` -> `33`
         - Updates internally utilized APIs that have been deprecated
     - Internal code clean up

## Version 4.7.12-0-1.3.2 (2023-01-01)
 - Enables support for using Unix Domain Sockets on darwin
 - Adds JDK 16+ support for Unix Domain Sockets
     - Extension `kmp-tor-ext-unix-socket` is now only needed when running
       JDK 15 and below if you want to use Unix Domain Sockets.

## Version 4.7.12-0-1.3.1 (2023-01-01)
 - Bumps `kmp-tor-binary` from `4.7.11-0` -> `4.7.12-0`

## Version 4.7.11-0-1.3.1 (2022-10-23)
 - Bumps `kmp-tor-binary` from `4.7.10-1` -> `4.7.11-0`
 - Updates `kotlin-components` submodule to latest commit
     - Documentation + publishing improvements

## Version 4.7.10-1-1.3.1 (2022-10-23)
 - Bumps `kmp-tor-binary` from `0.4.7.10` -> `4.7.10-1`
     - Tor version stayed the same but was a sub-release of `kmp-tor-binary`
       which contained improvements and changes for supporting Kotlin/JS in the
       near future
 - Adds support for `macOS arm64` for `Java` projects
     - See the `kmp-tor-binary` project `README`'s `Java` section
 - Reworks internals for extracting binary/geoip files to use new method
   provided by `kmp-tor-binary` sub-release
 - Changes the project's version scheme to be compliant with `npmjs` publication
   so support in the very near future can be had
     - Ex: `4.7.10  -  1  -  1.3.1`
         - `tor` version `0.4.7.10`
         - `kmp-tor-binary` sub-release `1`
         - `kmp-tor` release `1.3.1`
 - Bumps `kotlin-components` submodule to latest commit
     - Build + publishing improvements

## Version 0.4.7.10+1.3.1 (2022-09-24)
 - Bumps Tor from `0.4.7.8` -> `0.4.7.10`

## Version 0.4.7.8+1.3.1 (2022-09-24)
 - Refactors Android instantiation of `TorController` when using unix
   domain sockets (i.e. `LocalSocket`).
     - Allows for `minSdk` to be reduced from `19` -> `16` for modules
         - `kmp-tor-controller`
         - `kmp-tor-manager`
         - `kmp-tor-ext-callback-controller`
         - `kmp-tor-ext-callback-manager`

## Version 0.4.7.8+1.3.0 (2022-08-06)
 - `kmp-tor-common` module:
     - Adds base `Address` interface for all `*Address` classes to
       inherit from.
         - `OnionAddress` now implements `Address`.
     - Adds `IPAddress`, `IPAddressV4`, and `IPAddressV6` to support
       modeling of IP addresses.
     - Refactors `ProxyAddress` to now use `IPAddress` as a constructor
       argument instead of `String`.
     - Refactors `ProxyAddress` to `class` instead of `data class`.
     - Improves URL parsing when using `fromString` methods for `*Address`
       classes.
     - **DEPRECATIONS:**
         - `OnionAddress.valueDotOnion` in favor of `canonicalHostname()`
         - `ProxyAddress.component1()` in favor of `address.value`
         - `ProxyAddress.component2()` in favor of `port`
         - `ProxyAddress.ipAddress` in favor of `address.value`
         - `ProxyAddress` constructor which takes `ipAddress: String` in favor
           of constructor which takes `address: IPAddress`.
         - `ProxyAddress.copy` which uses `ipAddress: String` in favor of 
           `copy()`, `copy(address: IPAddress)`, and `copy(port: Port)` methods.
         - `Server.Fingerprint.valueWithPrefix` in favor of `canonicalName()`
         - `OnionUrl` class is now deprecated
         - `Scheme` enum is now deprecated


 - `kmp-tor-controller-common` module:
     - Adds `TorConfig.KeyWord.VirtualAddrNetworkIPv4`
     - Adds `TorConfig.Setting.VirtualAddrNetworkIPv4` to configure non-default
       IPv4 network ranges when mapping addresses.
     - Adds `TorConfig.Setting.VirtualAddrNetworkIPv6` to configure non-default
       IPv6 network ranges when mapping addresses.
     - Implements `TorControlMapAddress.mapAddress`
     - Implements `TorControlResolve.resolve`


 - `kmp-tor-ext-callback-controller-common` module:
     - Implements `CallbackTorControlMapAddress.mapAddress`
     - Implements `CallbackTorControlResolve.resolve`

## Version 0.4.7.8+1.2.1 (2022-07-21)
 - Fixes `-android` publication for `*-common` modules

## Version 0.4.7.8+1.2.0 (2022-07-21)
 - Adds `-android` variants to all `*-common` modules
 - Adds dependency on `io.matthewnelson.kotlin-components:parcelize`
 - Implements `android.os.Parcelable` for wrapper classes within the
   `kmp-tor-common` module.
 - Build improvements

## Version 0.4.7.8+1.1.0 (2022-07-14)
 - Optimized `TorEvent.Listener`
 - Adds remaining `TorConfig.KeyWord`s
     - **DEPRECATIONS:**
         - `TorConfig.KeyWord.GeoIpV4File` was deprecated in favor of 
           `TorConfig.KeyWord.GeoIPFile`
         - `TorConfig.KeyWord.OwningControllerProcess` was deprecated in 
           favor of `TorConfig.KeyWord.__OwningControllerProcess`
         - `TorConfig.Setting.GeoIpV4File` was deprecated in favor of 
           `TorConfig.Setting.GeoIPFile`
         - `TorConfig.Setting.OwningControllerProcess` was deprecated in 
           favor of `TorConfig.Setting.__OwningControllerProcess`


 - Adds ability to pass `HiddenService.UnixSocket` when adding a `HiddenService` via 
   the control port.
     - **BREAKING CHANGE** (Minor):
         - `TorControlOnionAdd` interface methods were changed to accept
           `Set<HiddenService.VirtualPort>`, whereas before they accepted
           only `Set<HiddenService.Ports>`.
         - `HiddenService.Ports` and `HiddenService.UnixSocket` both 
           extend `HiddenService.VirtualPort`.


 - Refactored/cleaned up internals
 - CI/Build improvements

## Version 0.4.7.8+1.0.0 (2022-06-26)
 - Updates `atomicfu` dependency from `0.17.2` -> `0.17.3`
 - Updates `coroutines` dependency from `1.6.1` -> `1.6.3`
 - Updates `encoding` dependency from `1.1.2` -> `1.1.3`
 - Updates `kmp-tor-binary` dependency from `0.4.7.7` -> `0.4.7.8`
 - Re-enable compiler flag `kotlin.mpp.enableCompatibilityMetadataVariant=true` 
   to support non-hierarchical projects. (sorry...)
 - Adds `android` source set to `kmp-tor-controller` and 
   `kmp-tor-ext-callback-controller` modules
 - Increases `minSdk` from `16` -> `19` on `android` source sets for 
   `kmp-tor-controller`, `kmp-tor-ext-callback-controller`, `kmp-tor-manager`,
   and `kmp-tor-ext-callback-tor-manager` modules
 - Optimizes internal coroutine usage
 - Adds use of atomics to `TorConfig.Setting` classes
     - See **BREAKING CHANGES**
 - Adds `TorManager.KeyWord` classes to decouple them from needing a 
   `TorConfig.Setting` to use
     - See **BREAKING CHANGES**
 - Adds Unix Domain Socket support on Linux (and Android) for:
     - `ControlPort` via `TorConfig.Setting.UnixSockets.Control`
     - `SocksPort` via `TorConfig.Setting.UnixSockets.Socks`
     - `HiddenServicePort` via `TorConfig.Setting.HiddenService.UnixSocket`
 - Adds `TorManagerEvent.AddressInfo.unixSocks` argument specifically for when 
   configuring a `SocksPort` as a unix domain socket 
 - Adds new common code apis to instantiate a TorController connection using:
     - a `Path` (for a Unix Domain Socket)
     - a `ProxyAddress` (for a TCP connection)
 - Fixes `TorManager`'s internal comparison of disconnected `TorController` 
   to held reference when shutting down.
 - Fixes setting multiple virtual ports of the same value for a 
   `TorConfig.Setting.HiddenService`
 - Fixes `TorConfig.Builder.build` where an improperly configured setting
   that was not written to `TorConfig.text` was still being added to 
   the `TorConfig.settings`
 - Fixes issue on Windows when adding a `TorConfig.Setting.HiddenService` 
   via `TorController`, where the path was not properly escaped
 - Fixes `Path.toString()`. Now returns the `value` instead of 
   `Path(value=/some/path)`
 - Fixes `TorManager` startup operations by calling `setEvents` after 
   `configLoad` to mitigate unnecessary dispatching of `CONF_CHANGED` events
 - Fixes a bug on android where API < 26 where, for some `TorService`
   configurations resulted in a notification not being removed when the service
   was stopped while task was removed.
 - Removes default overloads from `TorControl*` use case interfaces
     - See **BREAKING CHANGES**
 - Modified `TorControlConfigGet` and `TorControlConfigReset` to use 
   `TorConfig.KeyWord`s
     - See **BREAKING CHANGES**

**BREAKING CHANGES**
 - `KmpTorLoader`
     - Note that these changes will only affect library consumers if they
       had their own implementations of `KmpTorLoader` and were not using those
       provided via the `kmp-tor` dependency.
     - `KmpTorLoader.io` constructor argument was removed
     - `KmpTorLoader.setHiddenServiceDirPermissions` method name was changed to
       `setUnixDirPermissions`
     - `KmpTorLoader.excludeSettings` type was changed from `Set<TorConfig.Setting<*>>`
       to `Set<TorConfig.KeyWord>`
 - `TorConfig`
     - `TorConfig.keyword` type was changed from `String` to `TorConfig.KeyWord`
     - `TorConfig.Setting.HiddenService.ports` type was changed from 
       `TorConfig.Setting.HiddenService.Ports` to
       `TorConfig.Setting.HiddenService.VirtualPort`
 - `ControllerUtils` was renamed to `PlatformUtil`
     - Note that all methods were annotated with `InternalTorApi` (and still are)
 - `TorControlConfigGet.configGet()` was changed from accepting a `TorConfig.Setting<*>`,
   to accepting a `TorConfig.KeyWord`
 - `TorControlConfigReset.configReset()` was changed from accepting a 
   `TorConfig.Setting<*>` to accepting a `TorConfig.KeyWord`
 - `CallbackTorControlConfigGet.configGet()` was changed from accepting a
   `TorConfig.Setting<*>`, to accepting a `TorConfig.KeyWord`
 - `CallbackTorControlConfigReset.configReset()` was changed from accepting a
   `TorConfig.Setting<*>` to accepting a `TorConfig.KeyWord`
 - `TorControlConfigSave.configSave` overloads were removed
 - `TorControlOnionAdd.onionAdd` overloads were removed
 - `TorControlOnionAdd.onionAddNew` overloads were removed
 - `TorControlSetEvents.setEvents` argument `extended` was removed (obsoleted by Tor)
 - `CallbackTorControlConfigSave.configSave` overloads were removed
 - `CallbackTorControlOnionAdd.onionAdd` overloads were removed
 - `CallbackTorControlOnionAdd.onionAddNew` overloads were removed
 - Java only users:
     - `AddressInfo.isNull()` method changed to `isNull` property
     - `TorConfig.Setting.value` method changed from `getValue()` to `value()`
     - `TorConfig.Setting.isMutable` method changed from `getIsMutable()` to `isMutable()`
     - `TorConfig.Setting.default` method changed from `getDefault()` to property `default`
     - `TorConfig.Setting.isDefault` method changed from `getIsDefault()` to `isDefault()`
     - `TorConfig.Setting.HiddenService.ports` method changed from `getPorts()` to `ports()`
     - `TorConfig.Setting.HiddenService.maxStreams` method changed from `getMaxStreams()`
       to `maxStreams()`
     - `TorConfig.Setting.HiddenService.maxStreamsCloseCircuit` method changed from
       `getMaxStreamsCloseCircuit()` to `maxStreamsCloseCircuit()`
     - `TorConfig.Setting.Ports.Dns.isolationFlags` method changed from `getIsolationFlags()`
       to `isolationFlags()`
     - `TorConfig.Setting.Ports.HttpTunnel.isolationFlags` method changed from
       `getIsolationFlags()` to `isolationFlags()`
     - `TorConfig.Setting.Ports.Socks.flags` method changed from `getFlags()` to `flags()`
     - `TorConfig.Setting.Ports.Socks.isolationFlags` method changed from `getIsolationFlags()`
       to `isolationFlags()`
     - `TorConfig.Setting.Ports.Trans.isolationFlags` method changed from `getIsolationFlags()`
       to `isolationFlags()`

## Version 0.4.7.7+0.2.0 (2022-05-31)
 - Implements Controller use case for `HSFETCH`
 - Fixes `TorController`'s mapping of multi-line responses to single-line
     - Prior versions used a space instead of new line character
     - **WARNING**: This is potentially a breaking change
 - Fixes `TorControlConfigGet.configGet` return type
     - Now returns a `List<String>` instead of a `String`
     - Method that took a single `TorConfig.Setting` previously returned
       a single `String`. This was incorrect because the setting could
       potentially be expressed multiple times (i.e. SOCKSPort, HiddenServiceDir, etc.)
     - **WARNING**: This is potentially a breaking change.
 - Fixes `configGet`, `configSet`, and `configReset` functionality.
     - Previously, only the `TorConfig.Setting.value` was being taken into account
      where some `TorConfig.Setting`s has additional parameters (such as Flags) that
      weren't being passed to the controller.
     - `TorConfig.Setting.HiddenService` only `HiddenServiceDir` keyword was being
      queried (`configGet`) or set (`configSet`, `configReset`).
     - **WARNING**: This is potentially a breaking change.
 - Fixes `kmp-tor` module's `minSdk` for Android
     - Tor version `0.4.7.7` for Android no longer works for sdks `16-20` like prior
       versions did.
     - **WARNING**: This is potentially a breaking change.
 - Adds `JvmField` annotations to `TorConfigProvider.ValidatedTorConfig` arguments
     - **WARNING**: This is potentially a breaking change for Java users.
 - Fixes `TorControlConfigLoad.configLoad` functionality
     - Previously, the passed config would be loaded but Tor would
       not honor the settings if they were used at initial startup.
       Starting Tor now only uses the minimum required arguments from
       the provided config and then immediately calls `LOADCONF` to
       load remaining ones. Future calls to `configLoad` now reset Tor
       to the initial arguments it was started with, and then will load
       the additional settings.

## Version 0.4.7.7+0.1.3 (2022-05-14)
 - Updates Kotlin-Components
     - Bumps `component-encoding` from `1.1.1` -> `1.1.2`
     - Support new targets for `*-common` modules:
         - `iosArm32`
         - `iosSimulatorArm64`
         - `tvosSimulatorArm64`
         - `watchosx86`
         - `watchosSimulatorArm64`

## Version 0.4.7.7+0.1.2 (2022-05-08)
 - Updates Kotlin-Components
     - `kotlin` from `1.6.10` -> `1.6.21`
     - `atomicfu` from `0.7.1` -> `0.7.2`
     - `coroutines` from `1.6.0` -> `1.6.1`
     - `tor-bianry` from `0.4.6.10` -> `0.4.7.7`
     - `component-encoding` from `1.1.0` -> `1.1.1`

## Version 0.4.6.10+0.1.1 (2022-05-07)
 - Significant improvements for Java users (library is now usable from Java land):
     - Removes from public APIs all use of `JvmInline` value classes by use of 
       sealed interfaces (ie. `SealedValueClass`) w/o disrupting existing APIs.
     - Adds new `SealedValueClass` annotation as an `ExperimentalTorApi`.
     - Adds New `Callback` extension libraries for wrapping `TorManager` and `TorController`
       implementations in callbacks (for non-coroutine users).
     - Sample improvements.
     - Fixes all usage of `JvmName`, `JvmField`, `JvmStatic` annotations.
 - Fixes `PlatformInstaller` when `InstallOption.CleanInstallFirstStartOnly` was elected 
   for while re-using the `PlatformInstaller` across multiple instances of `TorManager`.
 - Disables not yet implemented multiplatform targets for non-common modules.
 - Updates Kotlin-Components (publishing tool updates).

## Version 0.4.6.10+0.1.0 (2022-05-01)
 - Improvements to Multi-instance performance
 - Makes `NetworkObserver` reusable with multiple instances
 - Adds `decode` method to `OnionAddress` interface for obtaining the address in raw bytes
 - Improves Java compatibility by adding `JvmField` and `JvmName` annotations where applicable
 - Fixes dispatching of bootstrap completion events
 - Fixes error returned for `AddOnion` if flag `DiscardPK` was not passed

## Version 0.4.6.10+0.1.0-beta2 (2022-04-02)
 - Added to Jvm and Js the ability to manage multiple instances of `TorManager`
     - See new `TorMultiInstanceManager` class in `jvmJsCommonMain` source set.
 - Added to Android's TorServiceConfig the option to call `exitProcess` for Foreground 
   Service operations in the event `TorManager.destroy` completes and the application
   Task has been removed (user swiped the app from recent app's tray)
 - Added to `TorStateManager` interface the `addressInfo` property getter, so 
   attached listeners do not always need to store the value locally once dispatched.
 - Fixes localhost ip address resolution (may not be `127.0.0.1` on some machines)
 - Fixes `HiddenService.Ports` equals/hashCode to only take into account the `virtualPort`
   argument as to disallow multiple virtual ports of the same value to be expressed.
 - Internal code clean up

## Version 0.4.6.10+0.1.0-beta1 (2022-03-20)
 - Added ability to declare HiddenServices via the `TorConfig.Builder` and
   the `TorController`.
 - Added new `TorManagerEvent.StartUpCompleteForTorInstance` that is dispatched to
   registered listeners in order to proc 1 time startup events.
 - Added new `PortUtil` helper class for checking/finding port availability
 - Internal performance tweaks to `TorController` and `TorManager`
 - Fixes how `TorController` parses `ConfChanged` events.
 - Fixes `TorControlOnionClientAuthDelete` unescaped command string that caused
   the controller to endlessly loop.
 - Fixes `TorController` result failures for `25x` status coded replies from Tor
     - Status Code `250` was previously the only accepted success case, but Tor
       replies with `251`, `252`, ... in some instances where there was success, but
       some additional event occurred.
 - Fixes `Port` class' required Int range
     - Changed from `1024..65535` -> `0..65535`
     - NOTE: This was a breaking change as it introduced the `PortProxy` class
       to be used in the `TorConfig` when declaring values for dns/http/socks/trans
       proxies.
 - Fixes for Android Foreground Service:
     - Notification not showing immediately on Android API 31+
     - Now uses `startForegroundService` on API 26+ if `enableForeground` is true
 - Documentation improvements
 - Bumps dependencies

## Version 0.4.6.10+0.1.0-alpha4 (2022-02-20)
 - Bumps Tor to version 0.4.6.10
 - Fixes geoip6 file extraction when newer geoip/geoip6 files are available
 - Adds support for `OwningControllerProcess`
 - Adds `TorController.disconnect` method as an additional assurance to shut down Tor
 - Cleans up internal functionality

## Version 0.4.6.9+0.1.0-alpha3 (2022-02-15)
 - Adds reading of Tor Process error/input stream and pipes output to listeners
 - Fixes Jvm loader for Windows and macOS by excluding Transparent Proxy Port if present
 - Adds arguments to `Destroyable.destroy` method for:
    - Callback invocation upon destruction completion
    - Boolean value to stop cleanly via signaling SHUTDOWN, or stopping immediately by
    coroutine cancellation + Process destruction
 - Logging improvements
    - Refactors `TorManagerEvent` warn/error/debug to live under the `Log` subclass hierarchy
    - Adds the `Log.Info` value class type
 - Refactors `TorConfig` to:
    - Use `Set` instead of `Map`
    - `TorConfig.Setting` are now all `clone`able
    - `TorConfig.Setting` now contain an `isMutable` flag to inhibit modification once
    `TorConfig` is built.
    - Adds ability to set multiple Ports of differing values
 - Tweaks Android `TorServiceConfig` so that `stop_service_on_task_removed` can now be configured
 regardless of whether `enable_foreground` is set to true or not.

## Version 0.4.6.9+0.1.0-alpha2 (2022-02-10)
 - Improves `Destroyable.destroy` method functionality
 - Adds sample project for JavaFx
 - Improves Android sample project

## Version 0.4.6.9+0.1.0-alpha1 (2022-02-06)
 - Initial `alpha` release

[pr-283]: https://github.com/05nelsonm/kmp-tor/pull/283
[pr-284]: https://github.com/05nelsonm/kmp-tor/pull/284
[issue-285]: https://github.com/05nelsonm/kmp-tor/issues/285
[issue-kmp-tor-binary-66]: https://github.com/05nelsonm/kmp-tor-binary/issues/66
