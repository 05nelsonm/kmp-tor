@file:Suppress("KotlinRedundantDiagnosticSuppress", "LocalVariableName", "UnusedReceiverParameter", "UNUSED_PARAMETER")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Keyword.Attribute
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d

@Throws(IllegalArgumentException::class)
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
    is TorCmd.Signal.Reload -> encodeSignal(LOG)
    is TorCmd.Signal.Dump -> encodeSignal(LOG)
    is TorCmd.Signal.Debug -> encodeSignal(LOG)
    is TorCmd.Signal.NewNym -> encodeSignal(LOG)
    is TorCmd.Signal.ClearDnsCache -> encodeSignal(LOG)
    is TorCmd.Signal.Heartbeat -> encodeSignal(LOG)
    is TorCmd.Signal.Active -> encodeSignal(LOG)
    is TorCmd.Signal.Dormant -> encodeSignal(LOG)
    is TorCmd.Signal.Shutdown -> encodeSignal(LOG)
    is TorCmd.Signal.Halt -> encodeSignal(LOG)
}

internal fun TorCmd<*>.signalNameOrNull(): String? = when (this) {
    is TorCmd.Signal.Reload -> "RELOAD"
    is TorCmd.Signal.Dump -> "DUMP"
    is TorCmd.Signal.Debug -> "DEBUG"
    is TorCmd.Signal.NewNym -> "NEWNYM"
    is TorCmd.Signal.ClearDnsCache -> "CLEARDNSCACHE"
    is TorCmd.Signal.Heartbeat -> "HEARTBEAT"
    is TorCmd.Signal.Active -> "ACTIVE"
    is TorCmd.Signal.Dormant -> "DORMANT"
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

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Authenticate.encode(LOG: Debugger?): ByteArray {
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

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Config.Get.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        for (word in keywords) {
            SP().append(word)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Config.Load.encode(LOG: Debugger?): ByteArray {
    require(configText.isNotBlank()) { "configText cannot be blank" }

    return StringBuilder().apply {
        append('+').append(keyword)
        LOG.d { ">> ${toString()}" }
        CRLF()
        configText.lines().joinTo(
            buffer = this,
            separator = "\n",
            transform = { line ->
                LOG.d { ">> $line" }
                line
            }
        )
        CRLF()
        LOG.d { ">> ." }
        append('.')
        CRLF()
    }.encodeToByteArray(fill = true)
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Config.Reset.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        for (word in keywords) {
            SP().append(word)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Config.Save.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        if (force) {
            SP().append("FORCE")
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Config.Set.encode(LOG: Debugger?): ByteArray {
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

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.DropGuards.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Hs.Fetch.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)

        for (server in servers) {
            require(server.isNotEmpty()) { "servers cannot contain empty values" }
            require(!server.hasWhitespace()) { "server values cannot contain whitespace" }

            SP().append("SERVER=").append(server)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Info.Get.encode(LOG: Debugger?): ByteArray {
    require(keywords.isNotEmpty()) { "A minimum of 1 keyword is required" }

    return StringBuilder(keyword).apply {
        for (word in keywords) {
            require(word.isNotEmpty()) { "keywords cannot contain empty values" }
            require(!word.hasWhitespace()) { "keyword values cannot contain whitespace" }

            SP().append(word)
        }

        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.MapAddress.encode(LOG: Debugger?): ByteArray {
    require(mappings.isNotEmpty()) { "A minimum of 1 mapping is required" }

    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Onion.Add.encode(LOG: Debugger?): ByteArray {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Onion.Delete.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.Add.encode(LOG: Debugger?): ByteArray {
    TODO()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.Remove.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        SP().append(address)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.OnionClientAuth.View.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        if (address != null) {
            SP().append(address)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Ownership.Drop.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.Ownership.Take.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd.Resolve.encode(LOG: Debugger?): ByteArray {
    require(hostname.isNotEmpty()) { "hostname cannot be empty" }
    require(!hostname.hasWhitespace()) { "hostname cannot contain whitespace" }

    return StringBuilder(keyword).apply {
        if (reverse) {
            SP().append("mode=reverse")
        }
        SP().append(hostname)
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmd.SetEvents.encode(LOG: Debugger?): ByteArray {
    return StringBuilder(keyword).apply {
        for (event in events) {
            SP().append(event.name)
        }
        LOG.d { ">> ${toString()}" }
        CRLF()
    }.encodeToByteArray()
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
private inline fun TorCmd<*>.encodeSignal(LOG: Debugger?): ByteArray {
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
private inline fun String.hasWhitespace(): Boolean {
    for (c in this) {
        if (!c.isWhitespace()) continue
        return true
    }

    return false
}
