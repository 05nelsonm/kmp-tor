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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent

internal abstract class TorEventObserver private constructor(
    event: TorEvent,
    staticTag: String,
    private val notifyObservers: TorEvent.(data: String) -> Unit,
): TorEvent.Observer(
    event,
    staticTag,
    null,
    OnEvent.noOp(),
) {

    protected override fun notify(data: String) {
        notifyObservers(event, data)
    }

    internal abstract class ConfChanged internal constructor(
        staticTag: String,
        notifyObservers: TorEvent.(data: String) -> Unit,
    ): TorEventObserver(TorEvent.CONF_CHANGED, staticTag, notifyObservers) {

        protected final override fun notify(data: String) {
            // TODO: parse data

            super.notify(data)
        }
    }

    internal abstract class Notice internal constructor(
        staticTag: String,
        notifyObservers: TorEvent.(data: String) -> Unit,
    ): TorEventObserver(TorEvent.NOTICE, staticTag, notifyObservers) {

        protected final override fun notify(data: String) {
            // TODO: parse data

            super.notify(data)
        }
    }
}
