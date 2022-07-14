/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager.internal.notification

import io.matthewnelson.kmp.tor.manager.internal.wrappers.ColorRes
import io.matthewnelson.kmp.tor.manager.internal.wrappers.DrawableRes

internal data class NotificationState(
    val actions: List<Action>,
    val color: ColorRes,
    val contentText: String,
    val contentTitle: String,
    val progress: Progress,
    val smallIcon: DrawableRes
) {

    internal fun diff(
        old: NotificationState,
        setColor: (ColorRes) -> Unit,
        setContentText: (String) -> Unit,
        setContentTitle: (String) -> Unit,
        setProgress: (Progress) -> Unit,
        setSmallIcon: (DrawableRes) -> Unit,
    ) {
        if (old.color != color) {
            setColor.invoke(color)
        }
        if (old.contentText != contentText) {
            setContentText.invoke(contentText)
        }
        if (old.contentTitle != contentTitle) {
            setContentTitle.invoke(contentTitle)
        }
        if (old.progress != progress) {
            setProgress.invoke(progress)
        }
        if (old.smallIcon != smallIcon) {
            setSmallIcon.invoke(smallIcon)
        }
    }

    internal fun isActionRefreshNeeded(old: List<Action>): Boolean {
        return old.size != actions.size
    }

    internal enum class Action(val requestCode: Int) {
        NewIdentity(1),
        RestartTor(2),
        StopTor(3);
    }

    internal sealed interface Progress {
        @JvmInline
        value class Determinant(val value: Int): Progress
        object Indeterminate: Progress
        object None: Progress
    }
}
