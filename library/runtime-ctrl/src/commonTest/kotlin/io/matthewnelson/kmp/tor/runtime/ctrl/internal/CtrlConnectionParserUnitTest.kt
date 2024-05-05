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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import kotlin.test.Test
import kotlin.test.assertEquals

class CtrlConnectionParserUnitTest {

    @Test
    fun givenEvent_whenMultiLine_thenIsAsExpected() {
        val p = TestParser(
            onNotify = { event, data ->
                assertEquals(TorEvent.NEWCONSENSUS, event)

                assertEquals(
                    """
                        l1
                        l2
                        l3
                    """.trimIndent(),
                    data,
                )
            }
        )

        p.parse("650+NEWCONSENSUS")
        p.parse("l1")
        p.parse("l2")
        p.parse("l3")
        p.parse(".") // end Multi-Line
        assertEquals(0, p.invocationNotify)
        p.parse("650 OK")
        assertEquals(1, p.invocationNotify)
    }

    @Test
    fun givenEvent_whenMultiResponse_thenIsAsExpected() {
        val p = TestParser(
            onNotify = { event, data ->
                assertEquals(TorEvent.CONF_CHANGED, event)

                assertEquals(
                    """
                        SocksPort=9055
                        DNSPort=1080
                    """.trimIndent(),
                    data
                )
            }
        )

        p.parse("650-CONF_CHANGED")
        p.parse("650-SocksPort=9055")
        p.parse("650-DNSPort=1080")
        assertEquals(0, p.invocationNotify)
        p.parse("650 OK")
        assertEquals(1, p.invocationNotify)
    }

    @Test
    fun givenEvent_whenMultiResponseNotEndOK_thenIsAsExpected() {
        val p = TestParser(
            onNotify = { event, data ->
                assertEquals(TorEvent.CIRC, event)

                assertEquals(
                    """
                    1000 EXTENDED moria1,moria2 0xBEEF
                    EXTRAMAGIC=99
                    ANONYMITY=high
                """.trimIndent(),
                    data
                )
            }
        )

        p.parse("650-CIRC 1000 EXTENDED moria1,moria2 0xBEEF")
        p.parse("650-EXTRAMAGIC=99")
        assertEquals(0, p.invocationNotify)
        p.parse("650 ANONYMITY=high")
        assertEquals(1, p.invocationNotify)
    }

    @Test
    fun givenEvent_whenSingleLine_thenIsAsExpected() {
        val expected = "1000 EXTENDED moria1,moria2"
        val p = TestParser(
            onNotify = { event, data ->
                assertEquals(TorEvent.CIRC, event)
                assertEquals(expected, data)
            }
        )

        p.parse("650 CIRC $expected")
        assertEquals(1, p.invocationNotify)
        p.parse("650 CIRC $expected")
        assertEquals(2, p.invocationNotify)
    }

    @Test
    fun givenResponse_whenMultiLineKvp_thenIsAsExpected() {
        val response = mutableListOf<ArrayList<Reply>>()
        val p = TestParser(
            onRespond = { replies ->
                response.add(replies)
            }
        )

        // e.g. GETINFO version desc/name/moria desc/name/moria2
        p.parse("250+desc/name/moria=")
        p.parse("[Descriptor1 for moria]")
        p.parse("[Descriptor2 for moria]")
        p.parse(".") // End
        p.parse("250-version=Tor 0.4.8.10")
        p.parse("250+desc/name/moria2=")
        p.parse("[Descriptor1 for moria2]")
        p.parse("[Descriptor2 for moria2]")
        p.parse(".") // End
        assertEquals(0, p.invocationRespond)
        p.parse("250 OK")
        assertEquals(1, p.invocationRespond)

        response[0].let { r1 ->
            assertEquals(
                """
                    desc/name/moria=[Descriptor1 for moria]
                    [Descriptor2 for moria]
                """.trimIndent(),
                r1[0].message,
            )

            assertEquals(
                "version=Tor 0.4.8.10",
                r1[1].message,
            )

            assertEquals(
                """
                    desc/name/moria2=[Descriptor1 for moria2]
                    [Descriptor2 for moria2]
                """.trimIndent(),
                r1[2].message,
            )

            assertEquals(
                "OK",
                r1[3].message,
            )
        }
    }

    private class TestParser(
        private val onError: (details: String) -> Unit = {},
        private val onNotify: (event: TorEvent, data: String) -> Unit = { _, _ ->},
        private val onRespond: (replies: ArrayList<Reply>) -> Unit = {},
    ): CtrlConnection.Parser() {

        var invocationError = 0
            private set
        var invocationNotify = 0
            private set
        var invocationRespond = 0
            private set

        override fun onError(details: String) {
            invocationError++
            onError.invoke(details)
        }

        override fun TorEvent.notify(data: String) {
            invocationNotify++
            onNotify.invoke(this, data)
        }

        override fun ArrayList<Reply>.respond() {
            invocationRespond++
            onRespond.invoke(this)
        }
    }
}
