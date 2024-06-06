@file:Suppress("KotlinRedundantDiagnosticSuppress", "LocalVariableName", "UnusedReceiverParameter", "UNUSED_PARAMETER")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Keyword.Attribute
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d

@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun TorCmd<*>.encodeToByteArray(LOG: Debugger?): ByteArray = when (this) {
    is TorCmd.Authenticate -> encode(LOG)
    is TorCmd.Config.Get -> encode(LOG)
    is TorCmd.Config.Load -> encode(LOG)
    is TorCmd.Config.Reset -> encode(LOG)
    is TorCmd.Config.Save -> encode(LOG)
    is TorCmd.Config.Set -> encode(LOG)
    is TorCmd.DropGuards -> encode(LOG)
    is TorCmd.Hs.Fetch -> encode(LOG)
    is TorCmd.Info.Get -> encode(LOG)
    is TorCmd.MapAddress -> encode(LOG)
    is TorCmd.Onion.Add -> encode(LOG)
    is TorCmd.Onion.Delete -> encode(LOG)
    is TorCmd.OnionClientAuth.Add -> encode(LOG)
    is TorCmd.OnionClientAuth.Remove -> encode(LOG)
    is TorCmd.OnionClientAuth.View -> encode(LOG)
    is TorCmd.Ownership.Drop -> encode(LOG)
    is TorCmd.Ownership.Take -> encode(LOG)
    is TorCmd.Resolve -> encode(LOG)
    is TorCmd.SetEvents -> encode(LOG)
    is TorCmd.Signal.Dump -> encodeSignal(LOG)
    is TorCmd.Signal.Debug -> encodeSignal(LOG)
    is TorCmd.Signal.NewNym -> encodeSignal(LOG)
    is TorCmd.Signal.ClearDnsCache -> encodeSignal(LOG)
    is TorCmd.Signal.Heartbeat -> encodeSignal(LOG)
    is TorCmd.Signal.Active -> encodeSignal(LOG)
    is TorCmd.Signal.Dormant -> encodeSignal(LOG)
    is TorCmd.Signal.Reload -> encodeSignal(LOG)
    is TorCmd.Signal.Shutdown -> encodeSignal(LOG)
    is TorCmd.Signal.Halt -> encodeSignal(LOG)
}

internal fun TorCmd<*>.signalNameOrNull(): String? = when (this) {
    is TorCmd.Signal.Dump -> "DUMP"
    is TorCmd.Signal.Debug -> "DEBUG"
    is TorCmd.Signal.NewNym -> "NEWNYM"
    is TorCmd.Signal.ClearDnsCache -> "CLEARDNSCACHE"
    is TorCmd.Signal.Heartbeat -> "HEARTBEAT"
    is TorCmd.Signal.Active -> "ACTIVE"
    is TorCmd.Signal.Dormant -> "DORMANT"
    is TorCmd.Signal.Reload -> "RELOAD"
    is TorCmd.Signal.Shutdown -> "SHUTDOWN"
    is TorCmd.Signal.Halt -> "HALT"
    else -> {
        if (keyword == TorCmd.Signal.Dump.keyword) {
            this::class.simpleName?.uppercase()
        } else {
            null
        }
    }
}

