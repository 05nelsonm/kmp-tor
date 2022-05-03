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
package io.matthewnelson.kmp.tor.controller.common.control.usecase

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import kotlin.jvm.JvmField

/**
 * "GETINFO" 1*(SP keyword) CRLF
 *
 * https://torproject.gitlab.io/torspec/control-spec/#getinfo
 * */
interface TorControlInfoGet {

    suspend fun infoGet(keyword: KeyWord): Result<String>

    suspend fun infoGet(keywords: Set<KeyWord>): Result<Map<String, String>>

    /**
     * KeyWords as detailed in tor spec 3.9 GETINFO
     *
     * https://torproject.gitlab.io/torspec/control-spec/#getinfo
     * */
    @OptIn(InternalTorApi::class)
    sealed class KeyWord(@JvmField val value: String) {

        override fun equals(other: Any?): Boolean {
            return other != null && other is KeyWord && other.value == value
        }

        override fun hashCode(): Int {
            return 17 * 31 + value.hashCode()
        }

        override fun toString(): String {
            return value
        }

        fun compareTo(other: String?): Boolean =
            other != null && other == value

        class Version                               : KeyWord("version")

        sealed class Config(value: String)          : KeyWord("config$value") {
            class File                                  : Config("-file")
            class DefaultsFile                          : Config("-defaults-file")
            class Text                                  : Config("-text")
            class Defaults                              : Config("/defaults")
            class CanSaveConf                           : Config("-can-saveconf")
        }

        sealed class ExitPolicy(value: String)      : KeyWord("exit-policy/$value") {
            class Default                               : ExitPolicy("default")
            sealed class RejectPrivate(value: String)   : ExitPolicy("reject-private/$value") {
                class Default                               : RejectPrivate("default")
                class Relay                                 : RejectPrivate("relay")
            }
            class Ipv4                                  : ExitPolicy("ipv4")
            class Ipv6                                  : ExitPolicy("ipv6")
            class Full                                  : ExitPolicy("full")
        }

        sealed class Desc(value: String)            : KeyWord("desc$value") {
            class AllRecent                             : Desc("/all-recent")
            class Id(identity: String)                  : Desc("/id/$identity") {
                override fun toString(): String {
                    return value.replaceAfter("desc/id/", REDACTED)
                }
            }
            class Name(nickname: String)                : Desc("/name/$nickname") {
                override fun toString(): String {
                    return value.replaceAfter("desc/name/", REDACTED)
                }
            }
            class DownloadEnabled                       : Desc("/download-enabled")
            class Annotations(identity: String)         : Desc("-annotations/id/$identity")
        }

        sealed class MicroDesc(value: String)       : KeyWord("md/$value") {
            class All                                   : MicroDesc("all")
            class Id(identity: String)                  : MicroDesc("id/$identity") {
                override fun toString(): String {
                    return value.replaceAfter("md/id/", REDACTED)
                }
            }
            class Name(nickname: String)                : MicroDesc("name/$nickname") {
                override fun toString(): String {
                    return value.replaceAfter("md/name/", REDACTED)
                }
            }
            class DownloadEnabled                       : MicroDesc("download-enabled")
        }

        class IsDormant                             : KeyWord("dormant")

        // TODO: use an actual wrapper class to get the hex digest
        class ExtraInfo(digest: String)             : KeyWord("extra-info/digest/$digest") {
            override fun toString(): String {
                return value.replaceAfter("extra-info/digest/", REDACTED)
            }
        }

        sealed class NetworkStatus(value: String)   : KeyWord("ns/$value") {
            class All                                   : NetworkStatus("all")
            class Id(identity: String)                  : NetworkStatus("id/$identity") {
                override fun toString(): String {
                    return value.replaceAfter("ns/id/", REDACTED)
                }
            }
            class Name(nickname: String)                : NetworkStatus("name/$nickname") {
                override fun toString(): String {
                    return value.replaceAfter("ns/name/", REDACTED)
                }
            }
            class Purpose(purpose: String = "bridge")   : NetworkStatus("purpose/$purpose")
        }

