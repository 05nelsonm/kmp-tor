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
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import io.matthewnelson.kmp.tor.runtime.service.ui.R

internal class UIColor private constructor(
    private val appContext: Context,
    private val sysResources: Resources,
    private val defaultNotNight: Int,
    private val defaultYesNight: Int,
) {

    internal data class Pallet(
        internal val notNight: ColorStateList,
        internal val yesNight: ColorStateList,
        internal val isUIModeNight: Boolean,
    ) {

        internal val default: ColorInt = (if (isUIModeNight) yesNight else notNight)
            .defaultColor
            .let { ColorInt(it) }
    }

    private var appThemeHashCode: Int? = appContext.theme?.hashCode()
    private var isReadyAttributeDefined: Boolean? = null
    private var palletNotReady: Pallet? = null

    internal operator fun get(state: ColorState): Pallet {
        if (state is ColorState.Ready) {
            appContext.theme?.let { appTheme ->

                val hashCode = appTheme.hashCode()
                if (hashCode != appThemeHashCode) {
                    isReadyAttributeDefined = null
                    appThemeHashCode = hashCode
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

                        // Always set isUIModeNight to true here b/c
                        // ColorStateList is the same, so it doesn't
                        // matter which is chosen from for RemoteViews.
                        return Pallet(csl, csl, isUIModeNight = true)
                    }

                    isReadyAttributeDefined = false
                }
            }
        }

        return getOrCreatePalletNotReady()
    }

    private fun getOrCreatePalletNotReady(): Pallet {
        val isUIModeNight = sysResources.isUIModeNight()

        palletNotReady?.let { pallet ->
            return if (pallet.isUIModeNight == isUIModeNight) {
                pallet
            } else {
                val copy = pallet.copy(isUIModeNight = isUIModeNight)
                palletNotReady = copy
                copy
            }
        }

        var ta: TypedArray? = null
        try {
            val wrapper = ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault)
            val attrs = intArrayOf(
                android.R.attr.textColorSecondaryInverse,   // Not Night
                android.R.attr.textColorSecondary,          // Night
            )
            ta = wrapper.obtainStyledAttributes(attrs)

            var notNight = ta.getColorStateList(0)
            @Suppress("ResourceType")
            var yesnight = ta.getColorStateList(1)

            val setGlobal = notNight != null && yesnight != null

            if (notNight == null) {
                notNight = ColorStateList.valueOf(defaultNotNight)
            }
            if (yesnight == null) {
                yesnight = ColorStateList.valueOf(defaultYesNight)
            }

            val result = Pallet(notNight, yesnight, isUIModeNight)
            if (setGlobal) {
                palletNotReady = result
            }

            return result
        } catch (_: Throwable) {
            // "Should" never happen
        } finally {
            ta?.recycle()
        }

        return Pallet(
            ColorStateList.valueOf(defaultNotNight),
            ColorStateList.valueOf(defaultYesNight),
            isUIModeNight,
        )
    }

    private fun Resources.isUIModeNight(): Boolean {
        val current = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return current == Configuration.UI_MODE_NIGHT_YES
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            context: Context,
            sysResources: Resources = Resources.getSystem(),
            defaultNotNight: Int = Color.DKGRAY,
            defaultYesNight: Int = Color.GRAY,
        ): UIColor = UIColor(
            context.applicationContext,
            sysResources,
            defaultNotNight,
            defaultYesNight,
        )
    }
}
