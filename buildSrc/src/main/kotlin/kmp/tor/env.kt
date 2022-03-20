/*
 * Copyright (c) 2021 Matthew Nelson
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
package kmp.tor

@Suppress("ClassName")
object env {
    // Must be either "-SNAPSHOT" || ""
    private const val SNAPSHOT              = "-SNAPSHOT"

    private const val MANAGER_VERSION_NAME  = "0.1.0-beta2"
    //                           1.0.0-alpha1  == 01_00_00_11
    //                           1.0.0-alpha2  == 01_00_00_12
    //                           1.0.0-beta1   == 01_00_00_21
    //                           1.0.0-rc1     == 01_00_00_31
    //                           1.0.0         == 01_00_00_99
    //                           1.0.1         == 01_00_01_99
    //                           1.1.1         == 01_01_01_99
    //                           1.15.1        == 01_15_01_99
    private const val MANAGER_VERSION_CODE  = /*00_0*/1_00_22

    private const val BINARY_VERSION_NAME   = "0.4.6.10"
    //                          0.4.6.9        == 00_04_06_09_00
    //                          0.4.6.9a       == 00_04_06_09_01
    //                          0.4.6.9b       == 00_04_06_09_02
    private const val BINARY_VERSION_CODE   = /*00_0*/4_06_10_00

    /**
     * Binaries exist in a different repo. Building against the staged
     * release is needed in order to run integration tests before publishing
     * them to ensure everything is copacetic.
     * */
    object kmpTorBinaries {
        const val pollStagingRepo           = false
        object version {
            const val name                  = BINARY_VERSION_NAME
        }
    }

    /**
     * Modules:
     *  - :library:controller:*
     *  - :library:kmp-tor-common
     *  - :library:kmp-tor-internal
     *  - :library:manager:*
     * */
    object kmpTor {
        const val holdPublication           = false
        object version {
            const val name                  = "$MANAGER_VERSION_NAME$SNAPSHOT"
            const val code                  = MANAGER_VERSION_CODE
        }
    }

    /**
     * Module :library:kmp-tor (combined kmp-tor + binary distribution)
     * */
    object kmpTorAll {
        const val holdPublication           = false
        object version {
            const val name                  = "$BINARY_VERSION_NAME+${kmpTor.version.name}"
            const val code                  = BINARY_VERSION_CODE + MANAGER_VERSION_CODE
        }
    }
}
