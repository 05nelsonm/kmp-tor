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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.use
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.runtime.core.apply
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

public interface FileID {

    /**
     * An ID string which is based off of a file path.
     *
     * @see [createFID]
     * */
    public val fid: String

    public companion object {

        /**
         * Creates a 24 character id based on a file path.
         *
         * It is the product of a double SHA-256 hash of the path,
         * whereby the first middle and last 4 bytes (12 bytes total)
         * are Base16 (hex) encoded.
         * */
        @JvmStatic
        public fun createFID(file: File): String {
            val h2 = SHA256().apply {
                val pathBytes = try {
                    file.canonicalPath()
                } catch (_: Throwable) {
                    file.absoluteFile.normalize().path
                }.encodeToByteArray()

                val h1 = digest(pathBytes)
                update(h1)
            }.digest()

            val s = StringBuilder(24)
            Base16.newEncoderFeed { char -> s.append(char) }.use { feed ->
                for (i in 0..3) { feed.consume(h2[i]) }
                for (i in 13..16) { feed.consume(h2[i]) }
                for (i in 28..31) { feed.consume(h2[i]) }
            }

            return s.toString()
        }

        @JvmStatic
        @JvmOverloads
        public fun FileID.toFIDString(includeHashCode: Boolean = true): String {
            val name = this::class.simpleName ?: "Unknown"
            return name + "[fid=" + fid + ']' + if (includeHashCode) '@' + hashCode() else ""
        }
    }
}
