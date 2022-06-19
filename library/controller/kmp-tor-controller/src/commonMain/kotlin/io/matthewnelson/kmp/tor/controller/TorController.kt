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
package io.matthewnelson.kmp.tor.controller

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.address.ProxyAddress
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.common.server.Server
import io.matthewnelson.kmp.tor.common.util.TorStrings.CLRF
import io.matthewnelson.kmp.tor.common.util.TorStrings.MULTI_LINE_END
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.internal.*
import io.matthewnelson.kmp.tor.controller.internal.controller.*
import io.matthewnelson.kmp.tor.controller.internal.controller.ListenersHandler
import io.matthewnelson.kmp.tor.controller.internal.controller.Waiter
import io.matthewnelson.kmp.tor.controller.internal.io.ReaderWrapper
import io.matthewnelson.kmp.tor.controller.internal.io.SocketWrapper
import io.matthewnelson.kmp.tor.controller.internal.io.WriterWrapper
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

/**
 * Connects to Tor via it's control port in order to facilitate
 * asynchronous communication.
 *
 * Upon connecting, [TorController] will run continuously until
 * Tor has been shutdown.
 *
 * @see [RealTorController]
 * @see [TorControlProcessor]
 * @see [RealTorControlProcessor]
 * */
expect interface TorController: TorControlProcessor, TorEventProcessor<TorEvent.SealedListener> {
    val isConnected: Boolean

    fun disconnect()

    /**
     * Callback will be made upon disconnect, and directly afterwards
     * the reference to the callback cleared.
     *
     * @see [RealTorController.RealControlPortInteractor]'s init block
     * */
    @ExperimentalTorApi
    fun onDisconnect(action: ((TorController) -> Unit)?)

    companion object {

        /**
         * Opens a TCP connection to Tor's control port at the given [address]
         * */
        @Throws(TorControllerException::class)
        suspend fun newInstance(address: ProxyAddress): TorController

        /**
         * Opens a unix domain socket to Tor's control port at the give [Path]
         * */
        @Throws(TorControllerException::class)
        suspend fun newInstance(unixSocket: Path): TorController
    }
}

@JvmSynthetic
@OptIn(ExperimentalCoroutinesApi::class)
internal fun realTorController(
    reader: ReaderWrapper,
    writer: WriterWrapper,
    socket: SocketWrapper,
    dispatchers: CloseableCoroutineDispatcher
): TorController =
    RealTorController(reader, writer, socket, dispatchers)

