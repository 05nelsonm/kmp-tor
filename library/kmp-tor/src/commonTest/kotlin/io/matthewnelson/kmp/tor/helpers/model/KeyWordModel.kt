/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.helpers.model

import io.matthewnelson.kmp.tor.controller.common.config.TorConfig

class KeyWordModel {
    fun getAll(): Set<TorConfig.KeyWord> {
        @Suppress("DEPRECATION")
        val keyWords = mutableSetOf<TorConfig.KeyWord>().apply {
            add(TorConfig.KeyWord.DisableNetwork)
            add(TorConfig.KeyWord.ControlPort)
            add(TorConfig.KeyWord.CookieAuthentication)
            add(TorConfig.KeyWord.CookieAuthFile)
            add(TorConfig.KeyWord.ControlPortWriteToFile)
            add(TorConfig.KeyWord.DataDirectory)
            add(TorConfig.KeyWord.CacheDirectory)
            add(TorConfig.KeyWord.RunAsDaemon)
            add(TorConfig.KeyWord.SyslogIdentityTag)
            add(TorConfig.KeyWord.ConnectionPadding)
            add(TorConfig.KeyWord.ReducedConnectionPadding)
            add(TorConfig.KeyWord.GeoIPExcludeUnknown)
            add(TorConfig.KeyWord.ClientOnionAuthDir)
            add(TorConfig.KeyWord.SocksPort)
            add(TorConfig.KeyWord.HttpTunnelPort)
            add(TorConfig.KeyWord.TransPort)
            add(TorConfig.KeyWord.AutomapHostsOnResolve)
            add(TorConfig.KeyWord.DnsPort)
            add(TorConfig.KeyWord.DormantClientTimeout)
            add(TorConfig.KeyWord.DormantTimeoutDisabledByIdleStreams)
            add(TorConfig.KeyWord.DormantOnFirstStartup)
            add(TorConfig.KeyWord.DormantCanceledByStartup)
            add(TorConfig.KeyWord.GeoIPFile)
            add(TorConfig.KeyWord.GeoIPv6File)
            add(TorConfig.KeyWord.HiddenServiceDir)
            add(TorConfig.KeyWord.HiddenServicePort)
            add(TorConfig.KeyWord.HiddenServiceMaxStreams)
            add(TorConfig.KeyWord.HiddenServiceMaxStreamsCloseCircuit)
            add(TorConfig.KeyWord.__OwningControllerProcess)

            add(TorConfig.KeyWord.BandwidthRate)
            add(TorConfig.KeyWord.BandwidthBurst)
            add(TorConfig.KeyWord.MaxAdvertisedBandwidth)
            add(TorConfig.KeyWord.RelayBandwidthRate)
            add(TorConfig.KeyWord.RelayBandwidthBurst)
            add(TorConfig.KeyWord.PerConnBWRate)
            add(TorConfig.KeyWord.PerConnBWBurst)
            add(TorConfig.KeyWord.ClientTransportPlugin)
            add(TorConfig.KeyWord.ServerTransportPlugin)
            add(TorConfig.KeyWord.ServerTransportListenAddr)
            add(TorConfig.KeyWord.ServerTransportOptions)
            add(TorConfig.KeyWord.ExtORPort)
            add(TorConfig.KeyWord.ExtORPortCookieAuthFile)
            add(TorConfig.KeyWord.ExtORPortCookieAuthFileGroupReadable)
            add(TorConfig.KeyWord.ConnLimit)
            add(TorConfig.KeyWord.ConstrainedSockets)
            add(TorConfig.KeyWord.ConstrainedSockSize)
            add(TorConfig.KeyWord.ControlSocket)
            add(TorConfig.KeyWord.ControlSocketsGroupWritable)
            add(TorConfig.KeyWord.HashedControlPassword)
            add(TorConfig.KeyWord.CookieAuthFileGroupReadable)
            add(TorConfig.KeyWord.ControlPortFileGroupReadable)
            add(TorConfig.KeyWord.DataDirectoryGroupReadable)
            add(TorConfig.KeyWord.CacheDirectoryGroupReadable)
            add(TorConfig.KeyWord.FallbackDir)
            add(TorConfig.KeyWord.UseDefaultFallbackDirs)
            add(TorConfig.KeyWord.DirAuthority)
            add(TorConfig.KeyWord.DirAuthorityFallbackRate)
            add(TorConfig.KeyWord.AlternateBridgeAuthority)
            add(TorConfig.KeyWord.DisableAllSwap)
            add(TorConfig.KeyWord.DisableDebuggerAttachment)
            add(TorConfig.KeyWord.FetchDirInfoEarly)
            add(TorConfig.KeyWord.FetchDirInfoExtraEarly)
            add(TorConfig.KeyWord.FetchHidServDescriptors)
            add(TorConfig.KeyWord.FetchServerDescriptors)
            add(TorConfig.KeyWord.FetchUselessDescriptors)
            add(TorConfig.KeyWord.HTTPSProxy)
            add(TorConfig.KeyWord.HTTPSProxyAuthenticator)
            add(TorConfig.KeyWord.Sandbox)
            add(TorConfig.KeyWord.Socks4Proxy)
            add(TorConfig.KeyWord.Socks5Proxy)
            add(TorConfig.KeyWord.Socks5ProxyPassword)
            add(TorConfig.KeyWord.UnixSocksGroupWritable)
            add(TorConfig.KeyWord.KeepalivePeriod)
            add(TorConfig.KeyWord.Log)
            add(TorConfig.KeyWord.LogMessageDomains)
            add(TorConfig.KeyWord.MaxUnparseableDescSizeToLog)
            add(TorConfig.KeyWord.OutboundBindAddress)
            add(TorConfig.KeyWord.OutboundBindAddressOR)
            add(TorConfig.KeyWord.OutboundBindAddressExit)
            add(TorConfig.KeyWord.PidFile)
            add(TorConfig.KeyWord.ProtocolWarnings)
            add(TorConfig.KeyWord.LogTimeGranularity)
            add(TorConfig.KeyWord.TruncateLogFile)
            add(TorConfig.KeyWord.AndroidIdentityTag)
            add(TorConfig.KeyWord.SafeLogging)
            add(TorConfig.KeyWord.User)
            add(TorConfig.KeyWord.KeepBindCapabilities)
            add(TorConfig.KeyWord.HardwareAccel)
            add(TorConfig.KeyWord.AccelName)
            add(TorConfig.KeyWord.AccelDir)
            add(TorConfig.KeyWord.AvoidDiskWrites)
            add(TorConfig.KeyWord.CircuitPriorityHalflife)
            add(TorConfig.KeyWord.CountPrivateBandwidth)
            add(TorConfig.KeyWord.ExtendByEd25519ID)
            add(TorConfig.KeyWord.NoExec)
            add(TorConfig.KeyWord.Schedulers)
            add(TorConfig.KeyWord.KISTSchedRunInterval)
            add(TorConfig.KeyWord.KISTSockBufSizeFactor)
            add(TorConfig.KeyWord.Bridge)
            add(TorConfig.KeyWord.LearnCircuitBuildTimeout)
            add(TorConfig.KeyWord.CircuitBuildTimeout)
            add(TorConfig.KeyWord.CircuitsAvailableTimeout)
            add(TorConfig.KeyWord.CircuitStreamTimeout)
            add(TorConfig.KeyWord.ClientOnly)
            add(TorConfig.KeyWord.ExcludeNodes)
            add(TorConfig.KeyWord.ExcludeExitNodes)
            add(TorConfig.KeyWord.ExitNodes)
            add(TorConfig.KeyWord.MiddleNodes)
            add(TorConfig.KeyWord.EntryNodes)
            add(TorConfig.KeyWord.StrictNodes)
            add(TorConfig.KeyWord.FascistFirewall)
            add(TorConfig.KeyWord.ReachableAddresses)
            add(TorConfig.KeyWord.ReachableORAddresses)
            add(TorConfig.KeyWord.HidServAuth)
            add(TorConfig.KeyWord.LongLivedPorts)
            add(TorConfig.KeyWord.MapAddress)
            add(TorConfig.KeyWord.NewCircuitPeriod)
            add(TorConfig.KeyWord.MaxCircuitDirtiness)
            add(TorConfig.KeyWord.MaxClientCircuitsPending)
            add(TorConfig.KeyWord.NodeFamily)
            add(TorConfig.KeyWord.EnforceDistinctSubnets)
            add(TorConfig.KeyWord.SocksPolicy)
            add(TorConfig.KeyWord.SocksTimeout)
            add(TorConfig.KeyWord.TokenBucketRefillInterval)
            add(TorConfig.KeyWord.TrackHostExits)
            add(TorConfig.KeyWord.TrackHostExitsExpire)
            add(TorConfig.KeyWord.UpdateBridgesFromAuthority)
            add(TorConfig.KeyWord.UseBridges)
            add(TorConfig.KeyWord.UseEntryGuards)
            add(TorConfig.KeyWord.GuardfractionFile)
            add(TorConfig.KeyWord.UseGuardFraction)
            add(TorConfig.KeyWord.NumEntryGuards)
            add(TorConfig.KeyWord.NumPrimaryGuards)
            add(TorConfig.KeyWord.NumDirectoryGuards)
            add(TorConfig.KeyWord.GuardLifetime)
            add(TorConfig.KeyWord.SafeSocks)
            add(TorConfig.KeyWord.TestSocks)
            add(TorConfig.KeyWord.VirtualAddrNetworkIPv4)
            add(TorConfig.KeyWord.VirtualAddrNetworkIPv6)
            add(TorConfig.KeyWord.AllowNonRFC953Hostnames)
            add(TorConfig.KeyWord.TransProxyType)
            add(TorConfig.KeyWord.NATDPort)
            add(TorConfig.KeyWord.AutomapHostsSuffixes)
            add(TorConfig.KeyWord.ClientDNSRejectInternalAddresses)
            add(TorConfig.KeyWord.ClientRejectInternalAddresses)
            add(TorConfig.KeyWord.DownloadExtraInfo)
            add(TorConfig.KeyWord.WarnPlaintextPorts)
            add(TorConfig.KeyWord.RejectPlaintextPorts)
            add(TorConfig.KeyWord.OptimisticData)
            add(TorConfig.KeyWord.HSLayer2Nodes)
            add(TorConfig.KeyWord.HSLayer3Nodes)
            add(TorConfig.KeyWord.UseMicrodescriptors)
            add(TorConfig.KeyWord.PathBiasScaleThreshold)
            add(TorConfig.KeyWord.PathBiasScaleUseThreshold)
            add(TorConfig.KeyWord.ClientUseIPv4)
            add(TorConfig.KeyWord.ClientUseIPv6)
            add(TorConfig.KeyWord.ClientPreferIPv6ORPort)
            add(TorConfig.KeyWord.ClientAutoIPv6ORPort)
            add(TorConfig.KeyWord.PathsNeededToBuildCircuits)
            add(TorConfig.KeyWord.ClientBootstrapConsensusAuthorityDownloadInitialDelay)
            add(TorConfig.KeyWord.ClientBootstrapConsensusFallbackDownloadInitialDelay)
            add(TorConfig.KeyWord.ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay)
            add(TorConfig.KeyWord.ClientBootstrapConsensusMaxInProgressTries)
            add(TorConfig.KeyWord.Address)
            add(TorConfig.KeyWord.AssumeReachable)
            add(TorConfig.KeyWord.BridgeRelay)
            add(TorConfig.KeyWord.BridgeDistribution)
            add(TorConfig.KeyWord.ContactInfo)
            add(TorConfig.KeyWord.ExitRelay)
            add(TorConfig.KeyWord.ExitPolicy)
            add(TorConfig.KeyWord.ExitPolicyRejectPrivate)
            add(TorConfig.KeyWord.ExitPolicyRejectLocalInterfaces)
            add(TorConfig.KeyWord.ReducedExitPolicy)
            add(TorConfig.KeyWord.IPv6Exit)
            add(TorConfig.KeyWord.MaxOnionQueueDelay)
            add(TorConfig.KeyWord.MyFamily)
            add(TorConfig.KeyWord.Nickname)
            add(TorConfig.KeyWord.NumCPUs)
            add(TorConfig.KeyWord.ORPort)
            add(TorConfig.KeyWord.PublishServerDescriptor)
            add(TorConfig.KeyWord.ShutdownWaitLength)
            add(TorConfig.KeyWord.SSLKeyLifetime)
            add(TorConfig.KeyWord.HeartbeatPeriod)
            add(TorConfig.KeyWord.MainloopStats)
            add(TorConfig.KeyWord.AccountingMax)
            add(TorConfig.KeyWord.AccountingRule)
            add(TorConfig.KeyWord.AccountingStart)
            add(TorConfig.KeyWord.RefuseUnknownExits)
            add(TorConfig.KeyWord.ServerDNSResolvConfFile)
            add(TorConfig.KeyWord.ServerDNSAllowBrokenConfig)
            add(TorConfig.KeyWord.ServerDNSSearchDomains)
            add(TorConfig.KeyWord.ServerDNSDetectHijacking)
            add(TorConfig.KeyWord.ServerDNSTestAddresses)
            add(TorConfig.KeyWord.ServerDNSAllowNonRFC953Hostnames)
            add(TorConfig.KeyWord.BridgeRecordUsageByCountry)
            add(TorConfig.KeyWord.ServerDNSRandomizeCase)
            add(TorConfig.KeyWord.CellStatistics)
            add(TorConfig.KeyWord.PaddingStatistics)
            add(TorConfig.KeyWord.DirReqStatistics)
            add(TorConfig.KeyWord.EntryStatistics)
            add(TorConfig.KeyWord.ExitPortStatistics)
            add(TorConfig.KeyWord.ConnDirectionStatistics)
            add(TorConfig.KeyWord.HiddenServiceStatistics)
            add(TorConfig.KeyWord.ExtraInfoStatistics)
            add(TorConfig.KeyWord.ExtendAllowPrivateAddresses)
            add(TorConfig.KeyWord.MaxMemInQueues)
            add(TorConfig.KeyWord.DisableOOSCheck)
            add(TorConfig.KeyWord.SigningKeyLifetime)
            add(TorConfig.KeyWord.OfflineMasterKey)
            add(TorConfig.KeyWord.KeyDirectory)
            add(TorConfig.KeyWord.KeyDirectoryGroupReadable)
            add(TorConfig.KeyWord.RephistTrackTime)
            add(TorConfig.KeyWord.DirPortFrontPage)
            add(TorConfig.KeyWord.DirPort)
            add(TorConfig.KeyWord.DirPolicy)
            add(TorConfig.KeyWord.DirCache)
            add(TorConfig.KeyWord.MaxConsensusAgeForDiffs)
            add(TorConfig.KeyWord.DoSCircuitCreationEnabled)
            add(TorConfig.KeyWord.DoSCircuitCreationMinConnections)
            add(TorConfig.KeyWord.DoSCircuitCreationRate)
            add(TorConfig.KeyWord.DoSCircuitCreationBurst)
            add(TorConfig.KeyWord.DoSCircuitCreationDefenseType)
            add(TorConfig.KeyWord.DoSCircuitCreationDefenseTimePeriod)
            add(TorConfig.KeyWord.DoSConnectionEnabled)
            add(TorConfig.KeyWord.DoSConnectionMaxConcurrentCount)
            add(TorConfig.KeyWord.DoSConnectionDefenseType)
            add(TorConfig.KeyWord.DoSRefuseSingleHopClientRendezvous)
            add(TorConfig.KeyWord.AuthoritativeDirectory)
            add(TorConfig.KeyWord.V3AuthoritativeDirectory)
            add(TorConfig.KeyWord.VersioningAuthoritativeDirectory)
            add(TorConfig.KeyWord.RecommendedVersions)
            add(TorConfig.KeyWord.RecommendedPackages)
            add(TorConfig.KeyWord.RecommendedClientVersions)
            add(TorConfig.KeyWord.BridgeAuthoritativeDir)
            add(TorConfig.KeyWord.MinUptimeHidServDirectoryV2)
            add(TorConfig.KeyWord.RecommendedServerVersions)
            add(TorConfig.KeyWord.ConsensusParams)
            add(TorConfig.KeyWord.DirAllowPrivateAddresses)
            add(TorConfig.KeyWord.AuthDirBadExit)
            add(TorConfig.KeyWord.AuthDirInvalid)
            add(TorConfig.KeyWord.AuthDirReject)
            add(TorConfig.KeyWord.AuthDirRejectCCs)
            add(TorConfig.KeyWord.AuthDirListBadExits)
            add(TorConfig.KeyWord.AuthDirMaxServersPerAddr)
            add(TorConfig.KeyWord.AuthDirFastGuarantee)
            add(TorConfig.KeyWord.AuthDirGuardBWGuarantee)
            add(TorConfig.KeyWord.AuthDirPinKeys)
            add(TorConfig.KeyWord.AuthDirSharedRandomness)
            add(TorConfig.KeyWord.AuthDirTestEd25519LinkKeys)
            add(TorConfig.KeyWord.BridgePassword)
            add(TorConfig.KeyWord.V3AuthVotingInterval)
            add(TorConfig.KeyWord.V3AuthVoteDelay)
            add(TorConfig.KeyWord.V3AuthDistDelay)
            add(TorConfig.KeyWord.V3AuthNIntervalsValid)
            add(TorConfig.KeyWord.V3BandwidthsFile)
            add(TorConfig.KeyWord.V3AuthUseLegacyKey)
            add(TorConfig.KeyWord.AuthDirHasIPv6Connectivity)
            add(TorConfig.KeyWord.MinMeasuredBWsForAuthToIgnoreAdvertised)
            add(TorConfig.KeyWord.PublishHidServDescriptors)
            add(TorConfig.KeyWord.HiddenServiceVersion)
            add(TorConfig.KeyWord.HiddenServiceAuthorizeClient)
            add(TorConfig.KeyWord.HiddenServiceAllowUnknownPorts)
            add(TorConfig.KeyWord.HiddenServiceExportCircuitID)
            add(TorConfig.KeyWord.RendPostPeriod)
            add(TorConfig.KeyWord.HiddenServiceDirGroupReadable)
            add(TorConfig.KeyWord.HiddenServiceNumIntroductionPoints)
            add(TorConfig.KeyWord.HiddenServiceSingleHopMode)
            add(TorConfig.KeyWord.HiddenServiceNonAnonymousMode)
            add(TorConfig.KeyWord.TestingTorNetwork)
            add(TorConfig.KeyWord.TestingV3AuthInitialVotingInterval)
            add(TorConfig.KeyWord.TestingV3AuthInitialVoteDelay)
            add(TorConfig.KeyWord.TestingV3AuthInitialDistDelay)
            add(TorConfig.KeyWord.TestingV3AuthVotingStartOffset)
            add(TorConfig.KeyWord.TestingAuthDirTimeToLearnReachability)
            add(TorConfig.KeyWord.TestingMinFastFlagThreshold)
            add(TorConfig.KeyWord.TestingServerDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingClientDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingServerConsensusDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingClientConsensusDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingBridgeDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingBridgeBootstrapDownloadInitialDelay)
            add(TorConfig.KeyWord.TestingClientMaxIntervalWithoutRequest)
            add(TorConfig.KeyWord.TestingDirConnectionMaxStall)
            add(TorConfig.KeyWord.TestingDirAuthVoteExit)
            add(TorConfig.KeyWord.TestingDirAuthVoteExitIsStrict)
            add(TorConfig.KeyWord.TestingDirAuthVoteGuard)
            add(TorConfig.KeyWord.TestingDirAuthVoteGuardIsStrict)
            add(TorConfig.KeyWord.TestingDirAuthVoteHSDir)
            add(TorConfig.KeyWord.TestingDirAuthVoteHSDirIsStrict)
            add(TorConfig.KeyWord.TestingEnableConnBwEvent)
            add(TorConfig.KeyWord.TestingEnableCellStatsEvent)
            add(TorConfig.KeyWord.TestingMinExitFlagThreshold)
            add(TorConfig.KeyWord.TestingLinkCertLifetime)
            add(TorConfig.KeyWord.TestingAuthKeyLifetime)
            add(TorConfig.KeyWord.TestingSigningKeySlop)
            add(TorConfig.KeyWord.__ControlPort)
            add(TorConfig.KeyWord.__DirPort)
            add(TorConfig.KeyWord.__DNSPort)
            add(TorConfig.KeyWord.__ExtORPort)
            add(TorConfig.KeyWord.__NATDPort)
            add(TorConfig.KeyWord.__ORPort)
            add(TorConfig.KeyWord.__SocksPort)
            add(TorConfig.KeyWord.__HttpTunnelPort)
            add(TorConfig.KeyWord.__TransPort)
            add(TorConfig.KeyWord.__AllDirActionsPrivate)
            add(TorConfig.KeyWord.__DisablePredictedCircuits)
            add(TorConfig.KeyWord.__LeaveStreamsUnattached)
            add(TorConfig.KeyWord.__HashedControlSessionPassword)
            add(TorConfig.KeyWord.__ReloadTorrcOnSIGHUP)
            add(TorConfig.KeyWord.__OwningControllerFD)
            add(TorConfig.KeyWord.__DisableSignalHandlers)

            add(TorConfig.KeyWord.HTTPProxy)
            add(TorConfig.KeyWord.HTTPProxyAuthenticator)
            add(TorConfig.KeyWord.FirewallPorts)
            add(TorConfig.KeyWord.ReachableDirAddresses)
            add(TorConfig.KeyWord.ClientPreferIPv6DirPort)
        }

        return keyWords
    }
}
