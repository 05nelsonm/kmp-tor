/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor

import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.FILE_NAME_GEOIPS_ZIP_SHA256
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_GEOIP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.file.toFile
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorConfigProviderJvmUnitTest {

    companion object {
        @get:ClassRule
        @get:JvmStatic
        val testDir = TemporaryFolder()
        lateinit var workDir: File
        lateinit var cacheDir: File

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            workDir = testDir.newFolder()
            cacheDir = testDir.newFolder()
        }

        @JvmStatic
        @AfterClass
        fun afterClass() {
            testDir.delete()
        }
    }

    private class TestProvider : TorConfigProviderJvm() {
        override val workDir: Path get() = Path(TorConfigProviderJvmUnitTest.workDir)
        override val cacheDir: Path get() = Path(TorConfigProviderJvmUnitTest.cacheDir)
        override fun provide(): TorConfig {
            TODO("Not yet implemented")
        }

        @Throws(TorManagerException::class)
        fun extractGeoIpV4File() {
            extractGeoIpV4File(geoIpV4File!!)
        }

        @Throws(TorManagerException::class)
        fun extractGeoIpV6File() {
            extractGeoIpV6File(geoIpV6File!!)
        }
    }

    private val provider by lazy {
        TestProvider()
    }

    @Test
    fun givenNeedToExtractGeoipFile_whenCalled_isSuccessful() {
        val geoipFile: File = provider.geoIpV4File?.toFile()!!
        val sha256SumFile: File = File(workDir, FILE_NAME_GEOIPS_ZIP_SHA256)
        geoipFile.delete()
        sha256SumFile.delete()

        provider.extractGeoIpV4File()

        assertTrue(geoipFile.exists())
        assertTrue(geoipFile.length() > 0)

        // writing of sha256sum file is performed when the geoip6 file is extracted
//        assertEquals(ZIP_SHA256_GEOIP, sha256SumFile.readText())
        assertFalse(sha256SumFile.exists())
    }

    @Test
    fun givenNeedToExtractGeoip6File_whenCalled_isSuccessful() {
        val geoipFile: File = provider.geoIpV6File?.toFile()!!
        val sha256SumFile: File = File(workDir, FILE_NAME_GEOIPS_ZIP_SHA256)
        geoipFile.delete()
        sha256SumFile.delete()

        provider.extractGeoIpV6File()

        assertTrue(geoipFile.exists())
        assertTrue(geoipFile.length() > 0)
        assertEquals(ZIP_SHA256_GEOIP, sha256SumFile.readText())
    }

    @Test
    fun givenAlreadyPresentGeoipFile_whenExtractionCalled_isNotExtracted() {
        val geoipFile: File = provider.geoIpV4File?.toFile()!!
        val sha256SumFile: File = File(workDir, FILE_NAME_GEOIPS_ZIP_SHA256)
        geoipFile.delete()
        sha256SumFile.delete()

        provider.extractGeoIpV4File()

        // sha256sum file is only written when extraction of geoip6 file is performed,
        // so we must fake it here
        sha256SumFile.writeText(ZIP_SHA256_GEOIP)

        assertTrue(geoipFile.exists())
        assertTrue(geoipFile.length() > 0)

        val expectedGeoip = geoipFile.lastModified()
        val expectedSha256 = sha256SumFile.lastModified()

        Thread.sleep(25L)

        val testFile = File(workDir, "testfile.txt").also { it.createNewFile() }
        assertTrue(expectedGeoip < testFile.lastModified())
        testFile.delete()

        provider.extractGeoIpV4File()

        assertEquals(expectedGeoip, geoipFile.lastModified())
        assertEquals(expectedSha256, sha256SumFile.lastModified())
    }

    @Test
    fun givenAlreadyPresentGeoip6File_whenExtractionCalled_isNotExtracted() {
        val geoipFile: File = provider.geoIpV6File?.toFile()!!
        val sha256SumFile: File = File(workDir, FILE_NAME_GEOIPS_ZIP_SHA256)
        geoipFile.delete()
        sha256SumFile.delete()

        provider.extractGeoIpV6File()

        assertTrue(geoipFile.exists())
        assertTrue(geoipFile.length() > 0)
        assertEquals(ZIP_SHA256_GEOIP, sha256SumFile.readText())

        val expectedGeoip = geoipFile.lastModified()
        val expectedSha256 = sha256SumFile.lastModified()

        Thread.sleep(25L)

        val testFile = File(workDir, "testfile.txt").also { it.createNewFile() }
        assertTrue(expectedGeoip < testFile.lastModified())
        testFile.delete()

        provider.extractGeoIpV6File()

        assertEquals(expectedGeoip, geoipFile.lastModified())
        assertEquals(expectedSha256, sha256SumFile.lastModified())
    }
}
