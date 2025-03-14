module io.matthewnelson.kmp.tor.runtime.core {
    requires transitive kotlin.stdlib;
    requires io.matthewnelson.encoding.base16;
    requires io.matthewnelson.encoding.base32;
    requires io.matthewnelson.encoding.base64;
    requires io.matthewnelson.encoding.core;
    requires io.matthewnelson.immutable.collections;
    requires io.matthewnelson.kmp.process;
    requires transitive io.matthewnelson.kmp.tor.common.api;
    requires io.matthewnelson.kmp.tor.common.core;
    requires kotlinx.coroutines.core;
    requires transitive org.kotlincrypto.error;
    requires org.kotlincrypto.hash.sha2;
    requires org.kotlincrypto.hash.sha3;
    requires org.kotlincrypto.random;

    exports io.matthewnelson.kmp.tor.runtime.core;
    exports io.matthewnelson.kmp.tor.runtime.core.config;
    exports io.matthewnelson.kmp.tor.runtime.core.config.builder;
    exports io.matthewnelson.kmp.tor.runtime.core.ctrl;
    exports io.matthewnelson.kmp.tor.runtime.core.ctrl.builder;
    exports io.matthewnelson.kmp.tor.runtime.core.key;
    exports io.matthewnelson.kmp.tor.runtime.core.net;
    exports io.matthewnelson.kmp.tor.runtime.core.util;
}
