# Module runtime

A "wrapper" around [runtime-ctrl][docs-runtime-ctrl] that is designed for management of tor start/stop/restart, 
and facilitation of interaction between API consumers and the tor daemon. Provides high level APIs for 
creating tor process environments and runtimes, observation of those runtimes, as well as pass-through 
interaction command issuance to the managed controller connection.

[docs-runtime-ctrl]: https://kmp-tor.matthewnelson.io/library/runtime-ctrl/index.html
