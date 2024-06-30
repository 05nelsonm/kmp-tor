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

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentText
import kotlin.jvm.JvmSynthetic

internal class State private constructor(
    actions: Set<ButtonAction>,
    internal val color: ColorState,
    internal val icon: IconState,
    internal val progress: Progress,
    internal val text: ContentText<*>,
    internal val title: TorState.Daemon,
    internal val fid: String,
) {

    internal val actions: Set<ButtonAction> = actions.toImmutableSet()

    internal fun copy(
        actions: Set<ButtonAction> = this.actions,
        color: ColorState = this.color,
        icon: IconState = this.icon,
        progress: Progress = this.progress,
        text: ContentText<*> = this.text,
        title: TorState.Daemon = this.title,
    ): State {
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

        return State(
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
        return  other is State
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
        append("State[fid=")
        append(fid)
        appendLine("]: [")

        append("    actions: [")
        if (actions.isEmpty()) {
            appendLine(']')
        } else {
            actions.forEach { action ->
                appendLine()
                append("        ")
                append(action)
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
        appendLine(title)

        append(']')
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            fid: FileID,
        ): State = State(
            emptySet(),
            ColorState.NotReady,
            IconState.NetworkDisabled,
            Progress.Indeterminate,
            ContentAction.of(Action.StartDaemon),
            TorState.Daemon.Off,
            fid.fidEllipses,
        )
    }
}
