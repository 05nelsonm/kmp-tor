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
    val mDns = dns?.toMutableSet() ?: return copy(dns = setOf(value))

    return if (mDns.add(value)) {
        copy(dns = mDns.toSet())
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.dnsClosed(value: String): AddressInfo? {
    val mDns = dns?.toMutableSet() ?: return null

    return if (mDns.remove(value)) {
        if (mDns.isEmpty()) {
            copy(dns = null)
        } else {
            copy(dns = mDns.toSet())
        }
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.httpOpened(value: String): AddressInfo? {
    val mHttp = http?.toMutableSet() ?: return copy(http = setOf(value))

    return if (mHttp.add(value)) {
        copy(http = mHttp.toSet())
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.httpClosed(value: String): AddressInfo? {
    val mHttp = http?.toMutableSet() ?: return null

    return if (mHttp.remove(value)) {
        if (mHttp.isEmpty()) {
            copy(http = null)
        } else {
            copy(http = mHttp.toSet())
        }
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.socksOpened(value: String): AddressInfo? {
    val mSocks = socks?.toMutableSet() ?: return copy(socks = setOf(value))

    return if (mSocks.add(value)) {
        copy(socks = mSocks.toSet())
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.socksClosed(value: String): AddressInfo? {
    val mSocks = socks?.toMutableSet() ?: return null

    return if (mSocks.remove(value)) {
        if (mSocks.isEmpty()) {
            copy(socks = null)
        } else {
            copy(socks = mSocks.toSet())
        }
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.transOpened(value: String): AddressInfo? {
    val mTrans = trans?.toMutableSet() ?: return copy(trans = setOf(value))

    return if (mTrans.add(value)) {
        copy(trans = mTrans.toSet())
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.transClosed(value: String): AddressInfo? {
    val mTrans = trans?.toMutableSet() ?: return null

    return if (mTrans.remove(value)) {
        if (mTrans.isEmpty()) {
            copy(trans = null)
        } else {
            copy(trans = mTrans.toSet())
        }
    } else {
        null
    }
}

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun AddressInfo.onStateChange(old: State, new: State): AddressInfo? {
    // Tor went from On, to something else
    if (old.isOn && !new.isOn) {
        return AddressInfo.NULL_VALUES
    }

    // DisableNetwork was set to true
    if (old.isNetworkEnabled && new.isNetworkDisabled && new.torState.isBootstrapped) {
        return AddressInfo.NULL_VALUES
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
