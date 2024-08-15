# Module runtime

A "wrapper" around `runtime-ctrl` that is designed for process management, and facilitation 
of interaction between API consumers and the tor daemon. Provides high level APIs for creating
tor process environments and runtimes, observation of those runtimes, as well as pass-through 
interaction command issuance to the managed controller connection.
