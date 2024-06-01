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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlinx.coroutines.*
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

object TestUtils {

    fun testEnv(
        dirName: String,
        block: ThisBlock<TorRuntime.Environment.Builder> = ThisBlock {}
    ): TorRuntime.Environment = TorRuntime.Environment.Builder(
        workDirectory = SysTempDir.resolve("kmp_tor_test/$dirName/work"),
        cacheDirectory = SysTempDir.resolve("kmp_tor_test/$dirName/cache"),
        installer = { dir -> TorResources(dir) },
        block = block
    ).also { it.debug = true }

    suspend fun <T: Action.Processor> T.ensureStoppedOnTestCompletion(): T {
        currentCoroutineContext().job.invokeOnCompletion {
            enqueue(Action.StopDaemon, {}, {})
        }

        withContext(Dispatchers.Default) { delay(100.milliseconds) }

        return this
    }

    fun List<Lifecycle.Event>.assertDoesNotContain(
        className: String,
        name: Lifecycle.Event.Name,
        fid: FileID? = null,
    ) {
        var error: AssertionError? = null
        try {
            assertContains(className, name, fid)
            error = AssertionError("LCEs contained $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
        } catch (_: AssertionError) {
            // pass
        }

        error?.let { throw it }
    }

    fun List<Lifecycle.Event>.assertContains(
        className: String,
        name: Lifecycle.Event.Name,
        fid: FileID? = null,
    ) {
        for (lce in this) {
            if (lce.className != className) continue
            if (fid != null) {
                if (lce.fid != fid.fidEllipses) continue
            }

            if (lce.name == name) return
        }

        fail("LCEs did not contain $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
    }

    val KEYWORDS by lazy { setOf(
        TorConfig.AccelDir,
        TorConfig.AccelName,
        TorConfig.AccountingMax,
        TorConfig.AccountingRule,
        TorConfig.AccountingStart,
        TorConfig.Address,
        TorConfig.AllowNonRFC953Hostnames,
        TorConfig.AlternateBridgeAuthority,
        TorConfig.AndroidIdentityTag,
        TorConfig.AssumeReachable,
        TorConfig.AuthDirBadExit,
        TorConfig.AuthDirFastGuarantee,
        TorConfig.AuthDirGuardBWGuarantee,
        TorConfig.AuthDirHasIPv6Connectivity,
        TorConfig.AuthDirInvalid,
        TorConfig.AuthDirListBadExits,
        TorConfig.AuthDirMaxServersPerAddr,
        TorConfig.AuthDirPinKeys,
        TorConfig.AuthDirReject,
        TorConfig.AuthDirRejectCCs,
        TorConfig.AuthDirSharedRandomness,
        TorConfig.AuthDirTestEd25519LinkKeys,
        TorConfig.AuthoritativeDirectory,
        TorConfig.AvoidDiskWrites,
        TorConfig.BandwidthBurst,
        TorConfig.BandwidthRate,
        TorConfig.Bridge,
        TorConfig.BridgeAuthoritativeDir,
        TorConfig.BridgeDistribution,
        TorConfig.BridgePassword,
        TorConfig.BridgeRecordUsageByCountry,
        TorConfig.BridgeRelay,
        TorConfig.CacheDirectoryGroupReadable,
        TorConfig.CellStatistics,
        TorConfig.CircuitBuildTimeout,
        TorConfig.CircuitPriorityHalflife,
        TorConfig.CircuitStreamTimeout,
        TorConfig.CircuitsAvailableTimeout,
        TorConfig.ClientAutoIPv6ORPort,
        TorConfig.ClientBootstrapConsensusAuthorityDownloadInitialDelay,
        TorConfig.ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay,
        TorConfig.ClientBootstrapConsensusFallbackDownloadInitialDelay,
        TorConfig.ClientBootstrapConsensusMaxInProgressTries,
        TorConfig.ClientDNSRejectInternalAddresses,
        TorConfig.ClientOnly,
        TorConfig.ClientPreferIPv6ORPort,
        TorConfig.ClientRejectInternalAddresses,
        TorConfig.ClientTransportPlugin,
        TorConfig.ClientUseIPv4,
        TorConfig.ClientUseIPv6,
        TorConfig.ConnDirectionStatistics,
        TorConfig.ConnLimit,
        TorConfig.ConsensusParams,
        TorConfig.ConstrainedSockSize,
        TorConfig.ConstrainedSockets,
        TorConfig.ContactInfo,
        TorConfig.ControlPort,
        TorConfig.ControlPortFileGroupReadable,
        TorConfig.ControlSocket,
        TorConfig.ControlSocketsGroupWritable,
        TorConfig.CookieAuthFileGroupReadable,
        TorConfig.CountPrivateBandwidth,
        TorConfig.DNSPort,
        TorConfig.DataDirectoryGroupReadable,
        TorConfig.DirAllowPrivateAddresses,
        TorConfig.DirAuthority,
        TorConfig.DirAuthorityFallbackRate,
        TorConfig.DirCache,
        TorConfig.DirPolicy,
        TorConfig.DirPort,
        TorConfig.DirPortFrontPage,
        TorConfig.DirReqStatistics,
        TorConfig.DisableAllSwap,
        TorConfig.DisableDebuggerAttachment,
        TorConfig.DisableOOSCheck,
        TorConfig.DoSCircuitCreationBurst,
        TorConfig.DoSCircuitCreationDefenseTimePeriod,
        TorConfig.DoSCircuitCreationDefenseType,
        TorConfig.DoSCircuitCreationEnabled,
        TorConfig.DoSCircuitCreationMinConnections,
        TorConfig.DoSCircuitCreationRate,
        TorConfig.DoSConnectionDefenseType,
        TorConfig.DoSConnectionEnabled,
        TorConfig.DoSConnectionMaxConcurrentCount,
        TorConfig.DoSRefuseSingleHopClientRendezvous,
        TorConfig.DownloadExtraInfo,
        TorConfig.EnforceDistinctSubnets,
        TorConfig.EntryNodes,
        TorConfig.EntryStatistics,
        TorConfig.ExcludeExitNodes,
        TorConfig.ExcludeNodes,
        TorConfig.ExitNodes,
        TorConfig.ExitPolicy,
        TorConfig.ExitPolicyRejectLocalInterfaces,
        TorConfig.ExitPolicyRejectPrivate,
        TorConfig.ExitPortStatistics,
        TorConfig.ExitRelay,
        TorConfig.ExtORPort,
        TorConfig.ExtORPortCookieAuthFile,
        TorConfig.ExtORPortCookieAuthFileGroupReadable,
        TorConfig.ExtendAllowPrivateAddresses,
        TorConfig.ExtendByEd25519ID,
        TorConfig.ExtraInfoStatistics,
        TorConfig.FallbackDir,
        TorConfig.FascistFirewall,
        TorConfig.FetchDirInfoEarly,
        TorConfig.FetchDirInfoExtraEarly,
        TorConfig.FetchHidServDescriptors,
        TorConfig.FetchServerDescriptors,
        TorConfig.FetchUselessDescriptors,
        TorConfig.GuardLifetime,
        TorConfig.GuardfractionFile,
        TorConfig.HSLayer2Nodes,
        TorConfig.HSLayer3Nodes,
        TorConfig.HTTPSProxy,
        TorConfig.HTTPSProxyAuthenticator,
        TorConfig.HTTPTunnelPort,
        TorConfig.HardwareAccel,
        TorConfig.HashedControlPassword,
        TorConfig.HeartbeatPeriod,
        TorConfig.HidServAuth,
        TorConfig.HiddenServiceAllowUnknownPorts,
        TorConfig.HiddenServiceAuthorizeClient,
        TorConfig.HiddenServiceDirGroupReadable,
        TorConfig.HiddenServiceExportCircuitID,
        TorConfig.HiddenServiceMaxStreams,
        TorConfig.HiddenServiceMaxStreamsCloseCircuit,
        TorConfig.HiddenServiceNonAnonymousMode,
        TorConfig.HiddenServiceNumIntroductionPoints,
        TorConfig.HiddenServicePort,
        TorConfig.HiddenServiceSingleHopMode,
        TorConfig.HiddenServiceStatistics,
        TorConfig.HiddenServiceVersion,
        TorConfig.IPv6Exit,
        TorConfig.KISTSchedRunInterval,
        TorConfig.KISTSockBufSizeFactor,
        TorConfig.KeepBindCapabilities,
        TorConfig.KeepalivePeriod,
        TorConfig.KeyDirectory,
        TorConfig.KeyDirectoryGroupReadable,
        TorConfig.LearnCircuitBuildTimeout,
        TorConfig.Log,
        TorConfig.LogMessageDomains,
        TorConfig.LogTimeGranularity,
        TorConfig.LongLivedPorts,
        TorConfig.MainloopStats,
        TorConfig.MapAddress,
        TorConfig.MaxAdvertisedBandwidth,
        TorConfig.MaxCircuitDirtiness,
        TorConfig.MaxClientCircuitsPending,
        TorConfig.MaxConsensusAgeForDiffs,
        TorConfig.MaxMemInQueues,
        TorConfig.MaxOnionQueueDelay,
        TorConfig.MaxUnparseableDescSizeToLog,
        TorConfig.MiddleNodes,
        TorConfig.MinMeasuredBWsForAuthToIgnoreAdvertised,
        TorConfig.MinUptimeHidServDirectoryV2,
        TorConfig.MyFamily,
        TorConfig.NewCircuitPeriod,
        TorConfig.Nickname,
        TorConfig.NoExec,
        TorConfig.NodeFamily,
        TorConfig.NumCPUs,
        TorConfig.NumDirectoryGuards,
        TorConfig.NumEntryGuards,
        TorConfig.NumPrimaryGuards,
        TorConfig.ORPort,
        TorConfig.OfflineMasterKey,
        TorConfig.OptimisticData,
        TorConfig.OutboundBindAddress,
        TorConfig.OutboundBindAddressExit,
        TorConfig.OutboundBindAddressOR,
        TorConfig.PaddingStatistics,
        TorConfig.PathBiasScaleThreshold,
        TorConfig.PathBiasScaleUseThreshold,
        TorConfig.PathsNeededToBuildCircuits,
        TorConfig.PerConnBWBurst,
        TorConfig.PerConnBWRate,
        TorConfig.PidFile,
        TorConfig.ProtocolWarnings,
        TorConfig.PublishHidServDescriptors,
        TorConfig.PublishServerDescriptor,
        TorConfig.ReachableAddresses,
        TorConfig.ReachableORAddresses,
        TorConfig.RecommendedClientVersions,
        TorConfig.RecommendedPackages,
        TorConfig.RecommendedServerVersions,
        TorConfig.RecommendedVersions,
        TorConfig.ReducedExitPolicy,
        TorConfig.RefuseUnknownExits,
        TorConfig.RejectPlaintextPorts,
        TorConfig.RelayBandwidthBurst,
        TorConfig.RelayBandwidthRate,
        TorConfig.RephistTrackTime,
        TorConfig.SSLKeyLifetime,
        TorConfig.SafeLogging,
        TorConfig.SafeSocks,
        TorConfig.Sandbox,
        TorConfig.Schedulers,
        TorConfig.ServerDNSAllowBrokenConfig,
        TorConfig.ServerDNSAllowNonRFC953Hostnames,
        TorConfig.ServerDNSDetectHijacking,
        TorConfig.ServerDNSRandomizeCase,
        TorConfig.ServerDNSResolvConfFile,
        TorConfig.ServerDNSSearchDomains,
        TorConfig.ServerDNSTestAddresses,
        TorConfig.ServerTransportListenAddr,
        TorConfig.ServerTransportOptions,
        TorConfig.ServerTransportPlugin,
        TorConfig.ShutdownWaitLength,
        TorConfig.SigningKeyLifetime,
        TorConfig.Socks4Proxy,
        TorConfig.Socks5Proxy,
        TorConfig.Socks5ProxyPassword,
        TorConfig.SocksPolicy,
        TorConfig.SocksPort,
        TorConfig.SocksTimeout,
        TorConfig.StrictNodes,
        TorConfig.TestSocks,
        TorConfig.TestingAuthDirTimeToLearnReachability,
        TorConfig.TestingAuthKeyLifetime,
        TorConfig.TestingBridgeBootstrapDownloadInitialDelay,
        TorConfig.TestingBridgeDownloadInitialDelay,
        TorConfig.TestingClientConsensusDownloadInitialDelay,
        TorConfig.TestingClientDownloadInitialDelay,
        TorConfig.TestingClientMaxIntervalWithoutRequest,
        TorConfig.TestingDirAuthVoteExit,
        TorConfig.TestingDirAuthVoteExitIsStrict,
        TorConfig.TestingDirAuthVoteGuard,
        TorConfig.TestingDirAuthVoteGuardIsStrict,
        TorConfig.TestingDirAuthVoteHSDir,
        TorConfig.TestingDirAuthVoteHSDirIsStrict,
        TorConfig.TestingDirConnectionMaxStall,
        TorConfig.TestingEnableCellStatsEvent,
        TorConfig.TestingEnableConnBwEvent,
        TorConfig.TestingEstimatedDescriptorPropagationTime,
        TorConfig.TestingLinkCertLifetime,
        TorConfig.TestingMinExitFlagThreshold,
        TorConfig.TestingMinFastFlagThreshold,
        TorConfig.TestingServerConsensusDownloadInitialDelay,
        TorConfig.TestingServerDownloadInitialDelay,
        TorConfig.TestingSigningKeySlop,
        TorConfig.TestingTorNetwork,
        TorConfig.TestingV3AuthInitialDistDelay,
        TorConfig.TestingV3AuthInitialVoteDelay,
        TorConfig.TestingV3AuthInitialVotingInterval,
        TorConfig.TestingV3AuthVotingStartOffset,
        TorConfig.TokenBucketRefillInterval,
        TorConfig.TrackHostExits,
        TorConfig.TrackHostExitsExpire,
        TorConfig.TransPort,
        TorConfig.TransProxyType,
        TorConfig.TruncateLogFile,
        TorConfig.UnixSocksGroupWritable,
        TorConfig.UpdateBridgesFromAuthority,
        TorConfig.UseBridges,
        TorConfig.UseDefaultFallbackDirs,
        TorConfig.UseEntryGuards,
        TorConfig.UseGuardFraction,
        TorConfig.UseMicrodescriptors,
        TorConfig.User,
        TorConfig.V3AuthDistDelay,
        TorConfig.V3AuthNIntervalsValid,
        TorConfig.V3AuthUseLegacyKey,
        TorConfig.V3AuthVoteDelay,
        TorConfig.V3AuthVotingInterval,
        TorConfig.V3AuthoritativeDirectory,
        TorConfig.V3BandwidthsFile,
        TorConfig.VersioningAuthoritativeDirectory,
        TorConfig.WarnPlaintextPorts,
        TorConfig.__AllDirActionsPrivate,
        TorConfig.__DirPort,
        TorConfig.__DisablePredictedCircuits,
        TorConfig.__DisableSignalHandlers,
        TorConfig.__ExtORPort,
        TorConfig.__HashedControlSessionPassword,
        TorConfig.__LeaveStreamsUnattached,
        TorConfig.__ORPort,
        TorConfig.__OwningControllerFD,
        TorConfig.__ReloadTorrcOnSIGHUP,
    ) }
}