private fun TorCmd.Authenticate.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        val redacted = if (hex.isNotEmpty()) {
            SP().append(hex)
            " [REDACTED]"
        } else {
            ""
        }

        LOG.d { ">> $keyword$redacted" }
        CRLF()
    }.encodeToByteArray(fill = true)
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Get.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        for (word in keywords) {
            SP().append(word)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Load.encode(LOG: Debugger?): ByteArray {
    return StringBuilder().apply {
        append('+').append(keyword)

        var hasLine = false
        for (line in configText.lines()) {
            val isCommentOrBlank = run {
                val i = line.indexOfFirst { !it.isWhitespace() }
                if (i == -1) true else line[i] == '#'
            }
            if (isCommentOrBlank) continue

            if (!hasLine) {
                hasLine = true
                LOG.d { ">> ${toString()}" }
                CRLF()
            } else {
                appendLine()
            }

            append(line)
            LOG.d { ">> $line" }
        }

        require(hasLine) { "configText must contain at least 1 setting" }

        CRLF()
        LOG.d { ">> ." }
        append('.')
        CRLF()
    }.encodeToByteArray(fill = true)
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Reset.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        for (word in keywords) {
            SP().append(word)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.Config.Save.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        if (force) {
            SP().append("FORCE")
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Set.encode(LOG: Debugger?): ByteArray {
    require(settings.isNotEmpty()) { "A minimum of 1 setting is required" }

    return StringBuilder(keyword).apply {
        for (setting in settings) {
            for (line in setting.items) {
                SP().append(line.keyword).append('=').append('"')

                run {
                    var argument = line.argument

                    with(line.keyword.attributes) {
                        when {
                            contains(Attribute.UnixSocket) -> {
                                if (argument.startsWith("unix:")) {
                                    argument = argument.replace("\"", "\\\"")
                                }
                            }
                            contains(Attribute.File) || contains(Attribute.Directory) -> {
                                if (SysDirSep == '\\') {
                                    argument = argument.replace("\\", "\\\\")
                                }
                            }
                        }
                    }

                    append(argument)
                }

                for (optional in line.optionals) {
                    SP().append(optional)
                }

                append('"')
            }
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray(fill = true)
}

private fun TorCmd.DropGuards.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Hs.Fetch.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)

        servers.forEach { server ->
            require(!server.isEmptyOrHasWhitespace()) {
                "server[$server] cannot be empty or contain whitespace"
            }

            SP().append("SERVER=").append(server)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Info.Get.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        keywords.forEach { word ->
            require(!word.isEmptyOrHasWhitespace()) {
                "keyword[$word] cannot be empty or contain whitespace"
            }

            SP().append(word)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.MapAddress.encode(LOG: Debugger?): ByteArray {
    require(mappings.isNotEmpty()) { "A minimum of 1 mapping is required" }

    return StringBuilder(keyword).apply {
        mappings.forEach { mapping ->
            require(!mapping.from.isEmptyOrHasWhitespace()) {
                "AddressMapping.from[${mapping.from}] cannot be empty or contain whitespace"
            }
            require(!mapping.to.isEmptyOrHasWhitespace()) {
                "AddressMapping.to[${mapping.to}] cannot be empty or contain whitespace"
            }

            SP().append(mapping.from).append('=').append(mapping.to)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class, IllegalStateException::class)
private fun TorCmd.Onion.Add.encode(LOG: Debugger?): ByteArray {
    require(ports.isNotEmpty()) { "At minimum of 1 port is required" }

    return StringBuilder(keyword).apply {
        SP()

        val redact = when (this@encode) {
            is TorCmd.Onion.Add.Existing -> {
                val b64Key = key.base64()
                append(key.algorithm()).append(':').append(b64Key)
                b64Key
            }
            is TorCmd.Onion.Add.New -> {
                append("NEW").append(':').append(type.algorithm())
                null
            }
        }

        if (flags.isNotEmpty()) {
            SP().append("Flags=")
            flags.joinTo(this, ",")
        }

        maxStreams?.let { maxStreams ->
            SP().append("MaxStreams=").append(maxStreams.argument)
        }

        for (port in ports) {
            SP().append("Port=")

            val i = port.argument.indexOf(' ')
            val virtual = port.argument.substring(0, i)
            var target = port.argument.substring(i + 1)

            if (target.startsWith("unix:")) {
                target = target.replace("\"", "\\\"")
            }

            append(virtual).append(',').append(target)
        }

        LOG.d {
            var log = toString()
            if (redact != null) {
                log = log.replace(redact, "[REDACTED]")
            }

            ">> $log"
        }

        CRLF()
    }.encodeToByteArray(fill = true)
}

private fun TorCmd.Onion.Delete.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.OnionClientAuth.Add.encode(LOG: Debugger?): ByteArray {
    TODO("Issue #420")
}

private fun TorCmd.OnionClientAuth.Remove.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.OnionClientAuth.View.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        if (address != null) {
            SP().append(address)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.Ownership.Drop.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.Ownership.Take.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Resolve.encode(LOG: Debugger?): ByteArray {
    require(hostname.isNotEmpty()) { "hostname cannot be empty" }
    require(!hostname.isEmptyOrHasWhitespace()) { "hostname cannot contain whitespace" }

    return StringBuilder(keyword).apply {
        if (reverse) {
            SP().append("mode=reverse")
        }
        SP().append(hostname)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

private fun TorCmd.SetEvents.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        events.forEach { event ->
            SP().append(event.name)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd<*>.encodeSignal(LOG: Debugger?): ByteArray {
    val name = signalNameOrNull()
        ?: throw IllegalArgumentException("${this::class} is not a SIGNAL command")

    return StringBuilder("SIGNAL").apply {
        SP().append(name)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE", "FunctionName")
private inline fun StringBuilder.SP(): StringBuilder = append(' ')

@Suppress("NOTHING_TO_INLINE", "FunctionName")
private inline fun StringBuilder.CRLF(): StringBuilder = append('\r').append('\n')

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.encodeToByteArray(fill: Boolean = false): ByteArray {
    val count = count()
    val s = toString()

    clear()
    if (fill) {
        repeat(count) { append(' ') }
    }

    return s.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun String.isEmptyOrHasWhitespace(): Boolean = indexOfFirst { it.isWhitespace() } != -1
