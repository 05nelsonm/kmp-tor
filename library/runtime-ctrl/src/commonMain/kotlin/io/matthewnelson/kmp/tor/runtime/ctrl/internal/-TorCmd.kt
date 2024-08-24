@file:Suppress("KotlinRedundantDiagnosticSuppress", "LocalVariableName", "UnusedReceiverParameter", "UNUSED_PARAMETER")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.Attribute
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
    require(options.isNotEmpty()) { "A minimum of 1 option is required" }

    return StringBuilder(keyword).apply {
        for (option in options) {
            SP().append(option)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Load.encode(LOG: Debugger?): ByteArray {
    require(config.settings.isNotEmpty()) { "A minimum of 1 setting is required" }

    return StringBuilder().apply {
        append('+').append(keyword)

        var crlf = false
        for (setting in config) {
            for (line in setting.items) {
                if (!crlf) {
                    crlf = true
                    LOG.d { ">> ${toString()}" }
                    CRLF()
                } else {
                    appendLine()
                }

                append(line)
                LOG.d { ">> $line" }
            }
        }

        CRLF()
        LOG.d { ">> ." }
        append('.')
        CRLF()
    }.encodeToByteArray(fill = true)
}

@Throws(IllegalArgumentException::class)
private fun TorCmd.Config.Reset.encode(LOG: Debugger?): ByteArray {
    require(options.isNotEmpty()) { "A minimum of 1 option is required" }

    return StringBuilder(keyword).apply {
        for (option in options) {
            SP().append(option)
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
    require(config.settings.isNotEmpty()) { "A minimum of 1 setting is required" }

    return StringBuilder(keyword).apply {
        for (setting in config) {
            for (line in setting.items) {
                SP().append(line.option).append('=').append('"')

                run {
                    var argument = line.argument

                    // Escapes
                    when {
                        line.isUnixSocket -> {
                            argument = argument.replace("\"", "\\\"")
                        }
                        (line.isDirectory || line.isFile) && SysDirSep == '\\' -> {
                            argument = argument.replace("\\", "\\\\")
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
                "from[${mapping.from}] cannot be empty or contain whitespace"
            }
            require(!mapping.to.isEmptyOrHasWhitespace()) {
                "to[${mapping.to}] cannot be empty or contain whitespace"
            }

            SP().append(mapping.from).append('=').append(mapping.to)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Throws(IllegalArgumentException::class, IllegalStateException::class)
private fun TorCmd.Onion.Add.encode(LOG: Debugger?): ByteArray {
    require(ports.isNotEmpty()) { "A minimum of 1 port is required" }
    val privateKey = key?.base64()

    return StringBuilder(keyword).apply {
        SP()

        if (privateKey != null) {
            append(keyType.algorithm()).append(':').append(privateKey)
        } else {
            append("NEW").append(':').append(keyType.algorithm())
        }

        if (flags.isNotEmpty()) {
            SP().append("Flags=")
            flags.joinTo(this, ",")
        }

        maxStreams?.let { maxStreams ->
            SP().append("MaxStreams=").append(maxStreams)
        }

        for (port in ports) {
            SP().append("Port=")

            val i = port.argument.indexOf(' ')
            val virtual = port.argument.substring(0, i)
            var target = port.argument.substring(i + 1)

            append(virtual).append(',')

            val prefixUnix = "unix:"
            if (target.startsWith(prefixUnix)) {
                append(prefixUnix)

                // Need to remove path quotes
                //
                // NOTE: If path has a space in it, controller fails
                //  as it cannot parse. There is no ability to quote
                //  the unix:"/pa th/to/hs.sock" like usual...
                //
                //  https://github.com/05nelsonm/kmp-tor/issues/207#issuecomment-1166722564
                //  https://gitlab.torproject.org/tpo/core/tor/-/issues/40633
                target = target.substring(prefixUnix.length + 1, target.length - 1)
            }

            append(target)
        }

        for (auth in clientAuth) {
            SP().append("ClientAuthV3=").append(auth.base32())
        }

        LOG.d {
            var log = toString()
            if (privateKey != null) {
                log = log.replace(privateKey, "[REDACTED]")
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

@Throws(IllegalArgumentException::class, IllegalStateException::class)
private fun TorCmd.OnionClientAuth.Add.encode(LOG: Debugger?): ByteArray {
    val nickname = clientName
    if (!nickname.isNullOrEmpty()) {
        require(!nickname.isEmptyOrHasWhitespace()) {
            "clientName[$clientName] cannot contain whitespace"
        }
    }

    val privateKey = key.base64()

    return StringBuilder(keyword).apply {
        SP().append(address)
        SP().append(key.algorithm()).append(':').append(privateKey)
        if (nickname != null) {
            SP().append("ClientName=").append(nickname)
        }
        if (flags.isNotEmpty()) {
            SP().append("Flags=")
            flags.joinTo(this, ",")
        }

        LOG.d {
            val log = toString().replace(privateKey, "[REDACTED]")
            ">> $log"
        }

        CRLF()
    }.encodeToByteArray(fill = true)
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
