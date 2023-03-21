package io.matthewnelson.kmp.tor

import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.test.Test

class TorConfigProviderJvmUnitTest {

    @Test
    fun givenTorConfigProvider_whenGetProcessId_thenReturnsExpected() {
        val config = object : TorConfigProviderJvm() {
            override val workDir: Path get() = TODO("Not yet implemented")
            override val cacheDir: Path get() = TODO("Not yet implemented")
            override fun provide(): TorConfig { TODO("Not yet implemented") }

        }

        config.processId

        // pass, does not throw.
    }
}
