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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.service.internal

import kotlin.jvm.JvmInline

internal expect class ColorId
internal expect class DrawableId

@JvmInline
internal expect value class ColorRes private constructor(internal val id: ColorId) {

    public override fun toString(): String

    internal companion object {
        internal fun of(id: ColorId): ColorRes
    }
}

@JvmInline
internal expect value class DrawableRes private constructor(internal val id: DrawableId) {

    public override fun toString(): String

    internal companion object {
        internal fun of(id: DrawableId): DrawableRes
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ColorRes.commonToString(): String = "ColorRes[id=$id]"
@Suppress("NOTHING_TO_INLINE")
internal inline fun DrawableRes.commonToString(): String = "DrawableRes[id=$id]"
