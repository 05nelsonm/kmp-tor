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

import android.app.PendingIntent
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Factory
import io.matthewnelson.kmp.tor.runtime.service.ui.R

internal enum class UIAction {
    NewNym,
    Restart,
    Stop,
    Previous,
    Next;

    internal class Icons private constructor(b: Factory.Builder) {

        private val icons = IntArray(UIAction.entries.size) { i ->
            when (UIAction.entries.elementAt(i)) {
                NewNym -> b.iconActionNewNym ?: R.drawable.ic_kmp_tor_ui_action_newnym
                Restart -> b.iconActionRestart ?: R.drawable.ic_kmp_tor_ui_action_restart
                Stop -> b.iconActionStop ?: R.drawable.ic_kmp_tor_ui_action_stop
                Previous -> b.iconActionPrevious ?: R.drawable.ic_kmp_tor_ui_action_previous
                Next -> b.iconActionNext ?: R.drawable.ic_kmp_tor_ui_action_next
            }
        }

        internal operator fun get(action: UIAction): DrawableRes = DrawableRes(icons.elementAt(action.ordinal))

        internal companion object {

            @JvmSynthetic
            internal fun of(
                b: Factory.Builder
            ): Icons = Icons(b)
        }
    }

    internal class View private constructor(
        private val action: UIAction,
        private val pallet: UIColor.Pallet,
        private val enabled: Boolean,
    ) {

        private var remoteView: RemoteViews? = null

        internal class Cache private constructor(
            private val appContext: Context,
            private val icons: Icons,
            private val pIntent: (UIAction?) -> PendingIntent,
        ) {

            private val cache = arrayOfNulls<View?>(UIAction.entries.size)

            internal fun getOrCreate(
                action: UIAction,
                pallet: UIColor.Pallet,
                enabled: Boolean,
            ): RemoteViews {
                val new = View(action, pallet, enabled)
                val cached = cache[action.ordinal]
                if (cached == new) {
                    cached.remoteView?.let { return it }
                }

                val view = new.build()
                new.remoteView = view
                cache[action.ordinal] = new
                return view
            }

            internal fun clearCache() {
                UIAction.entries.forEach { cache[it.ordinal] = null }
            }

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    context: Context,
                    icons: Icons,
                    pIntent: (UIAction?) -> PendingIntent,
                ): Cache = Cache(
                    context.applicationContext,
                    icons,
                    pIntent
                )
            }

            private fun View.build(): RemoteViews {
                val layoutId = if (enabled) {
                    R.layout.kmp_tor_ui_action_enabled
                } else {
                    R.layout.kmp_tor_ui_action_disabled
                }

                val pIntent = if (enabled) pIntent(action) else pIntent(null)

                val view = RemoteViews(appContext.packageName, layoutId)
                view.setOnClickPendingIntent(R.id.kmp_tor_ui_action, pIntent)

                val iconRes = icons[action]
                val imageResId = R.id.kmp_tor_ui_action_image

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val icon = Icon.createWithResource(appContext, iconRes.id)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        view.setColorStateList(imageResId, "setImageTintList", pallet.notNight, pallet.night)
                    } else {
                        icon.setTint(pallet.notNight.defaultColor)
                        icon.setTintMode(PorterDuff.Mode.SRC_IN)
                    }

                    view.setImageViewIcon(imageResId, icon)
                } else {
                    val color = ColorInt(pallet.notNight.defaultColor)
                    val bitmap = iconRes.toIconBitmap(appContext, color, dpSize = 24)
                    view.setImageViewBitmap(imageResId, bitmap)
                }

                return view
            }
        }

        public override fun equals(other: Any?): Boolean {
            return  other is View
                    && other.action == action
                    && other.pallet == pallet
                    && other.enabled == enabled
        }

        public override fun hashCode(): Int {
            var result = 17
            result = result * 42 + action.hashCode()
            result = result * 42 + pallet.hashCode()
            result = result * 42 + enabled.hashCode()
            return result
        }
    }
}
