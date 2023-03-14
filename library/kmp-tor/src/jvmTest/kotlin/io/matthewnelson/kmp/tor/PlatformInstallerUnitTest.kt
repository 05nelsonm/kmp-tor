package io.matthewnelson.kmp.tor

import io.matthewnelson.kmp.tor.binary.extract.TorBinaryResource
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlatformInstallerUnitTest {

    @get:Rule
    val testDir: TemporaryFolder = TemporaryFolder()
    private lateinit var workDir: File

    private val osName = System.getProperty("os.name")
    private var resource: TorBinaryResource? = null

    @Before
    fun before() {
        // Load static test resources for tor 0.4.7.12
        resource = when {
            osName.startsWith("Windows", true) -> {
                TorBinaryResource.from(
                    os = TorBinaryResource.OS.Mingw,
                    arch = "testx64",
                    sha256sum = "f228252a094f3fed8d9d08d6b98f1925644cfbbf59fbd08c566bf184027068e4",
                    resourceManifest = listOf(
                        "tor.exe.gz",
                        "tor-gencert.exe.gz"
                    ),
                )
            }

            osName == "Mac OS X" -> {
                TorBinaryResource.from(
                    os = TorBinaryResource.OS.Macos,
                    arch = "testx64",
                    sha256sum = "7c5687a3916483ee6c402ed77a99c6025c19b1bfdf54cb6e2e00601642451c82",
                    resourceManifest = listOf(
                        "libevent-2.1.7.dylib.gz",
                        "tor.gz"
                    ),
                )
            }

            osName.contains("Mac", true) -> {
                TorBinaryResource.from(
                    os = TorBinaryResource.OS.Macos,
                    arch = "testarm64",
                    sha256sum = "0d9217a47af322d72e9213c1afdd53f4f571ff0483d8053726e56efeec850ff1",
                    resourceManifest = listOf(
                        "libevent-2.1.7.dylib.gz",
                        "tor.gz"
                    ),
                )
            }

            osName == "Linux" -> {
                TorBinaryResource.from(
                    os = TorBinaryResource.OS.Linux,
                    arch = "testx64",
                    sha256sum = "7ea1e0a19f63d2542b34e1cfe8f8135b278a0eea5a7fd8d25e78e12972834ae2",
                    resourceManifest = listOf(
                        "libcrypto.so.1.1.gz",
                        "libevent-2.1.so.7.gz",
                        "libssl.so.1.1.gz",
                        "libstdc++.so.6.gz",
                        "tor.gz"
                    )
                )
            }
            else -> null
        }

        workDir = resource?.let { r ->
            testDir.newFolder(r.osName + r.arch)
        } ?: testDir.newFolder()
    }

    @Test
    fun givenPlatformInstaller_whenCustomBinaryResources_thenAreLoaded() = runBlocking {
        val resource = resource
        if (resource == null) {
            println("osName($osName) not recognized. Skipping test")
        } else {

            val installer = PlatformInstaller.custom(
                option = PlatformInstaller.InstallOption.CleanInstallOnEachStart,
                resource = resource
            )

            val tor = installer.retrieveTor(workDir)
            println("Tor Executable for osName($osName): $tor")
        }

        Unit
    }
}
