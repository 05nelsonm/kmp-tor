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
package io.matthewnelson.kmp.tor.manager.internal

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.common.server.Server
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import kotlinx.atomicfu.locks.SynchronizedObject

internal abstract class BaseTorManager: SynchronizedObject(), TorControlManager {

//    override suspend fun circuitClose(): Result<Any?> {
//        return provide<TorControlCircuitClose, Any?> {
//            circuitClose()
//        }
//    }

//    override suspend fun circuitExtend(): Result<String> {
//        return provide<TorControlCircuitExtend, String> {
//            circuitExtend()
//        }
//    }

//    override suspend fun circuitSetPurpose(): Result<Any?> {
//        return provide<TorControlCircuitSetPurpose, Any?> {
//            circuitSetPurpose()
//        }
//    }

    override suspend fun configGet(setting: TorConfig.Setting<*>): Result<ConfigEntry> {
        return provide<TorControlConfigGet, ConfigEntry> {
            configGet(setting)
        }
    }

    override suspend fun configGet(settings: Set<TorConfig.Setting<*>>): Result<List<ConfigEntry>> {
        return provide<TorControlConfigGet, List<ConfigEntry>> {
            configGet(settings)
        }
    }

    override suspend fun configLoad(config: TorConfig): Result<Any?> {
        return provide<TorControlConfigLoad, Any?> {
            configLoad(config)
        }
    }

    override suspend fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean
    ): Result<Any?> {
        return provide<TorControlConfigReset, Any?> {
            configReset(setting, setDefault)
        }
    }

    override suspend fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean
    ): Result<Any?> {
        return provide<TorControlConfigReset, Any?> {
            configReset(settings, setDefault)
        }
    }

    // TODO: Maybe???
    override suspend fun configSave(force: Boolean): Result<Any?> {
        return provide<TorControlConfigSave, Any?> {
            configSave(force)
        }
    }

    override suspend fun configSet(setting: TorConfig.Setting<*>): Result<Any?> {
        return provide<TorControlConfigSet, Any?> {
            configSet(setting)
        }
    }

    override suspend fun configSet(settings: Set<TorConfig.Setting<*>>): Result<Any?> {
        return provide<TorControlConfigSet, Any?> {
            configSet(settings)
        }
    }

//    override suspend fun descriptorPost(): Result<String> {
//        return provide<TorControlDescriptorPost, String> {
//            descriptorPost()
//        }
//    }

    override suspend fun dropGuards(): Result<Any?> {
        return provide<TorControlDropGuards, Any?> {
            dropGuards()
        }
    }

    override suspend fun hsFetch(address: OnionAddress): Result<Any?> {
        return provide<TorControlHsFetch, Any?> {
            hsFetch(address)
        }
    }

    override suspend fun hsFetch(address: OnionAddress, server: Server.Fingerprint): Result<Any?> {
        return provide<TorControlHsFetch, Any?> {
            hsFetch(address, server)
        }
    }

    override suspend fun hsFetch(
        address: OnionAddress,
        servers: Set<Server.Fingerprint>
    ): Result<Any?> {
        return provide<TorControlHsFetch, Any?> {
            hsFetch(address, servers)
        }
    }

//    override suspend fun hsPost(): Result<Any?> {
//        return provide<TorControlHsPost, Any?> {
//            hsPost()
//        }
//    }

    override suspend fun infoGet(keyword: KeyWord): Result<String> {
        return provide<TorControlInfoGet, String> {
            infoGet(keyword)
        }
    }

    override suspend fun infoGet(keywords: Set<KeyWord>): Result<Map<String, String>> {
        return provide<TorControlInfoGet, Map<String, String>> {
            infoGet(keywords)
        }
    }

//    override suspend fun infoProtocol(): Result<Any?> {
//        return provide<TorControlInfoProtocol, Any?> {
//            infoProtocol()
//        }
//    }

//    override suspend fun mapAddress(): Result<Map<String, String>> {
//        return provide<TorControlMapAddress, Map<String, String>> {
//            mapAddress()
//        }
//    }

    override suspend fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return provide<TorControlOnionAdd, HiddenServiceEntry> {
            onionAdd(privateKey, hsPorts, flags, maxStreams)
        }
    }

    override suspend fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return provide<TorControlOnionAdd, HiddenServiceEntry> {
            onionAddNew(type, hsPorts, flags, maxStreams)
        }
    }

    override suspend fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?
    ): Result<Any?> {
        return provide<TorControlOnionClientAuthAdd, Any?> {
            onionClientAuthAdd(address, key, clientName, flags)
        }
    }

    override suspend fun onionClientAuthRemove(address: OnionAddressV3): Result<Any?> {
        return provide<TorControlOnionClientAuthRemove, Any?> {
            onionClientAuthRemove(address)
        }
    }

    override suspend fun onionClientAuthView(): Result<List<ClientAuthEntry>> {
        return provide<TorControlOnionClientAuthView, List<ClientAuthEntry>> {
            onionClientAuthView()
        }
    }

    override suspend fun onionClientAuthView(address: OnionAddressV3): Result<ClientAuthEntry> {
        return provide<TorControlOnionClientAuthView, ClientAuthEntry> {
            onionClientAuthView(address)
        }
    }

    override suspend fun onionDel(address: OnionAddress): Result<Any?> {
        return provide<TorControlOnionDel, Any?> {
            onionDel(address)
        }
    }

//    override suspend fun resolve(): Result<Any?> {
//        return provide<TorControlResolve, Any?> {
//            resolve()
//        }
//    }

    override suspend fun setEvents(events: Set<TorEvent>, extended: Boolean): Result<Any?> {
        return provide<TorControlSetEvents, Any?> {
            setEvents(events, extended)
        }
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return provide<TorControlSignal, Any?> {
            signal(signal)
        }
    }

//    override suspend fun streamAttach(): Result<Any?> {
//        return provide<TorControlStreamAttach, Any?> {
//            streamAttach()
//        }
//    }

//    override suspend fun streamClose(): Result<Any?> {
//        return provide<TorControlStreamClose, Any?> {
//            streamClose()
//        }
//    }

//    override suspend fun streamRedirect(): Result<Any?> {
//        return provide<TorControlStreamRedirect, Any?> {
//            streamRedirect()
//        }
//    }

//    override suspend fun useFeature(): Result<Any?> {
//        return provide<TorControlUseFeature, Any?> {
//            useFeature()
//        }
//    }

    protected abstract suspend fun <T, V> provide(
        block: suspend T.() -> Result<V>
    ): Result<V>
}
