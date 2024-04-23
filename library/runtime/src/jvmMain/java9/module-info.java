module io.matthewnelson.kmp.tor.runtime {
    requires io.matthewnelson.encoding.base16;
    requires io.matthewnelson.encoding.core;
    requires io.matthewnelson.immutable.collections;
    requires io.matthewnelson.kmp.process;
    requires io.matthewnelson.kmp.tor.runtime.ctrl;
    requires transitive io.matthewnelson.kmp.tor.runtime.core;
    requires kotlinx.coroutines.core;
    requires org.kotlincrypto;
    requires org.kotlincrypto.hash.sha2;

    exports io.matthewnelson.kmp.tor.runtime;
    exports io.matthewnelson.kmp.tor.runtime.util;
}
