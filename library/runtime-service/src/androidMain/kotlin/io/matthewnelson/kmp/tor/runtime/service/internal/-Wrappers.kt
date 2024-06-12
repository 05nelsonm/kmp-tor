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
package io.matthewnelson.kmp.tor.runtime.service.internal

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build

@JvmInline
internal value class ColorRes private constructor(internal val id: Int) {

    @Throws(Resources.NotFoundException::class)
    internal fun retrieve(ctx: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+
            ctx.getColor(id)
        } else {
            // API 22-
            @Suppress("DEPRECATION")
            ctx.resources.getColor(id)
        }
    }

    internal companion object {

        internal val ZERO: ColorRes = ColorRes(0)

        internal fun of(id: Int): ColorRes {
            if (id == 0) return ZERO
            return ColorRes(id)
        }
    }
}

@JvmInline
internal value class DrawableRes private constructor(internal val id: Int) {

    @Throws(Resources.NotFoundException::class)
    internal fun retrieve(ctx: Context): Drawable {
        val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API 21+
            ctx.getDrawable(id)
        } else {
            // API 20-
            @Suppress("DEPRECATION")
            ctx.resources.getDrawable(id)
        }

        if (drawable == null) {
            throw Resources.NotFoundException("drawable[id=$id] was null")
        }

        return drawable
    }

    internal companion object {

        internal val ZERO: DrawableRes = DrawableRes(0)

        internal fun of(id: Int): DrawableRes {
            if (id == 0) return ZERO
            return DrawableRes(id)
        }
    }
}
