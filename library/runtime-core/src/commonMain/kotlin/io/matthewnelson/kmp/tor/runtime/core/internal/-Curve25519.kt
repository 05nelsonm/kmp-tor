/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("NOTHING_TO_INLINE", "UnusedReceiverParameter", "LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey
import org.kotlincrypto.bitops.endian.Endian.Little.lePackIntoUnsafe
import org.kotlincrypto.error.KeyException
import org.kotlincrypto.hash.sha2.SHA512
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.RandomnessProcurementException
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.jvm.JvmInline
import kotlin.math.min

@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateED25519PrivateKey(): ED25519_V3.PrivateKey {
    val sk = ByteArray(ED25519_V3.PrivateKey.BYTE_SIZE)
    val seed = ByteArray(ED25519_V3.PrivateKey.SEED_SIZE)
    nextBytes(seed)
    SHA512().apply { update(seed) }.digestInto(sk, 0)
    sk.clampPrivateKey()
    val key = sk.toED25519_V3PrivateKey()

    sk.fill(0)
    seed.fill(0)

    return key
}

@Throws(IllegalStateException::class, KeyException::class)
internal fun ED25519_V3.PrivateKey.toPublicKey(): ED25519_V3.PublicKey {
    val sk = encoded()
    val pk = ByteArray(ED25519_V3.PublicKey.BYTE_SIZE)

    val p = GE.Accum()
    val q = GE.Affine()
    GE.scalarMultBase(sk, p)
    GE.normalizeToAffine(p, q)
    if (GE.checkPoint(q) == 0) throw KeyException("checkPoint == 0")
    GE.encodePoint(q, pk)
    val key = pk.toED25519_V3PublicKey()

    FE.zero(p.x)
    FE.zero(p.y)
    FE.zero(p.z)
    FE.zero(p.u)
    FE.zero(p.v)
    FE.zero(q.x)
    FE.zero(q.y)
    pk.fill(0)
    sk.fill(0)

    return key
}

@Throws(RandomnessProcurementException::class)
internal fun CryptoRand.generateX25519PrivateKey(): X25519.PrivateKey {
    val sk = ByteArray(X25519.PrivateKey.BYTE_SIZE)
    nextBytes(sk)
    sk.clampPrivateKey()
    val key = sk.toX25519PrivateKey()

    sk.fill(0)

    return key
}

@Throws(IllegalStateException::class, KeyException::class)
internal fun X25519.PrivateKey.toPublicKey(): X25519.PublicKey {
    val sk = encoded()
    val pk = ByteArray(X25519.PublicKey.BYTE_SIZE)

    val p = GE.Accum()
    GE.scalarMultBase(sk, p)
    if (GE.checkPoint(p) == 0) throw KeyException("checkPoint == 0")
    FE.apm(p.z, p.y, p.y, p.z)
    FE.inv(p.z, p.z)
    FE.mul(p.y, p.z, p.y)
    FE.normalize(p.y)

    val g = Scalar()
    Scalar.encode(p.y, g)
    g.data.lePackIntoUnsafe(
        dest = pk,
        destOffset = 0,
        sourceIndexStart = 0,
        sourceIndexEnd = g.data.size,
    )
    val key = pk.toX25519PublicKey()

    FE.zero(p.x)
    FE.zero(p.y)
    FE.zero(p.z)
    FE.zero(p.u)
    FE.zero(p.v)
    g.data.fill(0)
    pk.fill(0)
    sk.fill(0)

    return key
}

private inline fun ByteArray.clampPrivateKey(): ByteArray = apply {
    this[ 0] = (this[ 0].toUByte() and 248u).toByte()
    this[31] = (this[31].toUByte() and 127u).toByte()
    this[31] = (this[31].toUByte()  or  64u).toByte()
}

// FieldElement
@JvmInline
private value class FE private constructor(val data: IntArray) {
    constructor(): this(IntArray(SIZE))
    constructor(i0: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int, i8: Int, i9: Int): this(
        intArrayOf(i0, i1, i2, i3, i4, i5, i6, i7, i8, i9)
    )
    companion object {
        private const val SIZE: Int = 10
        const val M24 = 0x00ffffff
        const val M25 = 0x01ffffff
        const val M26 = 0x03ffffff
    }
}

@JvmInline
private value class Scalar private constructor(val data: IntArray) {
    constructor(): this(IntArray(SIZE))
    companion object {
        private const val SIZE: Int = 32 / Int.SIZE_BYTES
        val L = Scalar(intArrayOf(1559614445, 1477600026, -1560830762, 350157278, 0, 0, 0, 268435456))
        val P = Scalar(intArrayOf(-19, -1, -1, -1, -1, -1, -1, 2147483647))
        const val M30 = 0x3fffffff
        const val M32 = 0xffffffffL
    }
}

// GroupElement
private object GE {

    val d = FE(56195235, 47411844, 25868126, 40503822, 57364, 58321048, 30416477, 31930572, 57760639, 10749657)

    @JvmInline
    value class Accum private constructor(private val data: Array<FE>) {
        constructor(): this(Array(5) { FE() })
        val x: FE get() = data[0]
        val y: FE get() = data[1]
        val z: FE get() = data[2]
        val u: FE get() = data[3]
        val v: FE get() = data[4]
    }

    @JvmInline
    value class Affine private constructor(private val data: Array<FE>) {
        constructor(): this(Array(2) { FE() })
        val x: FE get() = data[0]
        val y: FE get() = data[1]
    }

    @JvmInline
    value class PreComp private constructor(private val data: Array<FE>) {
        constructor(): this(FE(), FE(), FE())
        constructor(ymx: FE, ypx: FE, xyd: FE): this(arrayOf(ymx, ypx, xyd))
        val ymx: FE get() = data[0]
        val ypx: FE get() = data[1]
        val xyd: FE get() = data[2]

        companion object {
            val TABLE: Array<PreComp> by lazy { arrayOf(
                GE_00, GE_01, GE_02, GE_03, GE_04, GE_05, GE_06, GE_07,
                GE_08, GE_09, GE_10, GE_11, GE_12, GE_13, GE_14, GE_15,
                GE_16, GE_17, GE_18, GE_19, GE_20, GE_21, GE_22, GE_23,
                GE_24, GE_25, GE_26, GE_27, GE_28, GE_29, GE_30, GE_31,
                GE_32, GE_33, GE_34, GE_35, GE_36, GE_37, GE_38, GE_39,
                GE_40, GE_41, GE_42, GE_43, GE_44, GE_45, GE_46, GE_47,
                GE_48, GE_49, GE_50, GE_51, GE_52, GE_53, GE_54, GE_55,
                GE_56, GE_57, GE_58, GE_59, GE_60, GE_61, GE_62, GE_63,
            ) }
        }
    }
}

private inline operator fun FE.get(index: Int): Int = data[index]
private inline operator fun FE.set(index: Int, newValue: Int) { data[index] = newValue }
private inline val FE.indices: IntRange get() = data.indices

private inline fun FE.Companion.zero(z: FE) {
    z.data.fill(0)
}

private inline fun FE.Companion.one(z: FE) {
    z[0] = 1
    z.data.fill(0, 1)
}

private inline fun FE.Companion.add(x: FE, y: FE, z: FE) {
    for (i in z.indices) {
        z[i] = x[i] + y[i]
    }
}

private inline fun FE.Companion.addOne(z: FE) {
    z[0] += 1
}

private fun FE.Companion.apm(x: FE, y: FE, zp: FE, zm: FE) {
    for (i in zm.indices) {
        val xi = x[i]
        val yi = y[i]
        zp[i] = xi + yi
        zm[i] = xi - yi
    }
}

private fun FE.Companion.carry(z: FE) {
    var z0 = z[0]
    var z1 = z[1]
    var z2 = z[2]
    var z3 = z[3]
    var z4 = z[4]
    var z5 = z[5]
    var z6 = z[6]
    var z7 = z[7]
    var z8 = z[8]
    var z9 = z[9]

    z2 += (z1 shr  26)
    z1  = (z1 and M26)
    z4 += (z3 shr  26)
    z3  = (z3 and M26)
    z7 += (z6 shr  26)
    z6  = (z6 and M26)
    z9 += (z8 shr  26)
    z8  = (z8 and M26)

    z3 += (z2 shr  25)
    z2  = (z2 and M25)
    z5 += (z4 shr  25)
    z4  = (z4 and M25)
    z8 += (z7 shr  25)
    z7  = (z7 and M25)
    z0 += (z9 shr  25) * 38
    z9  = (z9 and M25)

    z1 += (z0 shr  26)
    z0  = (z0 and M26)
    z6 += (z5 shr  26)
    z5  = (z5 and M26)

    z2 += (z1 shr  26)
    z1  = (z1 and M26)
    z4 += (z3 shr  26)
    z3  = (z3 and M26)
    z7 += (z6 shr  26)
    z6  = (z6 and M26)
    z9 += (z8 shr  26)
    z8  = (z8 and M26)

    z[0] = z0
    z[1] = z1
    z[2] = z2
    z[3] = z3
    z[4] = z4
    z[5] = z5
    z[6] = z6
    z[7] = z7
    z[8] = z8
    z[9] = z9
}

private inline fun FE.Companion.copy(x: FE, z: FE) {
    x.data.copyInto(z.data)
}

private fun FE.Companion.inv(x: FE, z: FE) {
    val t = FE()
    val u = Scalar()

    FE.copy(x, t)
    FE.normalize(t)
    Scalar.encode(t, u)
    Scalar.modOddInverse(u, u)
    Scalar.decode(u, z)

    FE.zero(t)
    u.data.fill(0)
}

private fun FE.Companion.isZero(x: FE): Int {
    var d = 0
    for (i in x.indices) {
        d = d or x[i]
    }
    d = (d ushr 1) or (d and 1)
    return (d - 1) shr 31
}

private fun FE.Companion.mov(cond: Int, x: FE, z: FE) {
    for (i in x.indices) {
        var zi = z[i]
        val diff = zi xor x[i]
        zi = zi xor (diff and cond)
        z[i] = zi
    }
}

private fun FE.Companion.mul(x: FE, y: FE, z: FE) {
    var x0 = x[0]
    var x1 = x[1]
    var x2 = x[2]
    var x3 = x[3]
    var x4 = x[4]

    var y0 = y[0]
    var y1 = y[1]
    var y2 = y[2]
    var y3 = y[3]
    var y4 = y[4]

    val u0 = x[5]
    val u1 = x[6]
    val u2 = x[7]
    val u3 = x[8]
    val u4 = x[9]

    val v0 = y[5]
    val v1 = y[6]
    val v2 = y[7]
    val v3 = y[8]
    val v4 = y[9]

    var a0 = (x0.toLong() * y0)
    var a1 = (x0.toLong() * y1 + x1.toLong() * y0)
    var a2 = (x0.toLong() * y2 + x1.toLong() * y1 + x2.toLong() * y0)
    var a3 = (x1.toLong() * y2 + x2.toLong() * y1)
    a3 = a3 shl 1
    a3 += (x0.toLong() * y3 + x3.toLong() * y0)
    var a4 = (x2.toLong() * y2)
    a4 = a4 shl 1
    a4 += (x0.toLong() * y4 + x1.toLong() * y3 + x3.toLong() * y1 + x4.toLong() * y0)
    var a5 = (x1.toLong() * y4 + x2.toLong() * y3 + x3.toLong() * y2 + x4.toLong() * y1)
    a5 = a5 shl 1
    var a6 = (x2.toLong() * y4 + x4.toLong() * y2)
    a6 = a6 shl 1
    a6 += (x3.toLong() * y3)
    var a7 = (x3.toLong() * y4 + x4.toLong() * y3)
    var a8 = (x4.toLong() * y4)
    a8 = a8 shl 1

    val b0 = (u0.toLong() * v0)
    val b1 = (u0.toLong() * v1 + u1.toLong() * v0)
    val b2 = (u0.toLong() * v2 + u1.toLong() * v1 + u2.toLong() * v0)
    var b3 = (u1.toLong() * v2 + u2.toLong() * v1)
    b3 = b3 shl 1
    b3 += (u0.toLong() * v3 + u3.toLong() * v0)
    var b4 = (u2.toLong() * v2)
    b4 = b4 shl 1
    b4 += (u0.toLong() * v4 + u1.toLong() * v3 + u3.toLong() * v1 + u4.toLong() * v0)
    val b5 = (u1.toLong() * v4 + u2.toLong() * v3 + u3.toLong() * v2 + u4.toLong() * v1)
    var b6 = (u2.toLong() * v4 + u4.toLong() * v2)
    b6 = b6 shl 1
    b6 += u3.toLong() * v3
    val b7 = (u3.toLong() * v4 + u4.toLong() * v3)
    val b8 = (u4.toLong() * v4)

    a0 -= b5 * 76
    a1 -= b6 * 38
    a2 -= b7 * 38
    a3 -= b8 * 76

    a5 -= b0
    a6 -= b1
    a7 -= b2
    a8 -= b3

    x0 += u0
    y0 += v0
    x1 += u1
    y1 += v1
    x2 += u2
    y2 += v2
    x3 += u3
    y3 += v3
    x4 += u4
    y4 += v4

    val c0 = (x0.toLong() * y0)
    val c1 = (x0.toLong() * y1 + x1.toLong() * y0)
    val c2 = (x0.toLong() * y2 + x1.toLong() * y1 + x2.toLong() * y0)
    var c3 = (x1.toLong() * y2 + x2.toLong() * y1)
    c3 = c3 shl 1
    c3 += (x0.toLong() * y3 + x3.toLong() * y0)
    var c4 = x2.toLong() * y2
    c4 = c4 shl 1
    c4 += (x0.toLong() * y4 + x1.toLong() * y3 + x3.toLong() * y1 + x4.toLong() * y0)
    var c5 = (x1.toLong() * y4 + x2.toLong() * y3 + x3.toLong() * y2 + x4.toLong() * y1)
    c5 = c5 shl 1
    var c6 = (x2.toLong() * y4 + x4.toLong() * y2)
    c6 = c6 shl 1
    c6 += x3.toLong() * y3
    val c7 = (x3.toLong() * y4 + x4.toLong() * y3)
    var c8 = x4.toLong() * y4
    c8 = c8 shl 1

    var t = a8 + (c3 - a3)
    val z8 = t.toInt() and M26
    t = t shr 26
    t += (c4 - a4) - b4
    val z9 = t.toInt() and M25
    t = t shr 25
    t = a0 + (t + c5 - a5) * 38
    z[0] = t.toInt() and M26
    t = t shr 26
    t += a1 + (c6 - a6) * 38
    z[1] = t.toInt() and M26
    t = t shr 26
    t += a2 + (c7 - a7) * 38
    z[2] = t.toInt() and M25
    t = t shr 25
    t += a3 + (c8 - a8) * 38
    z[3] = t.toInt() and M26
    t = t shr 26
    t += a4 + b4 * 38
    z[4] = t.toInt() and M25
    t = t shr 25
    t += a5 + (c0 - a0)
    z[5] = t.toInt() and M26
    t = t shr 26
    t += a6 + (c1 - a1)
    z[6] = t.toInt() and M26
    t = t shr 26
    t += a7 + (c2 - a2)
    z[7] = t.toInt() and M25
    t = t shr 25
    t += z8.toLong()
    z[8] = t.toInt() and M26
    t = t shr 26
    z[9] = z9 + t.toInt()
}

private fun FE.Companion.negate(negate: Int, z: FE) {
    val mask = 0 - negate
    for (i in z.indices) {
        z[i] = (z[i] xor mask) - mask
    }
}

private fun FE.Companion.normalize(z: FE) {
    val x = (z[9] ushr 23) and 1
    reduce(z,  x)
    reduce(z, -x)
}

private fun FE.Companion.reduce(z: FE, x: Int) {
    var t = z[9]
    val z9 = t and M24
    t = (t shr 24) + x

    var cc = (t * 19).toLong()
    cc += z[0].toLong()
    z[0] = cc.toInt() and M26
    cc = cc shr 26
    cc += z[1].toLong()
    z[1] = cc.toInt() and M26
    cc = cc shr 26
    cc += z[2].toLong()
    z[2] = cc.toInt() and M25
    cc = cc shr 25
    cc += z[3].toLong()
    z[3] = cc.toInt() and M26
    cc = cc shr 26
    cc += z[4].toLong()
    z[4] = cc.toInt() and M25
    cc = cc shr 25
    cc += z[5].toLong()
    z[5] = cc.toInt() and M26
    cc = cc shr 26
    cc += z[6].toLong()
    z[6] = cc.toInt() and M26
    cc = cc shr 26
    cc += z[7].toLong()
    z[7] = cc.toInt() and M25
    cc = cc shr 25
    cc += z[8].toLong()
    z[8] = cc.toInt() and M26
    cc = cc shr 26
    z[9] = z9 + cc.toInt()
}

private fun FE.Companion.sqr(x: FE, z: FE) {
    var x0 = x[0]
    var x1 = x[1]
    var x2 = x[2]
    var x3 = x[3]
    var x4 = x[4]

    val u0 = x[5]
    val u1 = x[6]
    val u2 = x[7]
    val u3 = x[8]
    val u4 = x[9]

    var x1_2 = x1 * 2
    var x2_2 = x2 * 2
    var x3_2 = x3 * 2
    var x4_2 = x4 * 2

    var a0 = (x0.toLong() * x0)
    var a1 = (x0.toLong() * x1_2)
    var a2 = (x0.toLong() * x2_2 + x1.toLong() * x1)
    var a3 = (x1_2.toLong() * x2_2 + x0.toLong() * x3_2)
    val a4 = (x2.toLong() * x2_2 + x0.toLong() * x4_2 + x1.toLong() * x3_2)
    var a5 = (x1_2.toLong() * x4_2 + x2_2.toLong() * x3_2)
    var a6 = (x2_2.toLong() * x4_2 + x3.toLong() * x3)
    var a7 = (x3.toLong() * x4_2)
    var a8 = (x4.toLong() * x4_2)

    val u1_2 = u1 * 2
    val u2_2 = u2 * 2
    val u3_2 = u3 * 2
    val u4_2 = u4 * 2

    val b0 = (u0.toLong() * u0)
    val b1 = (u0.toLong() * u1_2)
    val b2 = (u0.toLong() * u2_2 + u1.toLong() * u1)
    val b3 = (u1_2.toLong() * u2_2 + u0.toLong() * u3_2)
    val b4 = (u2.toLong() * u2_2 + u0.toLong() * u4_2 + u1.toLong() * u3_2)
    val b5 = (u1_2.toLong() * u4_2 + u2_2.toLong() * u3_2)
    val b6 = (u2_2.toLong() * u4_2 + u3.toLong() * u3)
    val b7 = (u3.toLong() * u4_2)
    val b8 = (u4.toLong() * u4_2)

    a0 -= b5 * 38
    a1 -= b6 * 38
    a2 -= b7 * 38
    a3 -= b8 * 38

    a5 -= b0
    a6 -= b1
    a7 -= b2
    a8 -= b3

    x0 += u0
    x1 += u1
    x2 += u2
    x3 += u3
    x4 += u4

    x1_2 = x1 * 2
    x2_2 = x2 * 2
    x3_2 = x3 * 2
    x4_2 = x4 * 2

    val c0 = (x0.toLong() * x0)
    val c1 = (x0.toLong() * x1_2)
    val c2 = (x0.toLong() * x2_2 + x1.toLong() * x1)
    val c3 = (x1_2.toLong() * x2_2 + x0.toLong() * x3_2)
    val c4 = (x2.toLong() * x2_2 + x0.toLong() * x4_2 + x1.toLong() * x3_2)
    val c5 = (x1_2.toLong() * x4_2 + x2_2.toLong() * x3_2)
    val c6 = (x2_2.toLong() * x4_2 + x3.toLong() * x3)
    val c7 = (x3.toLong() * x4_2)
    val c8 = (x4.toLong() * x4_2)

    var t = a8 + (c3 - a3)
    val z8 = t.toInt() and M26
    t = t shr 26
    t += (c4 - a4) - b4
    val z9 = t.toInt() and M25
    t = t shr 25
    t = a0 + (t + c5 - a5) * 38
    z[0] = t.toInt() and M26
    t = t shr 26
    t += a1 + (c6 - a6) * 38
    z[1] = t.toInt() and M26
    t = t shr 26
    t += a2 + (c7 - a7) * 38
    z[2] = t.toInt() and M25
    t = t shr 25
    t += a3 + (c8 - a8) * 38
    z[3] = t.toInt() and M26
    t = t shr 26
    t += a4 + b4 * 38
    z[4] = t.toInt() and M25
    t = t shr 25
    t += a5 + (c0 - a0)
    z[5] = t.toInt() and M26
    t = t shr 26
    t += a6 + (c1 - a1)
    z[6] = t.toInt() and M26
    t = t shr 26
    t += a7 + (c2 - a2)
    z[7] = t.toInt() and M25
    t = t shr 25
    t += z8.toLong()
    z[8] = t.toInt() and M26
    t = t shr 26
    z[9] = z9 + t.toInt()
}

private fun FE.Companion.sub(x: FE, y: FE, z: FE) {
    for (i in z.indices) {
        z[i] = x[i] - y[i]
    }
}

private inline operator fun Scalar.get(index: Int): Int = data[index]
private inline operator fun Scalar.set(index: Int, newValue: Int) { data[index] = newValue }
private inline val Scalar.indices: IntRange get() = data.indices
private inline val Scalar.sizeBytes: Int get() = data.size * Int.SIZE_BYTES

private fun Scalar.Companion.signed(z: Scalar) {
    val mask = -((z[0].inv() and 1) and 1).toLong() and 0xffffffff
    var c = 0L
    for (i in z.indices) {
        c += (z[i].toLong() and 0xffffffff) + (L[i].toLong() and mask)
        z[i] = c.toInt()
        c = c ushr 32
    }

    var d = 1
    for (i in z.indices.reversed()) {
        val ti = z[i]
        z[i] = (ti ushr 1) or (d shl 31)
        d = ti
    }
}

private fun Scalar.Companion.grouped(z: Scalar) {
    for (i in z.indices) {
        var s = z[i]
        s = s.permuteBits(11141290, 7)
        s = s.permuteBits(52428, 14)
        s = s.permuteBits(15728880, 4)
        s = s.permuteBits(65280, 8)
        z[i] = s
    }
}

private inline fun Int.permuteBits(m: Int, s: Int): Int {
    val t = (this xor (this ushr s)) and m
    return (t xor (t shl s)) xor this
}

private fun Scalar.Companion.encode(x: FE, z: Scalar) {
    encode128(x, 0, z, 0)
    encode128(x, 5, z, 4)
}

private inline fun Scalar.Companion.encode128(x: FE, xOff: Int, z: Scalar, zOff: Int) {
    val x0 = x[xOff + 0]
    val x1 = x[xOff + 1]
    val x2 = x[xOff + 2]
    val x3 = x[xOff + 3]
    val x4 = x[xOff + 4]

    z[zOff + 0] = (x0        ) or (x1 shl 26)
    z[zOff + 1] = (x1 ushr  6) or (x2 shl 20)
    z[zOff + 2] = (x2 ushr 12) or (x3 shl 13)
    z[zOff + 3] = (x3 ushr 19) or (x4 shl  7)
}

private fun Scalar.Companion.decode(x: Scalar, z: FE) {
    decode128(x, 0, z, 0)
    decode128(x, 4, z, 5)
    z[9] = z[9] and FE.M24
}

private inline fun Scalar.Companion.decode128(x: Scalar, xOff: Int, z: FE, zOff: Int) {
    val t0 = x[xOff + 0]
    val t1 = x[xOff + 1]
    val t2 = x[xOff + 2]
    val t3 = x[xOff + 3]

    z[zOff + 0] = ((t0                       )) and FE.M26
    z[zOff + 1] = ((t1 shl  6) or (t0 ushr 26)) and FE.M26
    z[zOff + 2] = ((t2 shl 12) or (t1 ushr 20)) and FE.M25
    z[zOff + 3] = ((t3 shl 19) or (t2 ushr 13)) and FE.M26
    z[zOff + 4] = (               (t3 ushr  7))
}

private fun Scalar.Companion.modOddInverse(x: Scalar, z: Scalar) {
    val bits = (P.data.size shl 5) - /* P.last().numberOfLeadingZeros() */ 1
    val len30 = (bits + 29) / 30

    val t = IntArray(4)
    val D = IntArray(len30)
    val E = IntArray(len30)
    val G = IntArray(len30)
    val M = IntArray(len30)

    E[0] = 1
    modEncode30(bits, x, G)
    modEncode30(bits, P, M)
    val F = M.copyOf()

    var theta = 0
    val m0Inv32 = modInverse32(M[0])
    val divStepsLimit = ((150964L * bits + 99243) ushr 16).toInt()

    var divSteps = 0
    while (divSteps < divStepsLimit) {
        theta = modDivSteps30(theta, F[0], G[0], t)
        modUpdateDE30(len30, D, E, t, m0Inv32, M)
        modUpdateFG30(len30, F, G, t)
        divSteps += 30
    }

    val signF = F[len30 - 1] shr 31
    modNegate30(len30, signF, F)
    modNormalize30(len30, signF, D, M)
    modDecode30(bits, D, z)

    t.fill(0)
    D.fill(0)
    E.fill(0)
    F.fill(0)
    G.fill(0)
    M.fill(0)
}

private fun Scalar.Companion.modDecode30(nbits: Int, x: IntArray, z: Scalar) {
    var bits = nbits
    var avail = 0
    var data = 0L

    var xOff = 0
    var zOff = 0
    while (bits > 0) {
        val availLimit = min(32, bits)
        while (avail < availLimit) {
            data = data or (x[xOff++].toLong() shl avail)
            avail += 30
        }

        z[zOff++] = data.toInt()
        data = data ushr 32
        avail -= 32
        bits -= 32
    }
}

private fun Scalar.Companion.modDivSteps30(ntheta: Int, f0: Int, g0: Int, t: IntArray): Int {
    var theta = ntheta
    var u = 1 shl 30
    var v = 0
    var q = 0
    var r = 1 shl 30
    var f = f0
    var g = g0

    @Suppress("UNUSED")
    for (i in 0..<30) {
        val c1 = theta shr 31
        val c2 = -(g and 1)

        val x = f xor c1
        val y = u xor c1
        val z = v xor c1

        g -= x and c2
        q -= y and c2
        r -= z and c2

        val c3 = c2 and c1.inv()
        theta = (theta xor c3) + 1

        f += g and c3
        u += q and c3
        v += r and c3

        g = g shr 1
        q = q shr 1
        r = r shr 1
    }

    t[0] = u
    t[1] = v
    t[2] = q
    t[3] = r
    return theta
}

private fun Scalar.Companion.modEncode30(nbits: Int, x: Scalar, z: IntArray) {
    var bits = nbits
    var avail = 0
    var data = 0L

    var xOff = 0
    var zOff = 0
    while (bits > 0) {
        val availLimit = min(30, bits)
        if (avail < availLimit) {
            data = data or ((x[xOff++].toLong() and M32) shl avail)
            avail += 32
        }

        z[zOff++] = data.toInt() and M30
        data = data ushr 30
        avail -= 30
        bits -= 30
    }
}

private inline fun Scalar.Companion.modInverse32(d: Int): Int {
    var x = d
    x *= 2 - d * x
    x *= 2 - d * x
    x *= 2 - d * x
    x *= 2 - d * x
    return x
}

private fun Scalar.Companion.modNegate30(len30: Int, cond: Int, F: IntArray) {
    var c = 0
    val last = len30 - 1
    for (i in 0..<last) {
        c += (F[i] xor cond) - cond
        F[i] = c and M30
        c = c shr 30
    }
    c += (F[last] xor cond) - cond
    F[last] = c
}

private fun Scalar.Companion.modNormalize30(len30: Int, condNegate: Int, D: IntArray, M: IntArray) {
    val last = len30 - 1

    run {
        var c = 0
        val condAdd = D[last] shr 31
        for (i in 0..<last) {
            var di = D[i] + (M[i] and condAdd)
            di = (di xor condNegate) - condNegate
            c += di
            D[i] = c and M30
            c = c shr 30
        }

        var di = D[last] + (M[last] and condAdd)
        di = (di xor condNegate) - condNegate
        c += di
        D[last] = c
    }

    run {
        var c = 0
        val condAdd = D[last] shr 31
        for (i in 0..<last) {
            val di = D[i] + (M[i] and condAdd)
            c += di
            D[i] = c and M30
            c = c shr 30
        }

        val di = D[last] + (M[last] and condAdd)
        c += di
        D[last] = c
    }
}

private fun Scalar.Companion.modUpdateDE30(len30: Int, D: IntArray, E: IntArray, t: IntArray, m0Inv32: Int, M: IntArray) {
    val u = t[0]
    val v = t[1]
    val q = t[2]
    val r = t[3]

    val sd = D[len30 - 1] shr 31
    val se = E[len30 - 1] shr 31

    var md = (u and sd) + (v and se)
    var me = (q and sd) + (r and se)

    var mi = M[0]
    var di = D[0]
    var ei = E[0]

    var cd = u.toLong() * di + v.toLong() * ei
    var ce = q.toLong() * di + r.toLong() * ei

    md -= (m0Inv32 * cd.toInt() + md) and M30
    me -= (m0Inv32 * ce.toInt() + me) and M30

    cd += mi.toLong() * md
    ce += mi.toLong() * me

    cd = cd shr 30
    ce = ce shr 30

    for (i in 1..<len30) {
        mi = M[i]
        di = D[i]
        ei = E[i]

        cd += u.toLong() * di + v.toLong() * ei + mi.toLong() * md
        ce += q.toLong() * di + r.toLong() * ei + mi.toLong() * me

        D[i - 1] = cd.toInt() and M30
        cd = cd shr 30
        E[i - 1] = ce.toInt() and M30
        ce = ce shr 30
    }

    D[len30 - 1] = cd.toInt()
    E[len30 - 1] = ce.toInt()
}

private fun Scalar.Companion.modUpdateFG30(len30: Int, F: IntArray, G: IntArray, t: IntArray) {
    val u = t[0]
    val v = t[1]
    val q = t[2]
    val r = t[3]

    var fi = F[0]
    var gi = G[0]

    var cf = u.toLong() * fi + v.toLong() * gi
    var cg = q.toLong() * fi + r.toLong() * gi

    cf = cf shr 30
    cg = cg shr 30

    for (i in 1..<len30) {
        fi = F[i]
        gi = G[i]

        cf += u.toLong() * fi + v.toLong() * gi
        cg += q.toLong() * fi + r.toLong() * gi

        F[i - 1] = cf.toInt() and M30
        cf = cf shr 30
        G[i - 1] = cg.toInt() and M30
        cg = cg shr 30
    }

    F[len30 - 1] = cf.toInt()
    G[len30 - 1] = cg.toInt()
}

private fun GE.add(p: GE.PreComp, r: GE.Accum, tmp: FE) {
    FE.apm(r.y, r.x, r.y, r.x)
    FE.mul(r.x, p.ymx, r.x)
    FE.mul(r.y, p.ypx, r.y)
    FE.mul(r.u, r.v, tmp)
    FE.mul(tmp, p.xyd, tmp)
    FE.apm(r.y, r.x, r.v, r.u)
    FE.apm(r.z, tmp, r.y, r.x)
    FE.mul(r.x, r.y, r.z)
    FE.mul(r.x, r.u, r.x)
    FE.mul(r.y, r.v, r.y)
}

private fun GE.double(r: GE.Accum) {
    FE.add(r.x, r.y, r.u)
    FE.sqr(r.x, r.x)
    FE.sqr(r.y, r.y)
    FE.sqr(r.z, r.z)
    FE.add(r.z, r.z, r.z)
    FE.apm(r.x, r.y, r.v, r.y)
    FE.sqr(r.u, r.u)
    FE.sub(r.v, r.u, r.u)
    FE.add(r.z, r.y, r.x)
    FE.carry(r.x)
    FE.mul(r.x, r.y, r.z)
    FE.mul(r.x, r.u, r.x)
    FE.mul(r.y, r.v, r.y)
}

private fun GE.neutral(r: GE.Accum) {
    FE.zero(r.x)
    FE.one(r.y)
    FE.one(r.z)
    FE.zero(r.u)
    FE.one(r.v)
}

private fun GE.scalarMultBase(sk: ByteArray, r: GE.Accum) {
    val g = Scalar()
    sk.lePackIntoUnsafe(
        dest = g.data,
        destOffset = 0,
        sourceIndexStart = 0,
        sourceIndexEnd = g.sizeBytes,
    )
    Scalar.signed(g)
    Scalar.grouped(g)

    val p = GE.PreComp()
    val tmp = FE()

    GE.neutral(r)

    var rSign = 0
    var rounds = (8 - 1) * 4
    while (true) {
        for (i in g.indices) {
            val w = g[i] ushr rounds
            val sign = (w ushr 3) and 1
            val abs = (w xor -sign) and 7

            for (j in g.indices) {
                val cond = ((j xor abs) - 1) shr 31
                val table = GE.PreComp.TABLE[(i * g.data.size) + j]
                FE.mov(cond, table.ymx, p.ymx)
                FE.mov(cond, table.ypx, p.ypx)
                FE.mov(cond, table.xyd, p.xyd)
            }

            FE.negate(rSign xor sign, r.x)
            FE.negate(rSign xor sign, r.u)
            rSign = sign

            GE.add(p, r, tmp)
        }

        rounds -= 4
        if (rounds < 0) break

        GE.double(r)
    }

    FE.negate(rSign, r.x)
    FE.negate(rSign, r.u)

    g.data.fill(0)
    FE.zero(p.ymx)
    FE.zero(p.ypx)
    FE.zero(p.xyd)
    FE.zero(tmp)
}

private fun GE.normalizeToAffine(p: GE.Accum, q: GE.Affine) {
    FE.inv(p.z, q.y)
    FE.mul(q.y, p.x, q.x)
    FE.mul(q.y, p.y, q.y)
    FE.normalize(q.x)
    FE.normalize(q.y)
}

private fun GE.checkPoint(q: GE.Affine): Int {
    val t = FE()
    val u = FE()
    val v = FE()

    FE.sqr(q.x, u)
    FE.sqr(q.y, v)
    FE.mul(u, v, t)
    FE.sub(u, v, u)
    FE.mul(t, d, t)
    FE.addOne(t)
    FE.add(t, u, t)
    FE.normalize(t)
    FE.normalize(v)

    val result = FE.isZero(t) and FE.isZero(v).inv()

    FE.zero(t)
    FE.zero(u)
    FE.zero(v)

    return result
}

private fun GE.checkPoint(p: GE.Accum): Int {
    val t = FE()
    val u = FE()
    val v = FE()
    val w = FE()

    FE.sqr(p.x, u)
    FE.sqr(p.y, v)
    FE.sqr(p.z, w)
    FE.mul(u, v, t)
    FE.sub(u, v, u)
    FE.mul(u, w, u)
    FE.sqr(w, w)
    FE.mul(t, d, t)
    FE.add(t, w, t)
    FE.add(t, u, t)
    FE.normalize(t)
    FE.normalize(v)
    FE.normalize(w)

    val result = FE.isZero(t) and FE.isZero(v).inv() and FE.isZero(w).inv()

    FE.zero(t)
    FE.zero(u)
    FE.zero(v)
    FE.zero(w)

    return result
}

private fun GE.encodePoint(p: GE.Affine, pk: ByteArray) {
    val g = Scalar()

    Scalar.encode(p.y, g)
    g.data.lePackIntoUnsafe(
        dest = pk,
        destOffset = 0,
        sourceIndexStart = 0,
        sourceIndexEnd = g.data.size,
    )
    pk[g.sizeBytes - 1] = (pk[g.sizeBytes - 1].toInt() or ((p.x[0] and 1) shl 7)).toByte()

    g.data.fill(0)
}

private val GE_00: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(32830087, 47169020, 19189462, 41450022, 11585671, 5024651, 47067768, 21392707, 27693491, 9116471),
    ypx = FE(1900465, 7757663, 7171466, 53753821, 29250856, 9893597, 42401889, 5482578, 31408990, 377968),
    xyd = FE(65282320, 20650811, 768173, 5158983, 17177380, 12042352, 16952742, 2689225, 20312634, 1300092),
) }
private val GE_01: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(75798, 1648568, 5666029, 44760135, 243540, 63804554, 2460880, 32622474, 10119720, 10605451),
    ypx = FE(22569082, 29237920, 11502419, 62237145, 14230115, 54478690, 9799903, 3970657, 54002464, 4054223),
    xyd = FE(24990052, 63240503, 5575771, 5593211, 12330322, 27432074, 50703209, 32681986, 19397519, 7228463),
) }
private val GE_02: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(32050772, 545219, 28582062, 15927876, 26649559, 40117151, 44712658, 29206259, 15732179, 2992802),
    ypx = FE(58498239, 30754781, 18687699, 13074752, 7162749, 56730783, 65908277, 13625433, 24510022, 13372848),
    xyd = FE(12621496, 22577329, 26545784, 36098149, 29420699, 23764679, 31386757, 8689492, 12746340, 13710490),
) }
private val GE_03: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(18796991, 12026204, 27560420, 15957519, 20870609, 51249035, 50281574, 6403545, 8936723, 6405852),
    ypx = FE(11698796, 38282872, 14167842, 19062660, 16484991, 23941531, 34430518, 24131322, 43036605, 13057076),
    xyd = FE(5022571, 36181108, 26596523, 38428905, 1327695, 43614500, 10547126, 8780137, 39855611, 13947528),
) }
private val GE_04: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(44240058, 23566434, 32242390, 12575269, 28769332, 57458830, 59074141, 16964477, 20589188, 4607459),
    ypx = FE(17409752, 39001507, 17940804, 26819894, 31798464, 23977889, 35333572, 11697298, 248943, 8024729),
    xyd = FE(18032506, 39633876, 23331783, 16734298, 7776966, 31157622, 24968584, 26849678, 27116346, 16478119),
) }
private val GE_05: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(13260403, 61952229, 17027966, 23357507, 23666286, 2518761, 12629657, 23452375, 13178401, 2020817),
    ypx = FE(64562104, 21098259, 30373891, 45634004, 2403913, 36391587, 17529284, 13866015, 7700971, 10509336),
    xyd = FE(44953680, 53434464, 27029391, 59210815, 29308806, 2007873, 24891095, 26351332, 66905093, 11065348),
) }
private val GE_06: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(6439479, 61410751, 26965215, 30010019, 30834324, 55247486, 58150962, 32161154, 30690300, 4614005),
    ypx = FE(49692164, 61367441, 30822005, 41434647, 5471729, 35812661, 59681585, 14990007, 41099152, 11705078),
    xyd = FE(15498330, 15556126, 13721318, 55534185, 688348, 3449611, 34775842, 32783993, 53537136, 14400864),
) }
private val GE_07: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(59232775, 11029838, 31947820, 29337843, 719116, 43920068, 64778151, 4031561, 55110619, 3743072),
    ypx = FE(10528769, 38837578, 2510333, 39945679, 1624031, 33255577, 17470631, 4281376, 513729, 1834364),
    xyd = FE(43802177, 10398366, 6393828, 63112743, 20408934, 37372061, 32546475, 31312458, 50955934, 12995460),
) }
private val GE_08: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(17299515, 61264213, 24054396, 15539681, 14850315, 45404565, 29719225, 12745582, 36145652, 8176702),
    ypx = FE(14665974, 23603330, 14875345, 49375960, 5727279, 47048233, 11803272, 20615100, 66776323, 15460959),
    xyd = FE(61429055, 17294962, 8838334, 36120876, 7292411, 26748988, 27670359, 6940906, 47258380, 8510272),
) }
private val GE_09: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(32291157, 33450683, 16597970, 34817952, 11264350, 26244927, 34766129, 9202811, 56781285, 9161140),
    ypx = FE(11709636, 37675927, 15792729, 7925529, 18448884, 36656891, 30213735, 27893069, 6603778, 767837),
    xyd = FE(52383873, 43446440, 13448619, 4908974, 19134357, 17731374, 16575176, 5524493, 46217808, 6033327),
) }
private val GE_10: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(58557509, 62503953, 5675222, 32071608, 28584198, 1872726, 22821408, 4360148, 19494656, 14529265),
    ypx = FE(49139418, 22148045, 18341563, 12381693, 31254861, 28994639, 63678211, 30946043, 51787007, 7028461),
    xyd = FE(59354300, 11430604, 15032292, 17801810, 21443686, 11002395, 9885147, 6623272, 15546878, 3935579),
) }
private val GE_11: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(28173374, 61694383, 24463576, 39715828, 26656993, 48740354, 51959114, 14322453, 15228242, 6054218),
    ypx = FE(46688596, 14556341, 4309920, 42051574, 19766132, 18891420, 16481217, 25362120, 47283693, 12811824),
    xyd = FE(8469714, 16365920, 28901835, 60291060, 854660, 29827759, 60641000, 6104928, 3800657, 15355175),
) }
private val GE_12: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(11631610, 19888794, 24258262, 65827094, 6896398, 59417137, 59604928, 28225560, 6614473, 594748),
    ypx = FE(53741466, 19237990, 12454635, 62661658, 10905350, 43303409, 44286275, 7631680, 33581673, 3878610),
    xyd = FE(36084385, 50132356, 30475335, 27415137, 19443283, 9696717, 926653, 11156296, 60286856, 15360669),
) }
private val GE_13: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(60447307, 16498768, 19624215, 39364111, 22782146, 59011384, 53753367, 1247567, 64737241, 6935526),
    ypx = FE(64082291, 44030132, 10342568, 10356702, 30832178, 36659271, 23194315, 14808403, 3458744, 16360452),
    xyd = FE(52109564, 20735915, 7161572, 66431789, 24209908, 57271364, 58300268, 25319694, 9289467, 5433663),
) }
private val GE_14: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(19368456, 3188300, 2332265, 53767294, 13899138, 11307082, 31654487, 20233195, 38693478, 9857452),
    ypx = FE(26446980, 61391106, 25176109, 26704360, 28702437, 7681820, 32647134, 24403088, 24433258, 1536966),
    xyd = FE(29667338, 22819505, 18857095, 60081687, 3679014, 61352063, 31961512, 16154476, 65903674, 4020640),
) }
private val GE_15: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(50982557, 63238870, 9242460, 66719747, 25322958, 60328916, 6801632, 16473963, 44268813, 8515769),
    ypx = FE(39949954, 27694037, 31831307, 46759157, 9713514, 28306087, 24813481, 21640784, 27866939, 8641855),
    xyd = FE(33291027, 323877, 25700024, 50609891, 20109333, 58466281, 10104779, 26989397, 1277840, 7684367),
) }
private val GE_16: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(52944766, 59998675, 15875946, 63879877, 17860218, 24398722, 33183071, 16532887, 61686678, 8989915),
    ypx = FE(66409307, 21792552, 27938483, 5484200, 25265872, 66257466, 58761545, 25316521, 58410777, 8181234),
    xyd = FE(50287592, 1792291, 2031678, 47614271, 7765884, 30840170, 379217, 26574621, 27564121, 14912294),
) }
private val GE_17: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(31733229, 26250845, 22520442, 54209327, 31155795, 28483308, 59623910, 1264916, 12405560, 12078037),
    ypx = FE(58795085, 4828892, 26638964, 53306194, 1472638, 37591445, 54122107, 8887361, 28414278, 3923983),
    xyd = FE(27606976, 6277399, 31175690, 20075938, 23964696, 54922267, 56185976, 12851658, 60016310, 1083937),
) }
private val GE_18: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(42585978, 33070369, 32574028, 31154157, 16847900, 26025645, 61578203, 7731739, 66821612, 6265666),
    ypx = FE(13623766, 53295589, 1695979, 30244292, 22201152, 39580213, 46165705, 18017208, 43645882, 5046145),
    xyd = FE(1971095, 12500729, 21728445, 64173857, 17605912, 30497287, 40931132, 31949732, 5214105, 521292),
) }
private val GE_19: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(37808, 1998248, 27858925, 53391183, 20682414, 36966960, 44579709, 26844450, 7403440, 5142677),
    ypx = FE(14379795, 45155995, 16362497, 16088669, 2906521, 25274598, 53894943, 10887949, 4737073, 11126263),
    xyd = FE(4986258, 67069524, 5938235, 25373995, 29333769, 13697470, 54316616, 31961825, 51475628, 16098916),
) }
private val GE_20: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(57051921, 25056498, 17133146, 51226792, 16140438, 36320944, 56658310, 13657982, 6617112, 2957403),
    ypx = FE(61382549, 13577953, 4729617, 30627789, 17930001, 25428251, 46938449, 13951162, 9162719, 15282828),
    xyd = FE(31742047, 46761722, 26378005, 22803508, 1313756, 38211624, 32951252, 32504217, 53595663, 15711043),
) }
private val GE_21: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(15645770, 65877444, 6443508, 13157048, 7942104, 2504729, 33881917, 7750192, 51863800, 908781),
    ypx = FE(54620987, 26358500, 11693225, 26750025, 1455361, 6212433, 47011929, 24482103, 50242235, 1405540),
    xyd = FE(24799957, 1786837, 30468289, 33987783, 5093790, 31455846, 38767466, 26187939, 57625507, 15425458),
) }
private val GE_22: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(40780998, 24028962, 21432393, 23768573, 3789025, 56238991, 9129926, 21582186, 1334095, 3410210),
    ypx = FE(7342015, 40060431, 24789172, 59304520, 170577, 8572014, 63107081, 9192892, 33079675, 15906617),
    xyd = FE(45096211, 12024789, 12315260, 19924317, 27898982, 46057467, 66279009, 29523248, 8855644, 6052841),
) }
private val GE_23: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(59714464, 37057341, 4967320, 6155811, 5546464, 28779776, 30575328, 13281368, 47720353, 5644612),
    ypx = FE(38481307, 66946074, 12593933, 11235995, 4669475, 37685740, 53907620, 27218780, 9440768, 5907102),
    xyd = FE(48318132, 6476298, 18188580, 50269638, 1030010, 34511134, 19181911, 21628539, 63958853, 1377197),
) }
private val GE_24: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(67097434, 28871591, 24207536, 8864330, 15856656, 55945426, 29439150, 27510932, 5475276, 8278987),
    ypx = FE(10856677, 47479186, 31411611, 55480119, 3067557, 47087427, 54469111, 16282764, 3740167, 12282803),
    xyd = FE(25775396, 530976, 31897102, 50515526, 29186879, 1978317, 5046932, 25066254, 19448382, 7827511),
) }
private val GE_25: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(39892474, 4154169, 2506210, 19751237, 18771135, 7233319, 39000065, 1972, 21311129, 13643112),
    ypx = FE(4283132, 49852052, 10980960, 41065811, 29072834, 16915276, 2038291, 4583373, 65330726, 4031482),
    xyd = FE(66951207, 55729206, 9764223, 913056, 16268533, 809138, 33661404, 20505512, 40283112, 9187707),
) }
private val GE_26: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(14267395, 46684682, 19137453, 55130204, 20715333, 11186196, 40613389, 13602912, 10121794, 8912897),
    ypx = FE(48475674, 8249654, 1935094, 24630223, 20891272, 60056045, 43717868, 557806, 22236629, 2834169),
    xyd = FE(14025082, 27330105, 25348846, 65805214, 13395786, 30886990, 52442476, 31308207, 12998473, 1993873),
) }
private val GE_27: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(50915804, 31718230, 27887030, 29410607, 25206601, 49010684, 2111630, 17336601, 52324985, 10814359),
    ypx = FE(66955208, 46907068, 8828175, 38152706, 14430725, 61534635, 41431191, 20322548, 26415910, 4660024),
    xyd = FE(42822884, 59197981, 8549363, 15793137, 23790765, 8194928, 43902387, 22667997, 303522, 11770659),
) }
private val GE_28: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(64966292, 7732475, 12316351, 14849555, 3266023, 18256044, 49583430, 13804874, 4230023, 9064781),
    ypx = FE(21796182, 30177093, 4839611, 8218000, 26179140, 47425123, 38952451, 33230342, 22311524, 9104467),
    xyd = FE(51438949, 19680924, 13955846, 39637884, 328111, 53331235, 50463257, 18854999, 7992779, 12663015),
) }
private val GE_29: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(1207228, 47064950, 1566851, 61690981, 27141186, 39945364, 21456306, 7588198, 66279588, 4578472),
    ypx = FE(55446421, 16798656, 17541761, 64281872, 2185683, 15715997, 15126474, 13894624, 23005207, 4236454),
    xyd = FE(10219928, 31863887, 18137190, 38180079, 19449839, 55269033, 17031518, 22244603, 2023784, 10717878),
) }
private val GE_30: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(37589992, 2809519, 441550, 28314391, 11365016, 28006323, 27657434, 33183897, 31632146, 10066753),
    ypx = FE(4899531, 29423970, 19029049, 49441065, 14584192, 18950177, 16378404, 30497393, 50149692, 5433962),
    xyd = FE(14363863, 8493489, 16055823, 65698503, 19479139, 16631363, 46154658, 25479675, 31464565, 9339943),
) }
private val GE_31: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(11061100, 51628239, 13605288, 49050263, 18217099, 51740435, 6352760, 11089987, 26231310, 13217018),
    ypx = FE(25457280, 46062527, 32424941, 8556010, 21720392, 18022908, 31889899, 22261196, 63916326, 16059492),
    xyd = FE(28879397, 65871490, 13687320, 38399916, 20148739, 10526071, 50771159, 31663805, 3545328, 14361526),
) }
private val GE_32: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(5638614, 25215252, 13675349, 42332830, 30439376, 23058274, 39132142, 12940372, 63756482, 1187996),
    ypx = FE(10519022, 2410346, 30009088, 17054681, 9678619, 18016864, 37394371, 13147370, 6002957, 14985264),
    xyd = FE(15166755, 28829113, 24136349, 31730683, 27645289, 57571066, 52172685, 26853455, 37139174, 5065597),
) }
private val GE_33: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(39493152, 12956208, 26375669, 64719332, 2915614, 19721284, 8189053, 30553824, 50609189, 5292906),
    ypx = FE(23677491, 33578904, 9090481, 14632024, 31854189, 65163962, 15878539, 20060928, 18990415, 5178573),
    xyd = FE(63941427, 41470148, 7709875, 327821, 5082736, 10393405, 4104133, 23183274, 18541296, 2836849),
) }
private val GE_34: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(51142187, 25277870, 29584052, 42728592, 13985553, 66438868, 59688472, 13337850, 39004415, 10198265),
    ypx = FE(7131057, 2617635, 12517657, 49655595, 7868234, 11038195, 25838892, 12467554, 25417094, 1872297),
    xyd = FE(43508135, 37324851, 20096051, 65337899, 13908223, 46124853, 9958704, 5604790, 299868, 15058045),
) }
private val GE_35: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(28267838, 37340939, 25741512, 61390916, 29310205, 35657540, 24632579, 9479444, 61015323, 9941220),
    ypx = FE(65385682, 32187361, 11591852, 50412026, 7177986, 48062999, 14202048, 5039670, 12060067, 14112586),
    xyd = FE(58954923, 6308703, 26719029, 17881860, 28711657, 55952746, 40168082, 18652937, 30551102, 10003259),
) }
private val GE_36: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(11531304, 65897743, 30857708, 32251597, 11118511, 18665572, 7620926, 26498482, 10119107, 9939288),
    ypx = FE(7114185, 56506406, 5252085, 54272059, 28019902, 55867034, 45658132, 14443400, 33253919, 10102248),
    xyd = FE(12148409, 48955705, 3312093, 21264581, 27937737, 44903512, 8677565, 5852755, 851808, 14745573),
) }
private val GE_37: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(20789158, 18461971, 16700592, 29585565, 28236509, 29678025, 14974224, 11811648, 60077218, 8558772),
    ypx = FE(4211398, 61477774, 24591485, 50707485, 22025419, 2544995, 49301978, 12485681, 10229860, 11989205),
    xyd = FE(17748862, 11669964, 25318068, 26230193, 13121827, 65405359, 29254068, 9447427, 38656694, 3195269),
) }
private val GE_38: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(55363684, 34418120, 27736514, 29076049, 18668256, 46667439, 42333523, 33253005, 45203697, 862794),
    ypx = FE(29223425, 4014016, 12477636, 25038947, 923627, 24667900, 11716509, 21652542, 44953967, 12508293),
    xyd = FE(51270293, 36282313, 3047700, 53324673, 23217341, 64039533, 43953287, 6174574, 41217742, 4569443),
) }
private val GE_39: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(56820720, 31340986, 3045501, 26761840, 15153101, 52826718, 44921485, 15040167, 11558154, 6460007),
    ypx = FE(66681851, 12994181, 1658540, 51011911, 22733942, 64403724, 36289020, 5272527, 2512850, 9919179),
    xyd = FE(26919961, 27306119, 30894706, 13555138, 28342591, 62404886, 19029313, 8402650, 37125015, 15638393),
) }
private val GE_40: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(1956724, 39407529, 13114909, 43381825, 11771730, 18484839, 40848634, 29114793, 59524741, 8235743),
    ypx = FE(18813014, 14677121, 26012991, 33226410, 7361102, 30354932, 17154247, 9257686, 4791160, 14593751),
    xyd = FE(2193094, 36419408, 9076016, 60785975, 7137672, 26650845, 41957632, 30874297, 36470009, 8681005),
) }
private val GE_41: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(38039411, 55996908, 30297246, 18624124, 14254120, 59227494, 1143002, 22585740, 31426756, 2894878),
    ypx = FE(8493137, 29905928, 11847417, 54281104, 30449398, 34326439, 58510795, 33332829, 49454207, 10575663),
    xyd = FE(10823693, 47619406, 18800559, 18600286, 28904118, 8568886, 33079941, 4264776, 24417818, 8526170),
) }
private val GE_42: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(51072984, 30312604, 33376853, 55036694, 32959446, 45767370, 4593545, 4086953, 50117958, 14617972),
    ypx = FE(11160430, 27419072, 11354696, 21854332, 33136801, 10059592, 17902603, 17478121, 57895232, 15994840),
    xyd = FE(55907271, 45896750, 14964280, 44579742, 21346993, 21576662, 6997182, 6173866, 31168607, 2361590),
) }
private val GE_43: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(21576612, 18844496, 6976089, 33884515, 13852291, 16709412, 18802898, 26304004, 46702137, 1919069),
    ypx = FE(28303164, 50642421, 14967745, 57756823, 15682161, 30744341, 4125015, 31183297, 7346649, 283921),
    xyd = FE(21098173, 58048163, 1382737, 41518475, 11117030, 61157240, 66081825, 20939803, 6949197, 12401618),
) }
private val GE_44: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(56476630, 50855262, 25341296, 50551570, 6847354, 20690350, 10138889, 6680691, 43305060, 7482714),
    ypx = FE(56319720, 61382432, 2818666, 8681272, 242843, 59938081, 51207680, 28607854, 47577637, 7739265),
    xyd = FE(44485554, 40608051, 4599522, 48803201, 7920788, 53045619, 33477842, 18780954, 27469451, 1514688),
) }
private val GE_45: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(9918155, 21199421, 1577345, 18418342, 142725, 30177194, 2338447, 30921062, 58534113, 3279369),
    ypx = FE(47876187, 53246131, 11890699, 28112400, 4393663, 31791874, 4442277, 27154488, 47849853, 1025369),
    xyd = FE(20167127, 36633026, 20748505, 22028143, 31817271, 13613449, 21802966, 18222588, 38702375, 3904722),
) }
private val GE_46: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(65187732, 38343190, 14951025, 32555123, 19921204, 10566554, 15684853, 26324950, 62131032, 3830216),
    ypx = FE(54008611, 2239331, 29134353, 48770027, 19731146, 52476993, 21851398, 13822802, 47355839, 11850165),
    xyd = FE(12615642, 6015825, 5540180, 60072928, 16710647, 15667599, 3017367, 23496395, 10799563, 4969461),
) }
private val GE_47: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(56671120, 53948637, 19889180, 64911159, 9297152, 62424991, 58900453, 4467163, 235747, 12496975),
    ypx = FE(25752211, 17934986, 19162095, 34837377, 6038272, 56053658, 40431753, 3636595, 64050141, 10490116),
    xyd = FE(26849156, 26191879, 25653252, 61934975, 5027996, 63028760, 12898199, 32627200, 5310192, 14657741),
) }
private val GE_48: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(66114584, 51475323, 4134173, 57483180, 27981320, 58115004, 6554177, 30376661, 29512525, 8124351),
    ypx = FE(58437131, 61461514, 4635458, 7508616, 29057578, 39752641, 28134305, 9167206, 9616773, 14954453),
    xyd = FE(61641564, 61801963, 19415226, 53578027, 23536533, 1602302, 45660726, 11863119, 35318033, 13522670),
) }
private val GE_49: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(38728553, 22640816, 28961743, 32289439, 31571434, 28759869, 62835013, 32637049, 31396754, 1867429),
    ypx = FE(49870270, 59456494, 19577281, 16465051, 2305349, 4790231, 21201271, 14536979, 49876354, 8347704),
    xyd = FE(21615553, 25301856, 33432095, 12372231, 13895667, 28320220, 9544520, 22748528, 65296797, 8039749),
) }
private val GE_50: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(57867191, 53793683, 14903127, 28715106, 11899711, 49131471, 34268135, 10721291, 9417416, 6499940),
    ypx = FE(62310917, 10319783, 19375026, 66614916, 1250363, 17424392, 64417951, 3017866, 9237121, 13642142),
    xyd = FE(36101140, 30308794, 22306762, 35949728, 19277078, 19125772, 49908261, 4968432, 4905473, 16554933),
) }
private val GE_51: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(23885893, 63352240, 3169740, 44545803, 24374207, 56009927, 23957575, 15286580, 39498873, 11393187),
    ypx = FE(40602374, 65158245, 8621958, 6059193, 7298906, 58456091, 51060009, 32123050, 36794017, 16249377),
    xyd = FE(29405871, 10996793, 8592303, 60679585, 2917142, 38566581, 33915244, 4525379, 5920609, 6709164),
) }
private val GE_52: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(44467833, 55825178, 13116063, 58397920, 7059570, 26994933, 42430282, 7488939, 58018447, 1297919),
    ypx = FE(48777432, 41627171, 7246288, 51212330, 25522229, 34982393, 46389437, 19434389, 10291883, 12469979),
    xyd = FE(49298477, 14331131, 7464793, 41868558, 28421360, 50645571, 61935731, 28607758, 51539969, 2436182),
) }
private val GE_53: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(37733943, 62759723, 27599725, 63715349, 26877466, 20917581, 48046375, 17941389, 52676843, 4442735),
    ypx = FE(19675067, 47399932, 15699207, 40638464, 26373741, 1008509, 30377327, 11187978, 7946863, 15518677),
    xyd = FE(46969092, 10647872, 7518643, 29700456, 27296232, 57619419, 33029121, 12753613, 15248681, 2499306),
) }
private val GE_54: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(43651004, 14280504, 8760866, 19827920, 11303339, 5194464, 7153269, 9533657, 11257590, 8480367),
    ypx = FE(29638897, 1200956, 18971901, 35553581, 6223722, 15526145, 8291743, 4754178, 6857274, 16154788),
    xyd = FE(1645217, 16672432, 17483117, 19067089, 2437753, 29738510, 39245077, 24891798, 16371316, 10578930),
) }
private val GE_55: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(58088126, 33802492, 32109185, 31436277, 27300636, 62460878, 46073332, 32575567, 27541026, 7043586),
    ypx = FE(24029401, 38704118, 18938612, 40612589, 9702089, 26551026, 21898699, 16718390, 45899022, 12711022),
    xyd = FE(45468122, 347163, 26379858, 66951837, 9510408, 12350461, 21553789, 12129888, 25474541, 15469114),
) }
private val GE_56: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(53555464, 48831452, 6552956, 61979322, 6241963, 29156571, 6934961, 7209782, 37591522, 2294081),
    ypx = FE(46879826, 40252406, 3606056, 5088286, 20024009, 66707997, 49225683, 31266013, 21902032, 6011136),
    xyd = FE(24661175, 60927053, 1831245, 28619523, 5852559, 13647479, 42224801, 28330244, 312202, 1768886),
) }
private val GE_57: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(52399133, 14384100, 30515114, 5909940, 31621386, 53205908, 53844062, 19142217, 13505138, 15211371),
    ypx = FE(7451939, 25200652, 26072864, 21751229, 17113468, 58439778, 7317903, 31699546, 7951920, 14792672),
    xyd = FE(18358910, 62851501, 23550443, 26898343, 9606153, 63176114, 50307498, 28376223, 47745503, 5836639),
) }
private val GE_58: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(46287573, 14772898, 27671874, 8531495, 19151183, 67036258, 22451198, 2441398, 6425900, 11322990),
    ypx = FE(11441587, 10554532, 589639, 66391101, 13670385, 53274153, 17183492, 6843247, 23315815, 5730676),
    xyd = FE(66586035, 9244848, 5533485, 18460968, 3088748, 10020356, 19376854, 27488584, 46600479, 1191766),
) }
private val GE_59: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(14525474, 31230582, 932047, 10808605, 33363540, 59644458, 3069090, 8472614, 25420321, 7788841),
    ypx = FE(25108761, 58657536, 3462970, 35515908, 12524958, 1991750, 7444314, 9411120, 4358981, 8492535),
    xyd = FE(15256478, 52001239, 10783018, 30617066, 26457693, 5414741, 27121994, 15133418, 1746307, 7039485),
) }
private val GE_60: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(10476379, 1674358, 24454045, 28435464, 4828212, 12222698, 51848389, 28336696, 65606988, 10777261),
    ypx = FE(50456758, 10643603, 27737683, 25140188, 9060696, 3181109, 63875079, 24887747, 30635474, 10197382),
    xyd = FE(14735701, 38828171, 12550812, 29223957, 30685364, 5007712, 1448360, 9912947, 35487326, 3942816),
) }
private val GE_61: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(24341193, 5649663, 18399874, 30090816, 14464584, 3098288, 65570110, 27792497, 44027591, 7670402),
    ypx = FE(7689682, 63895314, 20988868, 9849025, 12282763, 24922080, 17915893, 26937668, 50862761, 2290702),
    xyd = FE(859367, 46289092, 16621778, 7408261, 17105514, 66008343, 59256433, 8527213, 60117568, 6425369),
) }
private val GE_62: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(40043816, 18046088, 11345752, 6790728, 22948257, 64023835, 35255583, 18881554, 40603015, 2364546),
    ypx = FE(54384587, 11088990, 19786238, 32515135, 10121981, 19052205, 64809610, 30523297, 2262386, 11309701),
    xyd = FE(61229918, 66939410, 19669295, 9394143, 24533154, 6952040, 51351442, 21344831, 39942220, 11283719),
) }
private val GE_63: GE.PreComp by lazy(NONE) { GE.PreComp(
    ymx = FE(50973740, 14163671, 29104284, 8657859, 6406966, 49646054, 16498406, 20243264, 4082707, 7421893),
    ypx = FE(66658807, 3571545, 19595920, 26422711, 23765607, 33693295, 14390643, 18140564, 52328770, 9637919),
    xyd = FE(32534283, 34873778, 16135364, 20792806, 6593036, 63800372, 5288522, 30625130, 46826686, 14620353),
) }
