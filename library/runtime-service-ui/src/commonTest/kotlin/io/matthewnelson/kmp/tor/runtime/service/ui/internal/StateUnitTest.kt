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

import io.matthewnelson.kmp.tor.runtime.FileID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateUnitTest {

    @Test
    fun givenState_whenToString_thenIsAsExpected() {
        val state = State.of(object : FileID {
            override val fid: String = "abcdefg12345678"
        }).copy(actions = setOf(ButtonAction.NewIdentity))

        assertEquals(
            """
                State[fid=abcd…5678]: [
                    actions: [
                        ButtonAction.NewIdentity
                    ]
                    color: ColorState.NotReady
                    icon: IconState.NetworkDisabled
                    progress: Progress.Indeterminate
                    text: ContentAction[value=StartDaemon]
                    title: TorState.Daemon.Off
                ]
            """.trimIndent(),
            state.toString(),
        )

        assertTrue(state.copy(actions = emptySet()).toString().contains("    actions: []"))
    }
}
