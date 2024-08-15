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
package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Model of a reply from a control connection as described in the
 * [control-spec](https://torproject.gitlab.io/torspec/control-spec/#replies).
 *
 * @see [of]
 * @see [Success]
 * @see [Error]
 * */
public open class Reply private constructor(
    @JvmField
    public val status: String,
    @JvmField
    public val message: String,
) {

    public operator fun component1(): String = status
    public operator fun component2(): String = message

    public companion object {

        @JvmStatic
        public fun of(status: String, message: String): Reply = Success.of(status, message)
    }

    /**
     * A more constrained [Reply] which **ONLY** holds replies
     * that have a status starting with the digit `2`, indicating
     * a positive completion of [TorCmd] via the control connection.
     *
     * e.g.
     *
     *     torCtrl.executeAsync(TorCmd.Signal.Dump)
     *     // 250 OK
     *
     *     torCtrl.executeAsync(TorCmd.OnionClientAuth.Remove(someAddress))
     *     // 251 Client credentials for "HSAddress" did not exist.
     *
     * @see [OK]
     * */
    public open class Success private constructor(
        status: String,
        message: String,
    ): Reply(status, message) {

        @get:JvmName("isOK")
        public val isOK: Boolean get() = this is OK

        /**
         * The most common kind of reply off of a control connection,
         * indicating that the [TorCmd] has been "accepted" by tor.
         *
         * **NOTE:** Some [TorCmd] that only return [OK] may require
         * further listening for a specific [TorEvent] to determine
         * actual success or failure of the command that was executed,
         * such as if you were rate limited or not when issuing
         * [TorCmd.Signal.NewNym].
         * */
        public data object OK: Success("250", "OK")

        internal companion object {

            @JvmSynthetic
            internal fun of(status: String, message: String): Reply {
                if (message.equals(OK.message, ignoreCase = true)) {
                    if (status == OK.status) return OK
                    if (status == OK_650.status) return OK_650
                }

                return if (status.startsWith('2')) {
                    Success(status, message)
                } else {
                    Reply(status, message)
                }
            }

            // Asynchronous Event Notification (TorEvent)
            // lazy b/c referencing Success.OK
            private val OK_650: Reply by lazy { Reply("650", OK.message) }
        }
    }

    /**
     * An exception for replies returned by the control connection which
     * contain status codes starting with `3`, `4`, or `5`, as described in the
     * [control-spec](https://torproject.gitlab.io/torspec/control-spec/#replies).
     *
     * [replies] are guaranteed to have at least 1 [Reply].
     *
     * @see [toError]
     * */
    public class Error private constructor(
        @JvmField
        public val jobName: String,
        replies: List<Reply>
    ): RuntimeException() {

        @JvmField
        public val replies: List<Reply> = replies.toImmutableList()

        public override val message: String = buildString {
            append("jobName: ")
            append(jobName)
            appendLine()

            append("replies: [")
            for (reply in this@Error.replies) {
                appendLine()
                append("    ")
                append(reply)
            }

            appendLine()
            append(']')
        }

        public companion object {

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun List<Reply>.toError(jobName: String): Error {
                require(isNotEmpty()) { "replies cannot be empty" }
                return Error(jobName, this)
            }
        }
    }

    /** @suppress */
    public final override fun equals(other: Any?): Boolean {
        return  other is Reply
                && other.status == status
                && other.message == message
    }

    /** @suppress */
    public final override fun hashCode(): Int {
        var result = 17
        result = result * 31 + status.hashCode()
        result = result * 31 + message.hashCode()
        return result
    }

    /** @suppress */
    public final override fun toString(): String = "$status $message"
}
