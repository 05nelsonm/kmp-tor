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
package io.matthewnelson.kmp.tor.manager.internal.ext

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.*
import kotlin.jvm.JvmSynthetic

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.dnsOpened(value: String): AddressInfo? {
    return if (dns != value) {
        copy(dns = value)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.dnsClosed(value: String): AddressInfo? {
    return if (dns == value) {
        copy(dns = null)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.httpOpened(value: String): AddressInfo? {
    return if (http != value) {
        copy(http = value)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.httpClosed(value: String): AddressInfo? {
    return if (http == value) {
        copy(http = null)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.socksOpened(value: String): AddressInfo? {
    return if (socks != value) {
        copy(socks = value)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.socksClosed(value: String): AddressInfo? {
    return if (socks == value) {
        copy(socks = null)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.transOpened(value: String): AddressInfo? {
    return if (trans != value) {
        copy(trans = value)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.transClosed(value: String): AddressInfo? {
    return if (trans == value) {
        copy(trans = null)
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.onStateChange(old: State, new: State): AddressInfo? {
    // Tor went from On, to something else
    if (old.isOn && !new.isOn) {
        return AddressInfo()
    }

    // DisableNetwork was set to true
    if (old.isNetworkEnabled && new.isNetworkDisabled && new.torState.isBootstrapped) {
        return AddressInfo()
    }

    // DisableNetwork was set to false (network re-enabled)
    // and Tor was already bootstrapped
    if (old.isNetworkDisabled && new.isNetworkEnabled && new.torState.isBootstrapped) {
        return this
    }

    // Bootstrapping completed
    if (
        !old.torState.isBootstrapped &&
        new.torState.isBootstrapped &&
        new.isNetworkEnabled
    ) {
        return this
    }

    return null
}
