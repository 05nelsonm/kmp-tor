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
package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItemOrNull
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import io.matthewnelson.kmp.tor.runtime.core.internal.isSingleLine
import kotlin.jvm.*

/**
 * Holder for a "setting".
 *
 * Most [TorOption] are 1 [LineItem], but there are some which
 * require "grouping", such as Hidden Services. For example,
 * declaring a Hidden Service in a configuration requires that
 * [TorOption.HiddenServiceDir] be declared first, and a minimum
 * of 1 declaration of [TorOption.HiddenServicePort] following
 * it. In this case, [items] would contain all Hidden Service
 * [LineItem] for that Hidden Service instance.
 *
 * @see [toSetting]
 * @see [toSettingOrNull]
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
     *       __SocksPort     9050     OnionTrafficOnly IsolateDestPort
     *
     * @see [toLineItem]
     * @see [toLineItemOrNull]
     * */
    public class LineItem private constructor(

        /**
         * The [TorOption], or "keyword" for this single line expression.
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
         * and [hashCode] consideration. All contents will be non-blank and single
         * line. Can be empty.
         * */
        @JvmField
        public val optionals: Set<String>,
    ) {

        public companion object {

            /**
             * Creates a [LineItem] for the [TorOption] and provided
             * [argument] and [optionals].
             *
             * @throws [IllegalArgumentException] when:
             *  - [argument] is blank.
             *  - [argument] is multiple lines.
             *  - [optionals] contains a value that is blank.
             *  - [optionals] contains a value that is multiple lines.
             * */
            @JvmStatic
            @JvmOverloads
            @JvmName("get")
            @ExperimentalKmpTorApi
            @Throws(IllegalArgumentException::class)
            public fun TorOption.toLineItem(
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

            /**
             * Creates a [LineItem] for the [TorOption] and provided
             * [argument] and [optionals].
             *
             * @return [LineItem], or `null` when:
             *  - [argument] is blank.
             *  - [argument] is multiple lines.
             *  - [optionals] contains a value that is blank.
             *  - [optionals] contains a value that is multiple lines.
             * */
            @JvmStatic
            @JvmOverloads
            @JvmName("getOrNull")
            @ExperimentalKmpTorApi
            public fun TorOption.toLineItemOrNull(
                argument: String,
                optionals: Set<String> = emptySet(),
            ): LineItem? = try {
                toLineItem(argument, optionals)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

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
         * [TorOption.Attribute.UNIX_SOCKET], and is configured with
         * an [argument] starting with `unix:`.
         * */
        @JvmField
        public val isUnixSocket: Boolean =
            option.attributes.contains(TorOption.Attribute.UNIX_SOCKET)
            && if (option is TorOption.HiddenServicePort) {
                // Check target, not virtual port
                argument.substringAfter(' ')
            } else {
                argument
            }.startsWith("unix:")

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
        public val isPortAndAuto: Boolean =
            isPort && argument == TorOption.AUTO

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured as `0`.
         * */
        @JvmField
        public val isPortAndDisabled: Boolean =
            isPort && argument == Port.ZERO.toString()

        /**
         * If this [LineItem] is a [TorOption] with the attribute
         * [TorOption.Attribute.PORT], and is configured as neither
         * `0` or `auto` (e.g. it is 9050).
         * */
        @JvmField
        public val isPortAndDistinct: Boolean =
            !isPortAndAuto && !isPortAndDisabled

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

        /** @suppress */
        public override fun equals(other: Any?): Boolean = equalsPrivate(other)
        /** @suppress */
        public override fun hashCode(): Int = hashCodePrivate()
        /** @suppress */
        public override fun toString(): String = toStringPrivate()
    }

    public companion object {

        /**
         * Creates the [TorSetting] for a single [LineItem].
         *
         * TODO: Shouldn't throw if the only thing checked is
         *  a non-empty Set<LineItem>. May want to keep though
         *  in case of further validation, such as ensuring
         *  if [LineItem.option] contains the attribute
         *  [TorOption.Attribute.HIDDEN_SERVICE], that it will
         *  throw if at least [TorOption.HiddenServiceDir] and
         *  1 [TorOption.HiddenServicePort] are not present.
         * */
        @JvmStatic
        @JvmOverloads
        @JvmName("get")
        @ExperimentalKmpTorApi
        @Throws(IllegalArgumentException::class)
        public fun LineItem.toSetting(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting = immutableSetOf(this).toSetting(extras)

        @JvmStatic
        @JvmOverloads
        @ExperimentalKmpTorApi
        @JvmName("getOrNull")
        public fun LineItem.toSettingOrNull(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting? = immutableSetOf(this).toSettingOrNull(extras)

        @JvmStatic
        @JvmOverloads
        @JvmName("get")
        @ExperimentalKmpTorApi
        @Throws(IllegalArgumentException::class)
        public fun Set<LineItem>.toSetting(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting {
            val items = toImmutableSet()

            require(items.isNotEmpty()) { "line items cannot be empty" }

            // TODO: Verify things.
            //  - Hidden Service attr requires HiddenServiceDir first item
            //    with
            //  - Many different things are single LineItem

            return TorSetting(items, extras.toImmutableMap())
        }

        @JvmStatic
        @JvmOverloads
        @JvmName("getOrNull")
        @ExperimentalKmpTorApi
        public fun Set<LineItem>.toSettingOrNull(
            extras: Map<String, Any> = emptyMap(),
        ): TorSetting? = try {
            toSetting(extras)
        } catch (_: IllegalArgumentException) {
            null
        }

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [TorConfig2] which contain, and are configured for, parameter
         * [A].
         *
         * For example, if [TorOption.Attribute.UNIX_SOCKET] is parameter [A]
         * and 2 declarations of [TorOption.ControlPort] are present (one
         * configured as a Unix Socket, and the other as a TCP port), then
         * only the one configured as a Unix Socket will be present in the
         * returned list.
         *
         * @see [Iterable.filterByAttribute]
         * */
        @JvmStatic
        public inline fun <reified A: TorOption.Attribute> TorConfig2.filterByAttribute(): List<TorSetting> {
            return settings.filterByAttribute<A>()
        }

        /**
         * Returns a list containing all elements of [TorSetting] within
         * the [Iterable] which contain, and are configured for, parameter
         * [A].
         *
         * For example, if [TorOption.Attribute.UNIX_SOCKET] is parameter [A]
         * and 2 declarations of [TorOption.ControlPort] are present (one
         * configured as a Unix Socket, and the other as a TCP port), then
         * only the one configured as a Unix Socket will be present in the
         * returned list.
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
         * TODO
         *
         * @see [Iterable.filterByOption]
         * */
        @JvmStatic
        public inline fun <reified O: TorOption> TorConfig2.filterByOption(): List<TorSetting> {
            return settings.filterByOption<O>()
        }

        /**
         * TODO
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
            @OptIn(ExperimentalKmpTorApi::class)
            val root = option.toLineItem(argument, optionals)

            @OptIn(ExperimentalKmpTorApi::class)
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
    public override fun equals(other: Any?): Boolean = equalsPrivate(other)
    /** @suppress */
    public override fun hashCode(): Int = hashCodePrivate()
    /** @suppress */
    public override fun toString(): String = toStringPrivate()
}

private fun TorSetting.LineItem.equalsPrivate(other: Any?): Boolean {
    if (other !is TorSetting.LineItem) return false

    if (other.option.isUnique || this.option.isUnique) {
        // If either are unique, compare only the option.
        //
        // This is safe from non-persistent namespace
        // because only non-persistent options that
        // are unique do not have persistent counterparts
        return other.option == this.option
    }

    // Have to compare 2 non-unique Items

    // Use option name to compare, as there are non-persistent
    // options which are not unique that also have persistent
    // counterparts (e.g. __ControlPort and ControlPort)
    val thisName = if (this.isNonPersistent) this.option.name.drop(2) else this.option.name
    val otherName = if (other.isNonPersistent) other.option.name.drop(2) else other.option.name

    // If both are ports
    if (other.isPort && this.isPort) {
        return if (!other.isPortAndDistinct || !this.isPortAndDistinct) {
            otherName == thisName && other.argument == this.argument
        } else {
            // Neither are disabled or set to auto, compare
            // only their port arguments (or unix socket paths)
            other.argument == this.argument
        }
    }

    // If both are Unix Sockets
    if (other.isUnixSocket && this.isUnixSocket) {
        return other.argument == this.argument
    }

    // if both are file system paths
    val thisIsFs = this.isFile || this.isDirectory
    val otherIsFs = other.isFile || other.isDirectory

    if (otherIsFs && thisIsFs) {
        // compare only by their arguments (file system paths)
        return other.argument == this.argument
    }

    return  otherName == thisName
            && other.argument == this.argument
}

private fun TorSetting.LineItem.hashCodePrivate(): Int {
    var result = 13
    if (option.isUnique) {
        return result * 42 + option.hashCode()
    }

    // Not unique, need to handle non-persistent options.
    // Instead of using option.hashCode() which would be
    // different for non-persistent and persistent options
    // of the same category, the name must always be used
    // w/o the prefixing `__`.
    val nameHashCode = if (isNonPersistent) {
        option.name.drop(2)
    } else {
        option.name
    }.hashCode()

    if (!isPortAndDistinct) {
        // If a port is `0` or `auto`, it needs to register
        // as the "same" as another port option of the same
        // namespace (e.g. ControlPort and ControlPort and
        // __ControlPort).

        result = result * 42 + nameHashCode
        return result * 42 + argument.hashCode()
    }

    if (isUnixSocket || isPort || isFile || isDirectory) {
        // TCP Ports and filesystem paths are shared across
        // the host, so only consider the argument and not
        // what option it is. If there is a collision of
        // host namespace with another option (e.g. DataDirectory
        // and CacheDirectory are set to the same path), it
        // needs to register as such.
        return result * 42 + argument.hashCode()
    }

    // Was something else, do name & argument only
    result = result * 42 + nameHashCode
    return result * 42 + argument.hashCode()
}

private fun TorSetting.LineItem.toStringPrivate(): String {
    return buildString {
        append(option)
        append(' ')
        append(argument)
        if (optionals.isNotEmpty()) {
            append(' ')
            optionals.joinTo(this, separator = " ")
        }
    }
}

private fun TorSetting.equalsPrivate(other: Any?): Boolean {
    return  other is TorSetting && other.items.first() == items.first()
}

private fun TorSetting.hashCodePrivate(): Int {
    return 17 * 31 + items.first().hashCode()
}

private fun TorSetting.toStringPrivate(): String {
    return buildString { items.joinTo(this, separator = "\n") }
}