        sealed class AddressMappings(value: String) : KeyWord("address-mappings/$value") {
            class All                                   : AddressMappings("all")
            class Config                                : AddressMappings("config")
            class Cache                                 : AddressMappings("cache")
            class Control                               : AddressMappings("control")
        }

        class Address                               : KeyWord("address")
        class Fingerprint                           : KeyWord("fingerprint")

        sealed class Status(value: String)          : KeyWord(value) {
            class Circuit                               : Status("circuit-status")
            class Stream                                : Status("stream-status")
            class ORConn                                : Status("orconn-status")

            class CircuitEstablished                    : Status("status/circuit-established")
            class EnoughDirInfo                         : Status("status/enough-dir-info")
            class GoodServerDesc                        : Status("status/good-server-descriptor")
            class AcceptedServerDesc                    : Status("status/accepted-server-descriptor")

            sealed class Reachability(value: String)    : Status("status/reachability-succeeded$value") {
                class SucceededOR                           : Reachability("/or")
                class SucceededDir                          : Reachability("/dir")
                class Succeeded                             : Reachability("")
            }

            class BootstrapPhase                        : Status("status/bootstrap-phase")

            sealed class Version(value: String)         : Status("status/version/$value") {
                class Recommended                           : Version("recommended")
                class Current                               : Version("current")
            }

            class ClientsSeen                           : Status("status/clients-seen")
            class FreshRelayDescs                       : Status("status/fresh-relay-descs")
        }

        class EntryGuards                           : KeyWord("entry-guards")

        sealed class Traffic(value: String)         : KeyWord("traffic/$value") {
            class Read                                  : Traffic("read")
            class Written                               : Traffic("written")
        }

        class Uptime                                : KeyWord("uptime")

        sealed class Accounting(value: String)      : KeyWord("accounting/$value") {
            class Enabled                               : Accounting("enabled")
            class Hibernating                           : Accounting("hibernating")
            class Bytes                                 : Accounting("bytes")
            class BytesLeft                             : Accounting("bytes-left")
            sealed class Interval(value: String)        : Accounting("interval-$value") {
                class Start                                 : Interval("start")
                class Wake                                  : Interval("wake")
                class End                                   : Interval("end")
            }
        }

        sealed class Names(value: String)           : KeyWord("$value/names") {
            class Config                                : Names("info")
            class Info                                  : Names("info")
            class Events                                : Names("events")
            class Features                              : Names("features")
            class Signal                                : Names("signal")
        }

        sealed class IpToCountry(value: String)     : KeyWord("ip-to-country/$value") {
            class Ipv4Avail                             : IpToCountry("ipv4-available")
            class Ipv6Avail                             : IpToCountry("ipv6-available")
            class All                                   : IpToCountry("*")
        }

        sealed class Process(value: String)         : KeyWord("process/$value") {
            class PID                                   : Process("pid")
            class UID                                   : Process("uid")
            class User                                  : Process("user")
            class DescriptorLimit                       : Process("descriptor-limit")
        }

        sealed class Dir(value: String)             : KeyWord("dir/$value") {

            /*
            * TODO: wtf?
            * "dir/status/fp/<F>"
            * "dir/status/fp/<F1>+<F2>+<F3>"
            * */
            sealed class Status(value: String)          : Dir("status$value") {
                class Consensus                             : Status("-vote/current/consensus")
                class ConsensusMicroDesc                    : Status("-vote/current/consensus-microdesc")
                class Authority                             : Status("/authority")
                class All                                   : Status("/all")
            }

            /*
            * TODO: wtf?
            * "dir/server/fp/<F>"
            * "dir/server/fp/<F1>+<F2>+<F3>"
            * "dir/server/d/<D>"
            * "dir/server/d/<D1>+<D2>+<D3>"
            * */
            sealed class Server(value: String)          : Dir("server/$value") {
                class Authority                             : Server("authority")
                class All                                   : Server("all")
            }
        }

