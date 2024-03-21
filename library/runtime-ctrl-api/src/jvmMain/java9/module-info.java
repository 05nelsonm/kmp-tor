module io.matthewnelson.kmp.tor.runtime.ctrl.api {
    requires transitive kotlin.stdlib;
    requires io.matthewnelson.encoding.base16;
    requires io.matthewnelson.encoding.base32;
    requires io.matthewnelson.encoding.base64;
    requires io.matthewnelson.encoding.core;
    requires io.matthewnelson.immutable.collections;
    requires io.matthewnelson.kmp.process;
    requires transitive io.matthewnelson.kmp.tor.core.api;
    requires io.matthewnelson.kmp.tor.core.resource;
    requires kotlinx.coroutines.core;

    exports io.matthewnelson.kmp.tor.runtime.ctrl.api;
    exports io.matthewnelson.kmp.tor.runtime.ctrl.api.address;
    exports io.matthewnelson.kmp.tor.runtime.ctrl.api.builder;
    exports io.matthewnelson.kmp.tor.runtime.ctrl.api.key;
    exports io.matthewnelson.kmp.tor.runtime.ctrl.api.util;
}
