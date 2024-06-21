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
package io.matthewnelson.kmp.tor.runtime.service.internal.notification

import io.matthewnelson.kmp.tor.runtime.service.internal.NumberFormat
import io.matthewnelson.kmp.tor.runtime.service.internal.numberFormat
import kotlin.jvm.JvmField
import kotlin.math.roundToLong

internal open class Bandwidth private constructor(
    @JvmField
    internal val down: Long,
    @JvmField
    internal val up: Long,
): CharSequence {

    private val formatted: String by lazy {
        val formatter = numberFormat()
        val d = down.formatWith(formatter)
        val u = up.formatWith(formatter)
        "$d ↓ / $u ↑"
    }

    internal fun copy(
        down: Long = this.down,
        up: Long = this.up,
    ): Bandwidth {
        val d = down.coerceAtLeast(0)
        val u = up.coerceAtLeast(0)

        if (d == this.down && u == this.up) {
            return this
        }

        if (d == ZERO.down && u == ZERO.up) {
            return ZERO
        }

        return Bandwidth(d, u)
    }

    public override val length: Int get() = formatted.length
    public override fun get(index: Int): Char = formatted[index]
    public override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = formatted.subSequence(startIndex, endIndex)

    public override fun equals(other: Any?): Boolean {
        return  other is Bandwidth
                && other.down == down
                && other.up == up
    }

    public override fun hashCode(): Int {
        var result = 17
        result = result * 42 + this::class.hashCode()
        result = result * 42 + down.toString().hashCode()
        result = result * 42 + up.toString().hashCode()
        return result
    }

    public override fun toString(): String = formatted

    internal companion object ZERO: Bandwidth(0, 0)
}

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
private inline fun Long.formatWith(instance: NumberFormat): String {
    val isKBps = this < 1e6

    val number = if (isKBps) {
        (((this * 10 / 1024).toInt()) / 10).toFloat().roundToLong()
    } else {
        (((this * 100 / 1024 / 1024).toInt()) / 100).toFloat().roundToLong()
    }

    return instance.format(number) + if (isKBps) "KBps" else "MBps"
}
