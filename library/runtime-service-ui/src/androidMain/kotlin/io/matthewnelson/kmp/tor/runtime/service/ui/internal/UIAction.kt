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

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Factory
import io.matthewnelson.kmp.tor.runtime.service.ui.R

internal enum class UIAction {
    NewNym,
    Restart,
    Stop,
    Previous,
    Next;

    internal class Icons private constructor(b: Factory.Builder) {

        private val icons = UIAction.entries.mapTo(ArrayList(UIAction.entries.size)) { entry ->
            when (entry) {
                NewNym -> b.iconActionNewNym ?: R.drawable.ic_kmp_tor_ui_action_newnym
                Restart -> b.iconActionRestart ?: R.drawable.ic_kmp_tor_ui_action_restart
                Stop -> b.iconActionStop ?: R.drawable.ic_kmp_tor_ui_action_stop
                Previous -> b.iconActionPrevious ?: R.drawable.ic_kmp_tor_ui_action_previous
                Next -> b.iconActionNext ?: R.drawable.ic_kmp_tor_ui_action_next
            }.let { id -> DrawableRes(id) }
        }.toImmutableList()

        internal operator fun get(action: UIAction): DrawableRes = icons[action.ordinal]

        internal companion object {

            @JvmSynthetic
            internal fun of(
                b: Factory.Builder
            ): Icons = Icons(b)
        }
    }
}
