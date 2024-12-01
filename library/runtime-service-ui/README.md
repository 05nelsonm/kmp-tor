# Module runtime-service-ui

An extension to [runtime-service][docs-runtime-service] which provides a "default" implementation of 
[TorServiceUI][docs-TorServiceUI].

<details>
    <summary>Configure Android</summary>

- **Permissions are required when using this dependency:**
    - See [AndroidManifest.xml][url-manifest-service-ui]
    - See [:runtime-service#AndroidManifest.xml][url-manifest-service]
- See the [Sample Implementation][url-sample-android]

</details>

<details>
    <summary>Configure iOS</summary>

Not yet implemented. PRs are welcomed! See [Issue #547][url-issue-547]

</details>

### Get Started

- Add dependency to project/sourceSet(s)
  ```kotlin
  // build.gradle.kts
  dependencies {
      implementation("io.matthewnelson.kmp-tor:runtime-service-ui:$vKmpTor")
  }
  ```

[docs-TorServiceUI]: https://kmp-tor.matthewnelson.io/library/runtime-service/io.matthewnelson.kmp.tor.runtime.service/-tor-service-u-i/index.html

[docs-runtime-service]: https://kmp-tor.matthewnelson.io/library/runtime-service/index.html

[url-manifest-service]: https://github.com/05nelsonm/kmp-tor/blob/master/library/runtime-service/src/androidMain/AndroidManifest.xml
[url-manifest-service-ui]: https://github.com/05nelsonm/kmp-tor/blob/master/library/runtime-service-ui/src/androidMain/AndroidManifest.xml
[url-issue-547]: https://github.com/05nelsonm/kmp-tor/issues/547
[url-sample-android]: https://github.com/05nelsonm/kmp-tor-samples/blob/master/samples/compose/src/androidMain/kotlin/io/matthewnelson/kmp/tor/sample/compose/Tor.android.kt
