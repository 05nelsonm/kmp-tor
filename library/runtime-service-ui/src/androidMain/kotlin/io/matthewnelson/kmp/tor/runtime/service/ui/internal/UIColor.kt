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
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import io.matthewnelson.kmp.tor.runtime.service.ui.R

internal class UIColor private constructor(
    private val appContext: Context,
    private val devContext: ContextThemeWrapper,
    private val defaultNotNight: Int,
    private val defaultNight: Int,
) {

    internal data class Pallet(internal val notNight: ColorStateList, internal val night: ColorStateList)

    private var appThemeHashCode: Int? = appContext.theme?.hashCode()
    private var isReadyAttributeDefined: Boolean? = null
    private var palletNotReady: Pallet? = null

    internal operator fun get(state: ColorState): Pallet {
        if (state is ColorState.Ready) {
            appContext.theme?.let { appTheme ->

                val hashCode = appTheme.hashCode()
                if (hashCode != appThemeHashCode) {
                    isReadyAttributeDefined = null
                    appThemeHashCode = null
                }

                if (isReadyAttributeDefined != false) {
                    val t = TypedValue()
                    val wasFound = appTheme.resolveAttribute(R.attr.kmp_tor_ui_color_ready, t, true)

                    if (
                        wasFound
                        && t.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && t.type <= TypedValue.TYPE_LAST_COLOR_INT
                    ) {
                        isReadyAttributeDefined = true
                        val csl = ColorStateList.valueOf(t.data)
                        return Pallet(csl, csl)
                    }

                    isReadyAttributeDefined = false
                }
            }
        }

        palletNotReady?.let { return it }

        var ta: TypedArray? = null
        try {
            val attrs = intArrayOf(
                android.R.attr.textColorSecondaryInverse,   // Not Night
                android.R.attr.textColorSecondary,          // Night
            )
            ta = devContext.obtainStyledAttributes(attrs)

            var notNight = ta.getColorStateList(0)
            @Suppress("ResourceType")
            var night = ta.getColorStateList(1)

            val setGlobal = notNight != null && night != null

            if (notNight == null) {
                notNight = ColorStateList.valueOf(defaultNotNight)
            }
            if (night == null) {
                night = ColorStateList.valueOf(defaultNight)
            }

            val result = Pallet(notNight, night)
            if (setGlobal) {
                palletNotReady = result
            }

            return result
        } catch (_: Throwable) {
            // "Should" never happen
        } finally {
            ta?.recycle()
        }

        return Pallet(ColorStateList.valueOf(defaultNotNight), ColorStateList.valueOf(defaultNight))
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            context: Context,
            defaultNotNight: Int = Color.DKGRAY,
            defaultNight: Int = Color.GRAY,
        ): UIColor {
            val appContext = context.applicationContext
            val devContext = ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault)

            return UIColor(appContext, devContext, defaultNotNight, defaultNight)
        }
    }
}
