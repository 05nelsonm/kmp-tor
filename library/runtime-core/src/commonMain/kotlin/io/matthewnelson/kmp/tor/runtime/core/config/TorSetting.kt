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

package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import io.matthewnelson.kmp.tor.runtime.core.internal.isSingleLine
import kotlin.jvm.*

/**
 * Holder for a "setting".
 *
 * Most [TorOption] are a single [LineItem], but there are some which
 * require "grouping", such as Hidden Services. For example, declaring a
 * Hidden Service in a configuration requires [TorOption.HiddenServiceDir]
 * be declared first, and a minimum of one [TorOption.HiddenServicePort]
 * declaration following it. In this case, [items] would contain all
 * Hidden Service [LineItem] for that Hidden Service "instance".
 *
 * Comparison of settings is done such that only the first [LineItem] (or
 * "root" item) within [items] is considered.
 *
 * @see [Iterable.filterByAttribute]
 * @see [Iterable.filterByOption]
 * */
public class TorSetting private constructor(

    /**
     * A single, or "group" of [LineItem]. This will **always**
     * contain, at a minimum, 1 [LineItem]. If multiple [LineItem]
     * are present (i.e. a group setting), the first [LineItem] is
     * treated as the "root" [LineItem] for the [TorSetting].
     * */
    @JvmField
    public val items: Set<LineItem>,

    /**
     * Any extra information about this setting that will be
     * excluded by [toString], but may be required elsewhere, such
     * as the `kmp-tor:runtime` dependency.
     *
     * For example, including the unhashed password for the
     * [TorOption.__HashedControlSessionPassword] setting, in order
     * to use when establishing a control connection.
     *
     * @see [BuilderScopePort.EXTRA_REASSIGNABLE]
     * */
    @JvmField
    public val extras: Map<String, Any>,
) {

    /**
     * Holder of a single configuration line item.
     *
     * e.g.
     *
     *     |    option    | argument |            optionals             |
     *     __SocksPort      9050       OnionTrafficOnly IsolateDestPort
     *     DisableNetwork   1
     *     RunAsDaemon      0
     * */
    public class LineItem private constructor(

        /**
         * The [TorOption] for this single line expression.
         * */
        @JvmField
        public val option: TorOption,

        /**
         * The argument for this [TorOption]. Will be non-blank and single line.
         * */
        @JvmField
        public val argument: String,

        /**
         * Optional things for this [TorOption] which are excluded from [equals]
         * and [hashCode] consideration. They are appended to the option and
         * argument string using a single space deliminator.
         *
         * All contents will be non-blank and single line. Can be empty.
         * */
        @JvmField
        public val optionals: Set<String>,
    ) {

        /**
         * If the [LineItem] is a [TorOption] that is "Non-Persistent",
         * indicative of the [TorOption.name] starting with two `_`
         * characters.
         * */
        @JvmField
        public val isNonPersistent: Boolean =
            option.name[0] == '_'

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.UNIX_SOCKET], and is configured as such.
         *
         * If the attribute [TorOption.Attribute.PORT] is also present
         * for the [TorOption] (e.g. [TorOption.ControlPort]), then the
         * [argument] will be checked to see if it starts with `unix:`.
         * */
        @JvmField
        public val isUnixSocket: Boolean = run {
            if (!option.attributes.contains(TorOption.Attribute.UNIX_SOCKET)) {
                // Does not contain UNIX_SOCKET
                return@run false
            }

            if (!option.attributes.contains(TorOption.Attribute.PORT)) {
                // Can only be configured as UNIX_SOCKET (e.g. ControlSocket)
                return@run true
            }

            if (option is TorOption.HiddenServicePort) {
                // Check target, not virtual port
                argument.substringAfter(' ')
            } else {
                argument
            }.startsWith("unix:")
        }

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured with an
         * [argument] of `0`, `auto`, or a [Port.value].
         * */
        @JvmField
        public val isPort: Boolean =
            option.attributes.contains(TorOption.Attribute.PORT)
            && !isUnixSocket

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured as `auto`.
         * */
        @JvmField
        public val isPortAuto: Boolean =
            isPort && argument == TorOption.AUTO

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured as `0`.
         * */
        @JvmField
        public val isPortDisabled: Boolean =
            isPort && argument == Port.ZERO.toString()

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured with a
         * distinct port, such as 9050 (i.e. not `0` or `auto`).
         * */
        @JvmField
        public val isPortDistinct: Boolean =
            isPort && !isPortAuto && !isPortDisabled

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.FILE].
         * */
        @JvmField
        public val isFile: Boolean =
            option.attributes.contains(TorOption.Attribute.FILE)

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.DIRECTORY].
         * */
        @JvmField
        public val isDirectory: Boolean =
            option.attributes.contains(TorOption.Attribute.DIRECTORY)

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.HIDDEN_SERVICE].
         * */
        @JvmField
        public val isHiddenService: Boolean =
            option.attributes.contains(TorOption.Attribute.HIDDEN_SERVICE)

        internal companion object {

            @JvmSynthetic
            @Throws(IllegalArgumentException::class)
            internal fun TorOption.toLineItem(
                argument: String,
                optionals: Set<String> = emptySet(),
            ): LineItem {
                require(argument.isNotBlank()) { "argument cannot be blank" }
                require(argument.isSingleLine()) { "argument cannot be multiple lines" }

                @Suppress("LocalVariableName")
                val _optionals = optionals.toImmutableSet()

                _optionals.forEach { optional ->
                    require(optional.isNotBlank()) { "optionals cannot be blank" }
                    require(optional.isSingleLine()) { "optionals cannot be multiple lines" }
                }

                return LineItem(this, argument, _optionals)
            }
        }

        /** @suppress */
        public override fun equals(other: Any?): Boolean {
            return  other is LineItem
                    && other.option == option
                    && other.argument == argument
        }

        /** @suppress */
        public override fun hashCode(): Int {
            var result = 13
            result = result * 42 + option.hashCode()
            result = result * 42 + argument.hashCode()
            return result
        }

        /** @suppress */
        public override fun toString(): String = buildString {
            append(option)
            append(' ')
            append(argument)
            if (optionals.isNotEmpty()) {
                append(' ')
                optionals.joinTo(this, separator = " ")
            }
        }
    }

    public companion object {

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [Iterable] which contain a [LineItem], and is configured
         * for, attribute [A].
         *
         * For example, if [TorOption.Attribute.UNIX_SOCKET] is parameter
         * [A] and 2 declarations of [TorOption.ControlPort] are present
         * (one configured as a Unix Socket, and the other as a TCP port),
         * then only the one configured as a Unix Socket will be present
         * in the returned list.
         *
         * @see [Iterable.filterByAttribute]
         * */
        @JvmStatic
        public inline fun <reified A: TorOption.Attribute> TorConfig.filterByAttribute(): List<TorSetting> {
            return settings.filterByAttribute<A>()
        }

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [Iterable] which contain a [LineItem], and is configured
         * for, attribute [A].
         *
         * For example, if [TorOption.Attribute.UNIX_SOCKET] is parameter
         * [A] and 2 declarations of [TorOption.ControlPort] are present
         * (one configured as a Unix Socket, and the other as a TCP port),
         * then only the one configured as a Unix Socket will be present
         * in the returned list.
         * */
        @JvmStatic
        public inline fun <reified A: TorOption.Attribute> Iterable<TorSetting>.filterByAttribute(): List<TorSetting> {
            return filter { setting ->

                // For all items in TorSetting
                setting.items.forEach items@ { item ->

                    // For all attributes of the item
                    item.option.attributes.forEach attrs@ { attr ->
                        if (attr !is A) return@attrs

                        // Found specified attribute T

                        // Only need to check PORT and UNIX_SOCKET
                        // configurations because some options can be
                        // either or (e.g. ControlPort and SocksPort).

                        if (attr is TorOption.Attribute.PORT) {
                            // Looking for PORT, but item configured
                            // as Unix Socket. Next item.
                            if (item.isUnixSocket) return@items
                        }

                        if (attr is TorOption.Attribute.UNIX_SOCKET) {
                            // Looking for UNIX_SOCKET, but item
                            // configured as a TCP port (or is `auto`
                            // or `0`). Next item.
                            if (item.isPort) return@items
                        }

                        // Item contains attribute T, add the TorSetting
                        // to the list.
                        return@filter true
                    }
                }

                false
            }
        }

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [TorConfig] which contain a [LineItem] for option [O].
         *
         * @see [Iterable.filterByOption]
         * */
        @JvmStatic
        public inline fun <reified O: TorOption> TorConfig.filterByOption(): List<TorSetting> {
            return settings.filterByOption<O>()
        }

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [Iterable] which contain a [LineItem] for option [O].
         * */
        @JvmStatic
        public inline fun <reified O: TorOption> Iterable<TorSetting>.filterByOption(): List<TorSetting> {
            return filter { setting ->
                setting.items.forEach items@ { item ->
                    if (item.option is O) return@filter true
                }

                false
            }
        }

        @JvmSynthetic
        internal fun LineItem.toSetting(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting = immutableSetOf(this).toSetting(extras)

        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal fun Set<LineItem>.toSetting(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting {
            val items = toImmutableSet()

            require(items.isNotEmpty()) { "items cannot be empty" }

            return TorSetting(items, extras.toImmutableMap())
        }
    }

    /**
     * Base builder scope for higher level implementations.
     * */
    @KmpTorDsl
    public abstract class BuilderScope
    @Throws(IllegalStateException::class)
    internal constructor(

        /**
         * The [TorOption] being configured for this [BuilderScope].
         * */
        @JvmField
        public val option: TorOption,
        init: Any,
    ) {

        /**
         * The argument string for this [BuilderScope], the result of which
         * is used as the [LineItem.argument]. Is initialized with whatever
         * [TorOption.default] is.
         * */
        @JvmField
        protected var argument: String = option.default

        /** @suppress */
        @JvmField
        protected val optionals: LinkedHashSet<String> = LinkedHashSet(1, 1.0f)
        /** @suppress */
        @JvmField
        protected val others: LinkedHashSet<LineItem> = LinkedHashSet(1, 1.0f)
        /** @suppress */
        @JvmField
        protected val extras: LinkedHashMap<String, Any> = LinkedHashMap(1, 1.0f)

        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal open fun build(): TorSetting {
            val root = option.toLineItem(argument, optionals)

            return if (others.isEmpty()) {
                root.toSetting(extras)
            } else {
                val group = LinkedHashSet<LineItem>(1 + others.size, 1.0f)
                group.add(root)
                group.addAll(others)

                group.toSetting(extras)
            }
        }

        protected companion object {
            @JvmSynthetic
            internal val INIT = Any()
        }

        init {
            check(init == INIT) { "TorSetting.BuilderScope cannot be extended" }
        }
    }

    /** @suppress */
    public override fun equals(other: Any?): Boolean = other is TorSetting && other.items.first() == items.first()
    /** @suppress */
    public override fun hashCode(): Int = 17 * 31 + items.first().hashCode()
    /** @suppress */
    public override fun toString(): String = buildString { items.joinTo(this, separator = "\n") }
}
