# CHANGELOG

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
 - Adds `TorController.disconnect` method as an additional assurance to shutdown Tor
 - Cleans up internal functionality

## Version 0.4.6.9+0.1.0-alpha3 (2022-02-15)
 - Adds reading of Tor Process error/input stream and pipes output to listeners
 - Fixes Jvm loader for Windows and Macos by excluding Transparent Proxy Port if present
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
