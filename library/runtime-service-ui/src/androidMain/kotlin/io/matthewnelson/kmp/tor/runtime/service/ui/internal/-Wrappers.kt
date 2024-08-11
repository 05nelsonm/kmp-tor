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
package io.matthewnelson.kmp.tor.runtime.service.ui.internal

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.Bitmap.Config
import android.graphics.drawable.Drawable

@JvmInline
internal value class ColorInt internal constructor(internal val argb: Int) {
    public override fun toString(): String = "ColorInt[argb=$argb]"
}

@JvmInline
internal value class ColorRes internal constructor(internal val id: Int) {
    public override fun toString(): String = "ColorRes[id=$id]"
}

@JvmInline
internal value class DrawableRes internal constructor(internal val id: Int) {

    @Throws(Resources.NotFoundException::class)
    internal fun toBitmap(
        context: Context,
        tint: ColorInt,
        dpSize: Int,
    ): Bitmap {
        require(dpSize > 0) { "dpSize must be greater than 0" }

        val appContext = context.applicationContext
        val pxSize = (dpSize * appContext.resources.displayMetrics.density).toInt()

        return appContext
            .retrieveDrawable(this)
            .toBitmap(pxSize)
            .applyTint(pxSize, tint)
    }

    private fun Drawable.toBitmap(pxSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(pxSize, pxSize, Config.ARGB_8888)
        setBounds(0, 0, pxSize, pxSize)
        draw(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.applyTint(pxSize: Int, color: ColorInt): Bitmap {
        val source = this
        val paint = Paint()
        paint.setColorFilter(PorterDuffColorFilter(color.argb, PorterDuff.Mode.SRC_IN))

        val result = Bitmap.createBitmap(pxSize, pxSize, Config.ARGB_8888)
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    public override fun toString(): String = "DrawableRes[id=$id]"
}
