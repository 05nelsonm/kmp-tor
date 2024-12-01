# Module runtime-service

A [TorRuntime.ServiceFactory][docs-ServiceFactory] implementation which facilitates execution of 
[TorRuntime][docs-TorRuntime] within a service object, such as an `android.app.Service`.

<details>
    <summary>Configure Android</summary>

This dependency will "wrap" [TorRuntime.Environment][docs-Environment] creation with some helpers, 
whereby all instances of [TorRuntime][docs-TorRuntime] instantiated with them will be started within 
`TorService`.

For Foreground Service support, you can either implement the [TorServiceUI][docs-TorServiceUI] yourself, 
or utilize the [runtime-service-ui][docs-runtime-service-ui] dependency.

- **Permissions may be required when using this dependency:**
    - See [AndroidManifest.xml][url-manifest-service]
- See the [TorServiceConfig Docs][docs-TorServiceConfig]
- See the [Sample Implementation][url-sample-android]

</details>

<details>
    <summary>Configure iOS</summary>

Not yet implemented. PRs are welcomed! See [Issue #547][url-issue-547]

</details>

### Get Started

**NOTE:** if using [runtime-service-ui][docs-runtime-service-ui], this dependency does not need to 
be expressed (it is automatically added by [runtime-service-ui][docs-runtime-service-ui]).

- Add dependency to project/sourceSet(s)
  ```kotlin
  // build.gradle.kts
  dependencies {
      implementation("io.matthewnelson.kmp-tor:runtime-service:$vKmpTor")
  }
  ```

[docs-Environment]: https://kmp-tor.matthewnelson.io/library/runtime/io.matthewnelson.kmp.tor.runtime/-tor-runtime/-environment/index.html
[docs-TorRuntime]: https://kmp-tor.matthewnelson.io/library/runtime/io.matthewnelson.kmp.tor.runtime/-tor-runtime/index.html
[docs-TorServiceConfig]: https://kmp-tor.matthewnelson.io/library/runtime-service/io.matthewnelson.kmp.tor.runtime.service/-tor-service-config/index.html
[docs-TorServiceUI]: https://kmp-tor.matthewnelson.io/library/runtime-service/io.matthewnelson.kmp.tor.runtime.service/-tor-service-u-i/index.html
[docs-ServiceFactory]: https://kmp-tor.matthewnelson.io/library/runtime/io.matthewnelson.kmp.tor.runtime/-tor-runtime/-service-factory/index.html

[docs-runtime-service-ui]: https://kmp-tor.matthewnelson.io/library/runtime-service-ui/index.html

[url-manifest-service]: https://github.com/05nelsonm/kmp-tor/blob/master/library/runtime-service/src/androidMain/AndroidManifest.xml
[url-issue-547]: https://github.com/05nelsonm/kmp-tor/issues/547
[url-sample-android]: https://github.com/05nelsonm/kmp-tor-samples/blob/master/samples/compose/src/androidMain/kotlin/io/matthewnelson/kmp/tor/sample/compose/Tor.android.kt

