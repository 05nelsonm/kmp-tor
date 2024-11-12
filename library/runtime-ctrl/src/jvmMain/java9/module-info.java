module io.matthewnelson.kmp.tor.runtime.ctrl {
    requires transitive io.matthewnelson.kmp.file;
    requires io.matthewnelson.kmp.tor.common.core;
    requires transitive io.matthewnelson.kmp.tor.runtime.core;
    requires kotlinx.coroutines.core;

    exports io.matthewnelson.kmp.tor.runtime.ctrl;
}
