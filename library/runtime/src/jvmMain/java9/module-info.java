module io.matthewnelson.kmp.tor.runtime {
    requires io.matthewnelson.encoding.base16;
    requires io.matthewnelson.encoding.core;
    requires io.matthewnelson.immutable.collections;
    requires io.matthewnelson.kmp.process;
    requires io.matthewnelson.kmp.tor.runtime.ctrl;
    requires transitive io.matthewnelson.kmp.tor.runtime.core;
    requires kotlinx.coroutines.core;
    requires org.kotlincrypto.hash.sha2;
    requires org.kotlincrypto.random;

    exports io.matthewnelson.kmp.tor.runtime;
}
