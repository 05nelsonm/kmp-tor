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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.internal.observer.newTorCmdObserver
import io.matthewnelson.kmp.tor.runtime.internal.observer.observeSignalNewNymInternal
import io.matthewnelson.kmp.tor.runtime.internal.process.TorDaemon
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Events specific to [TorRuntime]
 *
 * e.g.
 *
 *     val runtime = TorRuntime.Builder(myEnvironment) {
 *         observerStatic(ERROR) { t ->
 *             t.printStackTrace()
 *         }
 *         observerStatic(EXECUTE.ACTION) { job ->
 *             println(job)
 *         }
 *         // ...
 *     }
 *
 *     val observerState = STATE.observer(
 *         tag = "1234",
 *         executor = OnEvent.Executor.Main,
 *         onEvent = { state -> println(state) },
 *     )
 *     runtime.subscribe(observerState)
 *     runtime.unsubscribe(observerState)
 *
 * @see [Observer]
 * @see [Event.observer]
 * @see [Processor]
 * @see [RuntimeEvent.Companion]
 * */
public sealed class RuntimeEvent<Data: Any> private constructor(
    name: String
): Event<Data, RuntimeEvent<Data>, RuntimeEvent.Observer<Data>>(name) {

    /**
     * Errors encountered by [TorRuntime].
     *
     * All exceptions encountered when notifying other non-[ERROR]
     * observers (including [TorEvent] observers) are piped to [ERROR]
     * observers as [UncaughtException].
     *
     * **NOTE:** Any exceptions thrown by [ERROR] observers are re-thrown
     * as [UncaughtException] (if not already one). This will likely crash
     * the program.
     *
     * **NOTE:** If the error is an [UncaughtException] and no observers
     * for [ERROR] are subscribed with the [TorRuntime], the [UncaughtException]
     * will be thrown and likely crash the program. It is **critical** that an
     * [ERROR] observer be registered either via [TorRuntime.Builder.observerStatic],
     * or immediately after [TorRuntime] is instantiated via [TorRuntime.subscribe].
     * */
    public data object ERROR: RuntimeEvent<Throwable>("ERROR")

    public data object EXECUTE {

        /**
         * The current [Action] that is being executed by [TorRuntime].
         *
         * Useful for reacting to specific jobs via attachment of an
         * [ActionJob.invokeOnCompletion] handler.
         *
         * e.g.
         *
         *     EXECUTE.ACTION.observer { job ->
         *         if (!job.isStop) return@observer
         *         job.invokeOnCompletion {
         *             if (job.isSuccess) {
         *                 // do something
         *             }
         *         }
         *     }
         *
         * @see [ActionJob]
         * */
        public data object ACTION: RuntimeEvent<ActionJob>("EXECUTE_ACTION")

        /**
         * The current [TorCmd] that is being executed by [TorRuntime].
         *
         * Useful for reacting to specific jobs via attachment of an
         * [TorCmdJob.invokeOnCompletion] handler.
         *
         * e.g.
         *
         *     EXECUTE.CMD.observer { job ->
         *         if (job.cmd != TorCmd.Onion.Add::class) return@observer
         *         job.invokeOnCompletion {
         *             if (job.isSuccess) {
         *                 // do something
         *             }
         *         }
         *     }
         *
         * @see [TorCmdJob]
         * @see [observeSignalNewNym]
         * */
        public data object CMD: RuntimeEvent<TorCmdJob>("EXECUTE_CMD") {

            /**
             * Subscribes with provided [Processor] a [CMD] observer
             * which will intercept execution of all [TorCmd.Signal.NewNym]
             * jobs in order to transform tor's generic server response
             * of [Reply.Success.OK].
             *
             * The generic server response only indicates that tor has
             * accepted the command. Anything tor does as a result of
             * that command is (typically) dispatched as [TorEvent.NOTICE].
             *
             * Specific to [TorCmd.Signal.NewNym], if tor accepted the
             * signal, it may or may not dispatch a [TorEvent.NOTICE]
             * indicating that it was rate-limited. This observer handles
             * that transformation and notifies the provided [onEvent]
             * callback whenever there is a successful execution of
             * [TorCmd.Signal.NewNym] with either:
             *
             *  - `null` indicating tor accepted the signal and did **not**
             *    rate-limit it.
             *  - The rate-limit notice itself.
             *
             * e.g.
             *
             *     val disposable = myTorRuntime.observeSignalNewNym(
             *         "my tag",
             *         null,
             *     ) { rateLimiting ->
             *         println(rateLimiting ?: "You've changed Tor identities!")
             *     }
             *
             *     try {
             *         myTorRuntime.startDaemonAsync()
             *         myTorRuntime.executeAsync(TorCmd.Signal.NewNym)
             *         myTorRuntime.executeAsync(TorCmd.Signal.NewNym)
             *         myTorRuntime.executeAsync(TorCmd.Signal.NewNym)
             *         myTorRuntime.executeAsync(TorCmd.Signal.NewNym)
             *     } finally {
             *         disposable.dispose()
             *     }
             *
             *     // You've changed Tor identities!
             *     // Rate limiting NEWNYM request: delaying by 10 second(s)
             *     // Rate limiting NEWNYM request: delaying by 10 second(s)
             *     // Rate limiting NEWNYM request: delaying by 10 second(s)
             *
             * @return [Disposable] to unsubscribe the observer
             * @param [tag] A string to help grouping/identifying observer(s)
             * @param [executor] The thread context in which [onEvent] will be
             *   invoked in. If `null` and [Processor] is an instance of
             *   [TorRuntime], it will use whatever was declared via
             *   [TorRuntime.Environment.Builder.defaultEventExecutor]. Otherwise,
             *   [OnEvent.Executor.Immediate] will be used.
             * @param [onEvent] The callback to pass the data to.
             * */
            @JvmStatic
            public fun Processor.observeSignalNewNym(
                tag: String?,
                executor: OnEvent.Executor?,
                onEvent: OnEvent<String?>,
            ): Disposable = newTorCmdObserver(
                tag,
                executor,
                onEvent,
                ::observeSignalNewNymInternal,
            )
        }
    }

    /**
     * Events pertaining to an object's lifecycle.
     *
     * e.g. (println observer for [LIFECYCLE], [EXECUTE.ACTION])
     *
     *     Lifecycle.Event[obj=RealTorRuntime[fid=6E96…6985]@1625090026, name=onCreate]
     *     StartJob[name=StartDaemon, state=Executing]@1652817137
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@158078174, name=onCreate]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@158078174, name=onStart]
     *     Lifecycle.Event[obj=RealTorCtrl[fid=6E96…6985]@1682130731, name=onCreate]
     *     RestartJob[name=RestartDaemon, state=Executing]@1830373812
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@85303615, name=onCreate]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@158078174, name=onStop]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@158078174, name=onDestroy]
     *     Lifecycle.Event[obj=RealTorCtrl[fid=6E96…6985]@1682130731, name=onDestroy]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@85303615, name=onStart]
     *     Lifecycle.Event[obj=RealTorCtrl[fid=6E96…6985]@1241844104, name=onCreate]
     *     StopJob[name=StopDaemon, state=Executing]@560446930
     *     Lifecycle.Event[obj=RealTorCtrl[fid=6E96…6985]@1241844104, name=onDestroy]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@85303615, name=onStop]
     *     Lifecycle.Event[obj=TorDaemon[fid=6E96…6985]@85303615, name=onDestroy]
     *
     * @see [Lifecycle.Event]
     * */
    public data object LIFECYCLE: RuntimeEvent<Lifecycle.Event>("LIFECYCLE")

    /**
     * Events pertaining to listeners which tor has opened.
     *
     * Observers are notified with [TorListeners] once tor bootstraps
     * to the network. Subsequently, if a listener is opened or closed
     * (e.g. a configuration change), observers will be notified with
     * the updated information.
     *
     * When tor is stopped or the network disabled, observers will
     * be notified with [TorListeners] whereby [TorListeners.isEmpty] is
     * true (all listeners are closed or closing).
     *
     * e.g. (println observers for events [STATE], [LISTENERS], [PROCESS.READY])
     *
     *     TorState[fid=6E96…6985, daemon=On{95%}, network=Enabled]
     *     TorState[fid=6E96…6985, daemon=On{100%}, network=Enabled]
     *     TorListeners[fid=6E96…6985]: [
     *         dns: []
     *         http: []
     *         socks: [
     *             127.0.0.1:35607
     *         ]
     *         socksUnix: []
     *         trans: []
     *     ]
     *     Tor[fid=6E96…6985] IS READY
     *     TorState[fid=6E96…6985, daemon=On{100%}, network=Disabled]
     *     TorListeners[fid=6E96…6985]: [
     *         dns: []
     *         http: []
     *         socks: []
     *         socksUnix: []
     *         trans: []
     *     ]
     *
     * @see [TorListeners]
     * @see [TorRuntime.listeners]
     * */
    public data object LISTENERS: RuntimeEvent<TorListeners>("LISTENERS")

    /**
     * Logging for [TorRuntime] internals.
     * */
    public sealed class LOG private constructor(name: String): RuntimeEvent<String>(name) {

        /**
         * Debug level logging. Events will only be dispatched
         * when [TorRuntime.Environment.debug] is set to `true`.
         *
         * **NOTE:** Debug logs may reveal sensitive information
         * and should not be enabled in production!
         * */
        public data object DEBUG: LOG("LOG_DEBUG")

        /**
         * Info level logging.
         * */
        public data object INFO: LOG("LOG_INFO")

        /**
         * Warn level logging. These are non-fatal errors.
         * */
        public data object WARN: LOG("LOG_WARN")
    }

    /**
     * Events pertaining to the tor process.
     *
     * Observers are notified with single line output for **all**
     * [PROCESS] events, even [STDOUT] and [STDERR]. On Jvm & Native,
     * [kmp-process](https://github.com/05nelsonm/kmp-process) dispatches
     * lines on its own separate background threads; 1 for [STDOUT], 1
     * for [STDERR].
     * */
    public sealed class PROCESS private constructor(name: String): RuntimeEvent<String>(name) {

        /**
         * Indicates that the tor process has completed bootstrapping, and
         * the network is enabled. All [READY] observers will be notified a
         * **single** time per process start completion. When [Action.StopDaemon]
         * or [Action.RestartDaemon] executed, [READY] observers will be notified
         * again in the same manner for the new process instance.
         *
         * This is useful for triggering single execution events.
         *
         * **NOTE:** Toggling DisableNetwork on/off does **not** affect this. A
         * single notification will be dispatched the first time bootstrapping
         * completes and network is enabled.
         *
         * e.g. (println observers for events [STATE], [LISTENERS], [PROCESS.READY])
         *
         *     TorState[fid=6E96…6985, daemon=Starting, network=Disabled]
         *     TorState[fid=6E96…6985, daemon=On{0%}, network=Disabled]
         *     TorState[fid=6E96…6985, daemon=On{0%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{5%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{10%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{14%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{15%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{75%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{90%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{95%}, network=Enabled]
         *     TorState[fid=6E96…6985, daemon=On{100%}, network=Enabled]
         *     TorListeners[fid=6E96…6985]: [
         *         dns: []
         *         http: []
         *         socks: [
         *             127.0.0.1:35607
         *         ]
         *         socksUnix: []
         *         trans: []
         *     ]
         *     Tor[fid=6E96…6985] IS READY
         *     TorState[fid=6E96…6985, daemon=On{100%}, network=Disabled]
         *     TorListeners[fid=6E96…6985]: [
         *         dns: []
         *         http: []
         *         socks: []
         *         socksUnix: []
         *         trans: []
         *     ]
         *     TorState[fid=6E96…6985, daemon=On{100%}, network=Enabled]
         *     TorListeners[fid=6E96…6985]: [
         *         dns: []
         *         http: []
         *         socks: [
         *             127.0.0.1:38255
         *         ]
         *         socksUnix: []
         *         trans: []
         *     ]
         *     TorState[fid=6E96…6985, daemon=Stopping, network=Enabled]
         *     TorListeners[fid=6E96…6985]: [
         *         dns: []
         *         http: []
         *         socks: []
         *         socksUnix: []
         *         trans: []
         *     ]
         *     TorState[fid=6E96…6985, daemon=Off, network=Disabled]
         * */
        public data object READY: PROCESS("PROCESS_READY")

        /**
         * Lines output from tor's [Process] [Stdio.Config.stdout] stream.
         * */
        public data object STDOUT: PROCESS("PROCESS_STDOUT")

        /**
         * Lines output by tor's [Process] [Stdio.Config.stderr] stream.
         * */
        public data object STDERR: PROCESS("PROCESS_STDERR")
    }

    /**
     * Events pertaining to the current state of [TorRuntime].
     *
     * e.g. (println observers for events [STATE], [LISTENERS], [PROCESS.READY])
     *
     *     TorState[fid=6E96…6985, daemon=On{95%}, network=Enabled]
     *     TorState[fid=6E96…6985, daemon=On{100%}, network=Enabled]
     *     TorListeners[fid=6E96…6985]: [
     *         dns: []
     *         http: []
     *         socks: [
     *             127.0.0.1:35607
     *         ]
     *         socksUnix: []
     *         trans: []
     *     ]
     *     Tor[fid=6E96…6985] IS READY
     *     TorState[fid=6E96…6985, daemon=On{100%}, network=Disabled]
     *     TorListeners[fid=6E96…6985]: [
     *         dns: []
     *         http: []
     *         socks: []
     *         socksUnix: []
     *         trans: []
     *     ]
     *
     * @see [TorState]
     * */
    public data object STATE: RuntimeEvent<TorState>("STATE")

    /**
     * Model to be registered with a [Processor] for being notified
     * via [OnEvent] invocation with [RuntimeEvent] data.
     *
     * @see [Event.Observer]
     * @see [Processor]
     * */
    public open class Observer<Data: Any>(
        event: RuntimeEvent<Data>,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): Event.Observer<Data, RuntimeEvent<Data>>(
        event,
        tag,
        executor,
        onEvent,
    )

    /**
     * Base interface for implementations that process [RuntimeEvent].
     * */
    public interface Processor {

        /**
         * Add a single [Observer].
         * */
        public fun subscribe(observer: Observer<*>)

        /**
         * Add multiple [Observer].
         * */
        public fun subscribe(vararg observers: Observer<*>)

        /**
         * Remove a single [Observer].
         * */
        public fun unsubscribe(observer: Observer<*>)

        /**
         * Remove multiple [Observer].
         * */
        public fun unsubscribe(vararg observers: Observer<*>)

        /**
         * Remove all [Observer] of a single [RuntimeEvent].
         * */
        public fun unsubscribeAll(event: RuntimeEvent<*>)

        /**
         * Remove all [Observer] of multiple [RuntimeEvent].
         * */
        public fun unsubscribeAll(vararg events: RuntimeEvent<*>)

        /**
         * Remove all [Observer] with the given [tag].
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], all [TorEvent.Observer] with
         * the given [tag] will also be removed.
         * */
        public fun unsubscribeAll(tag: String)

        /**
         * Remove all non-static [Observer] that are currently
         * registered.
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], all [TorEvent.Observer]
         * will also be removed.
         * */
        public fun clearObservers()
    }

    public companion object: Entries<RuntimeEvent<*>>(numEvents = 12) {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public override fun valueOf(name: String): RuntimeEvent<*> {
            return super.valueOf(name)
        }

        @JvmStatic
        public override fun valueOfOrNull(name: String): RuntimeEvent<*>? {
            return super.valueOfOrNull(name)
        }

        @JvmStatic
        public override fun entries(): Set<RuntimeEvent<*>> {
            return super.entries()
        }

        protected override val lazyEntries: ThisBlock<LinkedHashSet<RuntimeEvent<*>>> = ThisBlock {
            // NOTE: Update numEvents when adding an event
            add(ERROR); add(EXECUTE.ACTION); add(EXECUTE.CMD); add(LIFECYCLE);
            add(LISTENERS); add(LOG.DEBUG); add(LOG.INFO); add(LOG.WARN);
            add(PROCESS.READY); add(PROCESS.STDOUT); add(PROCESS.STDERR); add(STATE);
        }
    }

    /**
     * Helper for selectively exposing the ability to notify observers
     * externally of the [TorRuntime] implementation.
     * */
    public interface Notifier {

        public fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data)

        public companion object {

            /**
             * [LOG.DEBUG] level logging. Will prefix [log] with [from]
             * string value and a space (' '), if [from] is non-null.
             * */
            @JvmStatic
            public fun Notifier.d(from: Any?, log: String) {
                notify(LOG.DEBUG, from.appendLog(log))
            }

            /**
             * [LOG.INFO] level logging. Will prefix [log] with [from]
             * string value and a space (' '), if [from] is non-null.
             * */
            @JvmStatic
            public fun Notifier.i(from: Any?, log: String) {
                notify(LOG.INFO, from.appendLog(log))
            }

            /**
             * [LOG.WARN] level logging. Will prefix [log] with [from]
             * string value and a space (' '), if [from] is non-null.
             * */
            @JvmStatic
            public fun Notifier.w(from: Any?, log: String) {
                notify(LOG.WARN, from.appendLog(log))
            }

            /**
             * [ERROR] level logging.
             * */
            @JvmStatic
            public fun Notifier.e(cause: Throwable) {
                notify(ERROR, cause)
            }

            /**
             * [LIFECYCLE] event logging.
             * */
            @JvmStatic
            public fun Notifier.lce(event: Lifecycle.Event) {
                notify(LIFECYCLE, event)
            }

            @JvmSynthetic
            internal fun Notifier.stdout(from: TorDaemon, line: String) {
                notify(PROCESS.STDOUT, from.appendLog(line))
            }

            @JvmSynthetic
            internal fun Notifier.stderr(from: TorDaemon, line: String) {
                notify(PROCESS.STDERR, from.appendLog(line))
            }

            @Suppress("NOTHING_TO_INLINE")
            private inline fun Any?.appendLog(log: String): String {
                return this?.toString()
                    ?.ifBlank { null }
                    ?.let { "$it $log" }
                    ?: log
            }
        }
    }

    protected final override fun factory(
        event: RuntimeEvent<Data>,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): Observer<Data> = Observer(event, tag, executor, onEvent)
}
