# MIGRATION

## Preamble

`2.0.0` Is here with a complete overhaul. From the pre-compiled `tor` resources provided 
by [kmp-tor-resource][url-kmp-tor-resource], to [kmp-tor](README.md) itself. Support has 
been added for more Jvm linux architectures, and there is now support for Kotlin/Native.

[kmp-tor-resource][url-kmp-tor-resource] has by-far been the largest task, migrating away 
from `tor-browser-build` and taking ownership of the compilation process.

All capabilities of `1.x.x` is available in `2.0.0`. There are far too many changes to list;
nothing survived from `1.x.x`. Operationally, `2.0.0` is far better tested with a more friendly 
API and allows for more easily adding things.

In order to make this refactor possible, several Kotlin Multiplatform support libraries 
were needed to be built out. It has been a great experience. Enjoy!
- [kmp-file][url-kmp-file]
- [kmp-process][url-kmp-process]
- [KotlinCrypto][url-kotlin-crypto]

## Migration guide for 1.x.x -> 2.0.0

 - See [kmp-tor#README](README.md) for initial setup.
     - This can be done while still using `1.x.x` dependencies w/o a conflict, as dependency 
       coordinates and package names are all different.
 - Migrate your old `TorManager` configuration to the new `TorRuntime.BuilderScope` APIs.
 - Migrate your old `TorEvent.Listener` implementations to the new `TorEvent.Observer` APIs.
 - Migrate your old `TorManagerEvent.Listener` implementations to the new `RuntimeEvent.Observer` APIs.
 - Refer to the [kmp-tor-samples][url-kmp-tor-samples] project.
 - I apologize in advance, but it was necessary to rip the band-aid off.

[url-kmp-file]: https://github.com/05nelsonm/kmp-file
[url-kmp-process]: https://github.com/05nelsonm/kmp-process
[url-kmp-tor-resource]: https://github.com/05nelsonm/kmp-tor-resource
[url-kmp-tor-samples]: https://github.com/05nelsonm/kmp-tor-samples
[url-kotlin-crypto]: https://github.com/KotlinCrypto
