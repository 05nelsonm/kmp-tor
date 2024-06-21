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
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import kotlin.jvm.JvmSynthetic

internal class NotificationState private constructor(
    actions: Set<ButtonAction>,
    internal val color: ColorRes,
    internal val contentText: CharSequence,
    internal val contentTitle: String,
    internal val progress: Progress,
    internal val icon: DrawableRes,
    internal val fid: String,
) {

    internal val actions: Set<ButtonAction> = actions.toImmutableSet()

    @Throws(IllegalArgumentException::class)
    internal fun diff(
        old: NotificationState,
        setColor: (ColorRes) -> Unit,
        setContentText: (CharSequence) -> Unit,
        setContentTitle: (String) -> Unit,
        setProgress: (Progress) -> Unit,
        setIcon: (DrawableRes) -> Unit
    ) {
        require(old.fid == fid) { "old[fid=${old.fid}] does not match new[fid=${fid}]" }

        if (old.color != color) {
            setColor(color)
        }
        if (old.contentText != contentText) {
            setContentText(contentText)
        }
        if (old.contentTitle != contentTitle) {
            setContentTitle(contentTitle)
        }
        if (old.progress != progress) {
            setProgress(progress)
        }
        if (old.icon != icon) {
            setIcon(icon)
        }
    }

    internal fun isActionRefreshNeeded(old: Set<ButtonAction>): Boolean {
        return old.size != actions.size
    }

    internal fun copy(
        actions: Set<ButtonAction> = this.actions,
        color: ColorRes = this.color,
        contentText: CharSequence = this.contentText,
        contentTitle: String = this.contentTitle,
        progress: Progress = this.progress,
        icon: DrawableRes = this.icon
    ): NotificationState {
        if (
            actions == this.actions
            && color == this.color
            && contentText == this.contentText
            && contentTitle == this.contentTitle
            && progress == this.progress
            && icon == this.icon
        ) {
            return this
        }

        return NotificationState(
            actions,
            color,
            contentText,
            contentTitle,
            progress,
            icon,
            fid,
        )
    }

    public override fun equals(other: Any?): Boolean {
        return  other is NotificationState
                && other.fid == fid
                && other.actions == actions
                && other.color == color
                && other.contentText == contentText
                && other.contentTitle == contentTitle
                && other.progress == progress
                && other.icon == icon
    }

    public override fun hashCode(): Int {
        var result = 17
        result = result * 42 + fid.hashCode()
        result = result * 42 + actions.hashCode()
        result = result * 42 + color.hashCode()
        result = result * 42 + contentText.hashCode()
        result = result * 42 + contentTitle.hashCode()
        result = result * 42 + progress.hashCode()
        result = result * 42 + icon.hashCode()
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
        append("    contentText: ")
        appendLine(contentText)
        append("    contentTitle: ")
        appendLine(contentTitle)
        append("    progress: ")
        appendLine(progress)
        append("    icon: ")
        appendLine(icon)
        append(']')
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            actions: Set<ButtonAction>,
            color: ColorRes,
            contentText: String,
            contentTitle: String,
            progress: Progress,
            icon: DrawableRes,
            fid: FileID,
        ): NotificationState = NotificationState(
            actions,
            color,
            contentText,
            contentTitle,
            progress,
            icon,
            fid.fidEllipses,
        )
    }
}
