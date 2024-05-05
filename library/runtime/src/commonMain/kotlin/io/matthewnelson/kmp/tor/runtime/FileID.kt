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
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.runtime.core.apply
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

public interface FileID {

    /**
     * An ID string which is based off of a file path.
     *
     * @see [createFID]
     * @see [fidEllipses]
     * @see [toFIDString]
     * */
    public val fid: String

    public companion object {

        /**
         * Creates an ID based off of the provided file's [canonicalPath].
         *
         * The resulting string is the product of the path's UTF-8 encoded
         * bytes double hashed using SHA-256, and then Base16 (hex) encoded.
         *
         * @see [fidEllipses]
         * @see [toFIDString]
         * */
        @JvmStatic
        public fun createFID(file: File): String = SHA256().apply {
            val pathBytes = try {
                file.canonicalPath()
            } catch (_: Throwable) {
                file.absoluteFile.normalize().path
            }.encodeToByteArray()

            val h1 = digest(pathBytes)
            update(h1)
        }.digest().encodeToString(Base16)

        /**
         * Helper for overriding a class's [toString] function.
         *
         * e.g.
         *
         *     println(myFileIDClass)
         *     // MyFileIDClass[fid=ABCD…1234]@178263541
         *
         * @param [defaultClassName] If the implementing class does
         *   not have a simple name (e.g. an Anonymous object),
         *   this will be utilized. Default: Unknown.
         * @param [includeHashCode] true to append the implementing
         *   class' [hashCode], false to omit it. Default: true.
         * */
        @JvmStatic
        @JvmOverloads
        @JvmName("fidString")
        public fun FileID.toFIDString(
            defaultClassName: String = "Unknown",
            includeHashCode: Boolean = true,
        ): String {
            val name = this::class.simpleName ?: defaultClassName
            val hash = if (includeHashCode) "@${hashCode()}" else ""
            return "$name[fid=$fidEllipses]$hash"
        }

        /**
         * Returns the first and last 4 characters of the [fid]
         * concatenated together with an ellipses between.
         *
         * If the [fid] length is less than 9 (implementor did not
         * utilize [createFID]), then the [fid] itself is returned.
         * */
        @get:JvmStatic
        @get:JvmName("fidEllipses")
        public val FileID.fidEllipses: String get() {
            if (fid.length <= 8) return fid
            return fid.take(4) + '…' + fid.takeLast(4)
        }
    }
}