@OptIn(
    InternalTorApi::class,
    ExperimentalTorApi::class,
    ExperimentalCoroutinesApi::class,
)
@Suppress("CanBePrimaryConstructorProperty")
private class RealTorController(
    reader: ReaderWrapper,
    writer: WriterWrapper,
    socket: SocketWrapper,
    dispatchers: CloseableCoroutineDispatcher,
): TorController, Debuggable {

    private val debugger: AtomicRef<((DebugItem) -> Unit)?> = atomic(null)
    private val listeners: ListenersHandler = ListenersHandler.newInstance {
        debugger.safeInvoke(it)
    }
    private val socket: SocketWrapper = socket
    private val onDisconnect: AtomicRef<((TorController) -> Unit)?> = atomic(null)

    @Suppress("SpellCheckingInspection")
    private val controlPortInteractor: ControlPortInteractor by lazy {
        RealControlPortInteractor(
            reader,
            writer,
            dispatchers,
        )
    }

    private val processorDelegate: TorControlProcessor by lazy {
        TorControlProcessor.newInstance(controlPortInteractor)
    }

    private class WaitersHolder {
        private val list = mutableListOf<Waiter>()
        private val lock = Mutex()
        suspend fun <T> withLock(action: suspend MutableList<Waiter>.() -> T): T =
            lock.withLock {
                action.invoke(list)
            }
    }

    @Suppress("SpellCheckingInspection")
    private inner class RealControlPortInteractor(
        reader: ReaderWrapper,
        writer: WriterWrapper,
        dispatchers: CloseableCoroutineDispatcher,
    ): ControlPortInteractor {

        private val dispatchers: CloseableCoroutineDispatcher = dispatchers
        private val supervisor = SupervisorJob()
        private val scope = CoroutineScope(context =
            CoroutineName(name = "TorController.Scope") +
            supervisor                                  +
            dispatchers
        )
        private val reader: ReaderWrapper = reader
        private val writer: WriterWrapper = writer
        private val waiters: WaitersHolder = WaitersHolder()
        private val whileLoopBroke: AtomicBoolean = atomic(false)

        init {
            scope.launch {
                while (currentCoroutineContext().isActive) {
                    val replies = try {
                        readReply()
                    } catch (e: Exception) {
                        whileLoopBroke.value = true
                        debugger.safeInvoke(DebugItem.Error(e))
                        break
                    }

                    if (replies.isEmpty()) {
                        whileLoopBroke.value = true
                        debugger.safeInvoke(DebugItem.Message("Replies off the control port were empty"))
                        break
                    }

                    if (replies.first().status.startsWith('6')) {
                        try {
                            notifyListeners(replies)
                        } catch (e: Exception) {
                            // shouldn't be necessary, but who knows...
                            debugger.safeInvoke(DebugItem.Error(e))
                        }
                    } else {
                        waiters.withLock {
                            val waiter = removeFirstOrNull() ?: return@withLock

                            val lines = replies.map { reply ->
                                when (reply) {
                                    is ReplyLine.MultiLine -> {
                                        ReplyLine.SingleLine(
                                            reply.status,
                                            reply.messages.joinToString(separator = "\n", prefix = reply.event)
                                        )
                                    }
                                    is ReplyLine.SingleLine -> {
                                        reply
                                    }
                                }
                            }

                            waiter.setResponse(lines)
                        }
                    }
                }
            }.invokeOnCompletion {
                scope.cancel()
                debugger.safeInvoke(DebugItem.Message("Tor has stopped. Shutting down TorController threads."))
                dispatchers.close()
                onDisconnect.safeInvoke(this@RealTorController)
                onDisconnect.value = null
            }
        }

        override val isConnected: Boolean
            get() = !whileLoopBroke.value && scope.isActive

        @Throws(
            CancellationException::class,
            ControllerShutdownException::class,
            TorControllerException::class,
        )
        override suspend fun <T : Any?> processCommand(
            command: String,
            transform: List<ReplyLine.SingleLine>.() -> T
        ): T {
            val replies = processCommand(command)

            return withContext(dispatchers) {
                transform.invoke(replies)
            }
        }

        @Throws(
            CancellationException::class,
            ControllerShutdownException::class,
            TorControllerException::class,
        )
        override suspend fun processCommand(command: String): List<ReplyLine.SingleLine> {
            if (!isConnected) {
                throw ControllerShutdownException("Tor has stopped and a new connection is required")
            }

            return withContext(dispatchers) {
                val waiter = Waiter.newInstance { isConnected }
                waiters.withLock {

                    if (command.startsWith('+')) {
                        for (line in command.lines()) {
                            if (line.isBlank()) {
                                continue
                            }

                            val trimmed = line.trim()

                            debugger.safeInvoke(DebugItem.Message(">> $trimmed"))

                            writer.write(trimmed)
                            writer.write(CLRF)
                            writer.flush()
                        }
                    } else {
                        debugger.safeInvoke(DebugItem.Message(">> $command"))
                        writer.write(command)
                        writer.flush()
                    }

                    add(waiter)
                }

                val replies: List<ReplyLine.SingleLine> = waiter.getResponse()

                for (reply in replies) {
                    if (!reply.isCommandResponseStatusSuccess) {
                        throw TorControllerException("\ncommand=$command\nreply=$reply\n")
                    }
                }

                replies
            }
        }

        @Throws(TorControllerException::class)
        private fun notifyListeners(replies: List<ReplyLine>) {
            if (listeners.isEmpty) return

            var isConfChanged = false

            for (reply in replies) {
                when (reply) {
                    is ReplyLine.SingleLine -> {
                        if (reply.isEventStatusSuccess) {
                            // final line in multi-line reply. disregard.
                            continue
                        }

                        if (isConfChanged) {
                            listeners.notify(TorEvent.ConfChanged, reply.message)
                            continue
                        }

                        // separate event from remainder message
                        val index = reply.message.indexOf(' ')

                        val (eventString, message) = try {
                            Pair(
                                reply.message.substring(0, index).uppercase(),
                                reply.message.substring(index + 1)
                            )
                        } catch (e: IndexOutOfBoundsException) {

                            if (reply.message == TorEvent.ConfChanged.value) {
                                isConfChanged = true
                                continue
                            }

                            throw TorControllerException(
                                "Failed to parse reply." +
                                "\nReply being parsed: $reply" +
                                "\nAll replies: $replies",
                                e
                            )
                        }

                        for (event in TorEvent.SINGLE_LINE_EVENTS) {
                            if (event.compareTo(eventString)) {
                                listeners.notify(event, message)
                                break
                            }
                        }
                    }
                    is ReplyLine.MultiLine -> {
                        for (event in TorEvent.MULTI_LINE_EVENTS) {
                            if (event.compareTo(reply.event)) {
                                listeners.notify(event, reply.messages)
                                break
                            }
                        }
                    }
                }
            }
        }

        // TODO: Modify to flow where lines are emitted
        @Throws(TorControllerException::class, CancellationException::class)
        private suspend fun readReply(): List<ReplyLine> {
            val replies = ArrayList<ReplyLine>()
            val multiLine: MutableList<String> = mutableListOf()
            var char: Char? = null
            while (char != ' ') {
                currentCoroutineContext().ensureActive()
                var line = reader.readLine()

                if (line == null) {
                    if (replies.isEmpty()) {
                        // Tor has stopped, we can exit cleanly
                        return replies
                    }

                    throw TorControllerException("Connection to Tor broke down while receiving reply")
                }

                debugger.safeInvoke(DebugItem.Message("<< $line"))
                if (line.length < 4) {
                    throw TorControllerException("Line($line) too short")
                }

                val status = line.substring(0, 3)
                char = line[3]

                // If single-line reply, first word will be the event string
                // If multi-line reply (char == '+'), it will be a single word (our event string)
                val msg = line.substring(4)

                if (char == '+') {
                    // process multi-line reply
                    while (true) {
                        line = reader.readLine()
                        debugger.safeInvoke(DebugItem.Message("<< $line"))
                        if (line == "$MULTI_LINE_END") {
                            break
                        } else if (line?.startsWith(MULTI_LINE_END) == true) {
                            line = line.substring(1)
                        }
                        line?.let { multiLine.add(it) }
                    }
                }

                if (multiLine.isEmpty()) {
                    replies.add(ReplyLine.SingleLine(status, msg))
                } else {
                    replies.add(ReplyLine.MultiLine(status, msg, multiLine.toList()))
                    multiLine.clear()
                }
            }

            return replies
        }
    }

    override val isConnected: Boolean get() = controlPortInteractor.isConnected

    override fun disconnect() {
        try {
            socket.close()
        } catch (e: Exception) {
            debugger.safeInvoke(DebugItem.Error(e))
        }
    }

    override fun onDisconnect(action: ((TorController) -> Unit)?) {
        onDisconnect.value = action
    }

    override fun setDebugger(debugger: ((DebugItem) -> Unit)?) {
        this.debugger.value = debugger
    }

    override val hasDebugger: Boolean get() = debugger.value != null

    override fun addListener(listener: TorEvent.SealedListener): Boolean {
        return listeners.addListener(listener)
    }

    override fun removeListener(listener: TorEvent.SealedListener): Boolean {
        return listeners.removeListener(listener)
    }

    override suspend fun authenticate(bytes: ByteArray): Result<Any?> {
        return processorDelegate.authenticate(bytes)
    }

//    override suspend fun challengeAuth(clientNonce: String): Result<Map<String, String>> {
//        return processorDelegate.challengeAuth(clientNonce)
//    }

//    override suspend fun circuitClose(): Result<Any?> {
//        return processorDelegate.circuitClose()
//    }

//    override suspend fun circuitExtend(): Result<String> {
//        return processorDelegate.circuitExtend()
//    }

//    override suspend fun circuitSetPurpose(): Result<Any?> {
//        return processorDelegate.circuitSetPurpose()
//    }

    override suspend fun configGet(setting: TorConfig.Setting<*>): Result<List<ConfigEntry>> {
        return processorDelegate.configGet(setting)
    }

    override suspend fun configGet(settings: Set<TorConfig.Setting<*>>): Result<List<ConfigEntry>> {
        return processorDelegate.configGet(settings)
    }

    override suspend fun configLoad(config: TorConfig): Result<Any?> {
        return processorDelegate.configLoad(config)
    }

    override suspend fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean
    ): Result<Any?> {
        return processorDelegate.configReset(setting, setDefault)
    }

    override suspend fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean
    ): Result<Any?> {
        return processorDelegate.configReset(settings, setDefault)
    }

    override suspend fun configSave(force: Boolean): Result<Any?> {
        return processorDelegate.configSave(force)
    }

    override suspend fun configSet(setting: TorConfig.Setting<*>): Result<Any?> {
        return processorDelegate.configSet(setting)
    }

    override suspend fun configSet(settings: Set<TorConfig.Setting<*>>): Result<Any?> {
        return processorDelegate.configSet(settings)
    }

