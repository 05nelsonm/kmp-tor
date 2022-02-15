# CHANGELOG

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
