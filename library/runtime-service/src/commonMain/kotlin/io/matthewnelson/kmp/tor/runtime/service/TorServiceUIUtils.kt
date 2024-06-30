/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import kotlin.jvm.JvmSynthetic

/**
 * Utils shared between [AbstractTorServiceUI] and [AbstractTorServiceUI.InstanceState]
 * */
public sealed class TorServiceUIUtils {

    protected enum class UpdateType {
        Added,
        Removed,
        Changed
    }

    protected class FileIDKey private constructor(override val fid: String): FileID {

        override fun equals(other: Any?): Boolean {
            return other is FileIDKey && other.fid == fid
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 42 + this::class.hashCode()
            result = result * 42 + fid.hashCode()
            return result
        }

        override fun toString(): String = toFIDString(includeHashCode = false)

        internal companion object {

            @JvmSynthetic
            internal fun of(fid: String): FileIDKey = FileIDKey(fid)
        }
    }

    /**
     * Helper for exporting [synchronized] functionality to implementors
     * for thread safety.
     * */
    protected class Lock {

        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        public fun <T: Any?> withLock(block: () -> T): T {
            @OptIn(InternalKmpTorApi::class)
            return synchronized(lock, block)
        }
    }
}
