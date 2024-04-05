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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "UnusedReceiverParameter")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply.Error.Companion.toError
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.coroutines.cancellation.CancellationException

internal fun TorCmdJob<*>.respond(replies: ArrayList<Reply>) {
    // waiters were destroyed while awaiting server response (i.e. EOS)
    if (replies.isEmpty()) {
        error(CancellationException("CtrlConnection Stream Ended"))
        return
    }

    val success = run {
        var areAllSuccess = true
        for (reply in replies) {
            if (reply is Reply.Success) continue
            areAllSuccess = false
            break
        }

        if (areAllSuccess) {
            @Suppress("UNCHECKED_CAST")
            replies as ArrayList<Reply.Success>
        } else {
            error(replies.toError())
            null
        }
    }

    if (success == null) return

    when (cmd) {
        is TorCmd.Authenticate -> cmd.complete(this)
        is TorCmd.Config.Get -> cmd.complete(this, success)
        is TorCmd.Config.Load -> cmd.complete(this)
        is TorCmd.Config.Reset -> cmd.complete(this)
        is TorCmd.Config.Save -> cmd.complete(this)
        is TorCmd.Config.Set -> cmd.complete(this)
        is TorCmd.DropGuards -> cmd.complete(this)
        is TorCmd.Hs.Fetch -> cmd.complete(this)
        is TorCmd.Info.Get -> cmd.complete(this, success)
        is TorCmd.MapAddress -> cmd.complete(this, success)
        is TorCmd.Onion.Add -> cmd.complete(this, success)
        is TorCmd.Onion.Delete -> cmd.complete(this, success)
        is TorCmd.OnionClientAuth.Add -> cmd.complete(this, success)
        is TorCmd.OnionClientAuth.Remove -> cmd.complete(this, success)
        is TorCmd.OnionClientAuth.View -> cmd.complete(this, success)
        is TorCmd.Ownership.Drop -> cmd.complete(this)
        is TorCmd.Ownership.Take -> cmd.complete(this)
        is TorCmd.Resolve -> cmd.complete(this)
        is TorCmd.SetEvents -> cmd.complete(this)
        is TorCmd.Signal.Halt -> cmd.complete(this)
        is TorCmd.Signal.Shutdown -> cmd.complete(this)
        is TorCmd.Signal.Active -> cmd.complete(this)
        is TorCmd.Signal.ClearDnsCache -> cmd.complete(this)
        is TorCmd.Signal.Debug -> cmd.complete(this)
        is TorCmd.Signal.Dormant -> cmd.complete(this)
        is TorCmd.Signal.Dump -> cmd.complete(this)
        is TorCmd.Signal.Heartbeat -> cmd.complete(this)
        is TorCmd.Signal.NewNym -> cmd.complete(this)
        is TorCmd.Signal.Reload -> cmd.complete(this)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Authenticate.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Get.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Load.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Reset.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Save.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Set.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.DropGuards.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Hs.Fetch.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Info.Get.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.MapAddress.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Onion.Add.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Onion.Delete.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    job.unsafeCast<Reply.Success>().completion(replies.first())
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.Add.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    job.unsafeCast<Reply.Success>().completion(replies.first())
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.Remove.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    job.unsafeCast<Reply.Success>().completion(replies.first())
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.View.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Ownership.Drop.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Ownership.Take.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Resolve.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.SetEvents.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Reload.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Dump.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Debug.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.NewNym.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.ClearDnsCache.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Heartbeat.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Active.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Dormant.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Shutdown.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Signal.Halt.complete(job: TorCmdJob<*>) { job.completeOK() }

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmdJob<*>.completeOK() { unsafeCast<Reply.Success.OK>().completion(Reply.Success.OK) }

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
private inline fun <T: Any> TorCmdJob<*>.unsafeCast(): TorCmdJob<T> = this as TorCmdJob<T>