//    override suspend fun descriptorPost(): Result<String> {
//        return processorDelegate.descriptorPost()
//    }

    override suspend fun dropGuards(): Result<Any?> {
        return processorDelegate.dropGuards()
    }

    override suspend fun hsFetch(address: OnionAddress): Result<Any?> {
        return processorDelegate.hsFetch(address)
    }

    override suspend fun hsFetch(address: OnionAddress, server: Server.Fingerprint): Result<Any?> {
        return processorDelegate.hsFetch(address, server)
    }

    override suspend fun hsFetch(address: OnionAddress, servers: Set<Server.Fingerprint>): Result<Any?> {
        return processorDelegate.hsFetch(address, servers)
    }

//    override suspend fun hsPost(): Result<Any?> {
//        return processorDelegate.hsPost()
//    }

    override suspend fun infoGet(keyword: KeyWord): Result<String> {
        return processorDelegate.infoGet(keyword)
    }

    override suspend fun infoGet(keywords: Set<KeyWord>): Result<Map<String, String>> {
        return processorDelegate.infoGet(keywords)
    }

//    override suspend fun infoProtocol(): Result<Any?> {
//        return processorDelegate.infoProtocol()
//    }

//    override suspend fun mapAddress(): Result<Map<String, String>> {
//        return processorDelegate.mapAddress()
//    }

    override suspend fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return processorDelegate.onionAdd(privateKey, hsPorts, flags, maxStreams)
    }

    override suspend fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return processorDelegate.onionAddNew(type, hsPorts, flags, maxStreams)
    }

    override suspend fun onionDel(address: OnionAddress): Result<Any?> {
        return processorDelegate.onionDel(address)
    }

    override suspend fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?
    ): Result<Any?> {
        return processorDelegate.onionClientAuthAdd(address, key, clientName, flags)
    }

    override suspend fun onionClientAuthRemove(address: OnionAddressV3): Result<Any?> {
        return processorDelegate.onionClientAuthRemove(address)
    }

    override suspend fun onionClientAuthView(): Result<List<ClientAuthEntry>> {
        return processorDelegate.onionClientAuthView()
    }

    override suspend fun onionClientAuthView(address: OnionAddressV3): Result<ClientAuthEntry> {
        return processorDelegate.onionClientAuthView(address)
    }

    override suspend fun ownershipDrop(): Result<Any?> {
        return processorDelegate.ownershipDrop()
    }

    override suspend fun ownershipTake(): Result<Any?> {
        return processorDelegate.ownershipTake()
    }

//    override suspend fun resolve(): Result<Any?> {
//        return processorDelegate.resolve()
//    }

    override suspend fun setEvents(events: Set<TorEvent>, extended: Boolean): Result<Any?> {
        return processorDelegate.setEvents(events, extended)
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return processorDelegate.signal(signal)
    }

//    override suspend fun streamAttach(): Result<Any?> {
//        return processorDelegate.streamAttach()
//    }

//    override suspend fun streamClose(): Result<Any?> {
//        return processorDelegate.streamClose()
//    }

//    override suspend fun streamRedirect(): Result<Any?> {
//        return processorDelegate.streamRedirect()
//    }

//    override suspend fun useFeature(): Result<Any?> {
//        return processorDelegate.useFeature()
//    }

}

@Suppress("nothing_to_inline")
private inline fun <T: Any?> AtomicRef<((T) -> Unit)?>.safeInvoke(item: T) {
    value?.let { try { it.invoke(item) } catch (_: Throwable) {} }
}