        sealed class NetListeners(value: String)    : KeyWord("net/listeners/$value") {
            class All                                   : NetListeners("*")
            class ORConns                               : NetListeners("or")
            class Dir                                   : NetListeners("dir")
            class Socks                                 : NetListeners("socks")
            class Trans                                 : NetListeners("trans")
            class Natd                                  : NetListeners("natd")
            class Dns                                   : NetListeners("dns")
            class Control                               : NetListeners("control")
            class Extor                                 : NetListeners("extor")
            class HttpTunnel                            : NetListeners("httptunnel")
        }

        class BWEventCache                          : KeyWord("bw-event-cache")

        sealed class Consensus(value: String)       : KeyWord("consensus/$value") {
            class ValidAfter                            : Consensus("valid-after")
            class FreshUntil                            : Consensus("fresh-until")
            class ValidUntil                            : Consensus("valid-until")
        }

        sealed class HSDescriptors(value: String)   : KeyWord("hs/$value") {
            class Client(address: OnionAddress)         : HSDescriptors("client/desc/id/${address.value}") {
                override fun toString(): String {
                    return value.replaceAfter("hs/client/desc/id/", REDACTED)
                }
            }
            class Service(address: OnionAddress)        : HSDescriptors("service/desc/id/${address.value}") {
                override fun toString(): String {
                    return value.replaceAfter("hs/service/desc/id/", REDACTED)
                }
            }
        }

        sealed class Onions(value: String)          : KeyWord("onions/$value") {
            class Current                               : Onions("current")
            class Detached                              : Onions("detached")
        }

        class NetworkLiveness                       : KeyWord("network-liveness")

        sealed class Downloads(value: String)       : KeyWord("downloads/$value") {
            sealed class NetworkStatus(value: String)   : Downloads("networkstatus/$value") {
                class NS                                    : NetworkStatus("ns")
                class Bootstrap                             : NetworkStatus("ns/bootstrap")
                class Running                               : NetworkStatus("ns/running")
                sealed class MicroDesc(value: String)       : NetworkStatus("microdesc$value") {
                    class MicroDesc                             : NetworkStatus.MicroDesc("")
                    class Bootstrap                             : NetworkStatus.MicroDesc("/bootstrap")
                    class Running                               : NetworkStatus.MicroDesc("/running")
                }
            }

            sealed class Cert(value: String)            : Downloads("cert/$value") {
                class Fps                                   : Cert("fps")
                class Fp(fingerprint: String)               : Cert("fp/$fingerprint") {
                    override fun toString(): String {
                        return value.replaceAfter("cert/fp/", REDACTED)
                    }
                }
                class FpSks(fingerprint: String)            : Cert("fp/$fingerprint/sks") {
                    override fun toString(): String {
                        return value.replaceAfter("cert/fp/", "$REDACTED/sks")
                    }
                }
                class FpDigest(fingerprint: String, digest: String) : Cert("fp/$fingerprint/$digest") {
                    override fun toString(): String {
                        return value.replaceAfter("cert/fp/", "$REDACTED/$REDACTED")
                    }
                }
            }

            sealed class Desc(value: String)            : Downloads("desc/$value") {
                class Descs                                 : Desc("descs")
                class Digest(digest: String)                : Desc(digest) {
                    override fun toString(): String {
                        return value.replaceAfter("desc/", REDACTED)
                    }
                }
            }

            sealed class Bridge(value: String)          : Downloads("bridge/$value") {
                class Bridges                               : Bridge("bridges")
                class Digest(digest: String)                : Bridge(digest) {
                    override fun toString(): String {
                        return value.replaceAfter("bridge/", REDACTED)
                    }
                }
            }
        }

        sealed class Sr(value: String)              : KeyWord("sr/$value") {
            class Current                               : Sr("current")
            class Previous                              : Sr("previous")
        }

        sealed class CurrentTime(value: String)     : KeyWord("current-time/$value") {
            class Local                                 : CurrentTime("local")
            class UTC                                   : CurrentTime("utc")
        }

        sealed class Limits(value: String)          : KeyWord("limits/$value") {
            class MaxMemInQueues                        : Limits("max-mem-in-queues")
        }
    }

}
