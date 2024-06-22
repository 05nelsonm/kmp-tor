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

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentText
import kotlin.jvm.JvmSynthetic

internal class NotificationState private constructor(
    actions: Set<ButtonAction>,
    internal val color: ColorRes,
    internal val icon: DrawableRes,
    internal val progress: Progress,
    internal val text: ContentText<*>,
    internal val title: TorState.Daemon,
    internal val fid: String,
) {

    internal val actions: Set<ButtonAction> = actions.toImmutableSet()

    internal fun diff(
        other: NotificationState,
        setColor: (ColorRes) -> Unit,
        setContentText: (ContentText<*>) -> Unit,
        setContentTitle: (TorState.Daemon) -> Unit,
        setProgress: (Progress) -> Unit,
        setIcon: (DrawableRes) -> Unit
    ) {
        if (other.color != color) {
            setColor(color)
        }
        if (other.icon != icon) {
            setIcon(icon)
        }
        if (other.progress != progress) {
            setProgress(progress)
        }
        if (other.text != text) {
            setContentText(text)
        }
        if (other.title != title) {
            setContentTitle(title)
        }
    }

    internal fun isActionRefreshNeeded(other: NotificationState): Boolean {
        return other.fid != fid ||  other.actions.size != actions.size
    }

    internal fun copy(
        actions: Set<ButtonAction> = this.actions,
        color: ColorRes = this.color,
        icon: DrawableRes = this.icon,
        progress: Progress = this.progress,
        text: ContentText<*> = this.text,
        title: TorState.Daemon = this.title,
    ): NotificationState {
        if (
            actions == this.actions
            && color == this.color
            && icon == this.icon
            && progress == this.progress
            && text == this.text
            && title == this.title
        ) {
            return this
        }

        return NotificationState(
            actions,
            color,
            icon,
            progress,
            text,
            title,
            fid,
        )
    }

    public override fun equals(other: Any?): Boolean {
        return  other is NotificationState
                && other.fid == fid
                && other.actions == actions
                && other.color == color
                && other.icon == icon
                && other.progress == progress
                && other.text == text
                && other.title == title
    }

    public override fun hashCode(): Int {
        var result = 17
        result = result * 42 + fid.hashCode()
        result = result * 42 + actions.hashCode()
        result = result * 42 + color.hashCode()
        result = result * 42 + icon.hashCode()
        result = result * 42 + progress.hashCode()
        result = result * 42 + text.hashCode()
        result = result * 42 + title.hashCode()
        return result
    }

    public override fun toString(): String = buildString {
        append("NotificationState[fid=")
        append(fid)
        appendLine("]: [")

        append("    actions: [")
        if (actions.isEmpty()) {
            appendLine(']')
        } else {
            actions.forEach { action ->
                appendLine()
                append("        ")
                append(action.name)
            }
            appendLine()
            appendLine("    ]")
        }

        append("    color: ")
        appendLine(color)
        append("    icon: ")
        appendLine(icon)
        append("    progress: ")
        appendLine(progress)
        append("    text: ")
        appendLine(text)

        append("    title: ")
        when (title) {
            is TorState.Daemon.Off -> "TorState.Daemon.Off"
            is TorState.Daemon.On -> "TorState.Daemon.On{${title.bootstrap}}"
            is TorState.Daemon.Starting -> "TorState.Daemon.Starting"
            is TorState.Daemon.Stopping -> "TorState.Daemon.Stopping"
        }.let { appendLine(it) }

        append(']')
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            actions: Set<ButtonAction>,
            color: ColorRes,
            icon: DrawableRes,
            progress: Progress,
            text: ContentText<*>,
            title: TorState.Daemon,
            fid: FileID,
        ): NotificationState = NotificationState(
            actions,
            color,
            icon,
            progress,
            text,
            title,
            fid.fidEllipses,
        )
    }
}
