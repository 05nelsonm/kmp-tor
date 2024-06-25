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

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey
import kotlinx.coroutines.*
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

object TestUtils {

    private val TMP_TEST_DIR = SysTempDir.resolve("kmp_tor_test")

    private val INSTALLER by lazy {
        TorResources(TMP_TEST_DIR.resolve("resources"))
    }

    fun testEnv(
        dirName: String,
        installer: (File) -> ResourceInstaller<Paths.Tor> = { INSTALLER },
        block: ThisBlock<TorRuntime.Environment.Builder> = ThisBlock {}
    ): TorRuntime.Environment = TorRuntime.Environment.Builder(
        workDirectory = TMP_TEST_DIR.resolve("$dirName/work"),
        cacheDirectory = TMP_TEST_DIR.resolve("$dirName/cache"),
        installer = installer,
    ) {
        defaultEventExecutor = OnEvent.Executor.Immediate

        apply(block)
    }.also { it.debug = true }

    suspend fun <T: Action.Processor> T.ensureStoppedOnTestCompletion(
        errorObserver: Boolean = true,
    ): T {
        currentCoroutineContext().job.invokeOnCompletion {
            enqueue(Action.StopDaemon, {}, {})
        }

        if (errorObserver && this is TorRuntime) {
            val tag = environment().staticTag()
            subscribe(RuntimeEvent.ERROR.observer(tag, OnEvent.Executor.Immediate) { t ->
                if (t is UncaughtException) throw t
                t.printStackTrace()
            })
        }

        withContext(Dispatchers.Default) { delay(100.milliseconds) }

        return this
    }

    fun clientAuthTestKeyPairs(): Set<Pair<X25519.PublicKey, X25519.PrivateKey>> {
        return CLIENT_AUTH_TEST_KEYS.mapTo(LinkedHashSet(3, 1.0f)) { (pub, prv) ->
            pub.toX25519PublicKey() to prv.toX25519PrivateKey()
        }.toImmutableSet()
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

    private val CLIENT_AUTH_TEST_KEYS by lazy {
        val pub1 = byteArrayOf(
            25, -126, -57, 114, -127, 21, 26, -78,
            53, -84, 111, -119, -95, 97, 30, 56,
            89, 70, 0, 1, -101, 83, 62, 82,
            55, 100, 84, 57, -24, -28, -1, 109,
        )
        val prv1 = byteArrayOf(
            -88, -76, 0, -64, 103, 3, 48, -24,
            -96, -91, -20, 105, -2, -36, -60, 111,
            99, 76, -87, 85, 58, -54, 69, 86,
            -96, 107, -53, -93, 106, -11, 70, 87,
        )

        val pub2 = byteArrayOf(
            100, 59, 2, 66, -103, -67, 5, 73,
            19, 106, 69, -106, -13, 49, -86, -17,
            -113, 51, -4, 74, -65, 77, -122, -90,
            80, -125, 45, 81, -53, 61, -54, 66,
        )
        val prv2 = byteArrayOf(
            72, 127, 34, -97, -98, 84, -74, -106,
            63, -78, -47, -19, 63, -34, -13, -14,
            18, -77, 88, 127, 5, 85, -59, 100,
            116, 124, -128, -125, -102, -103, -128, 65,
        )

        val pub3 = byteArrayOf(
            67, -57, -106, 104, 23, 112, 0, -77,
            50, 91, 69, 28, 17, -107, -73, -80,
            -112, 67, -11, -118, -74, 18, 57, 108,
            -52, 83, 126, 10, 2, 70, -73, 91,
        )
        val prv3 = byteArrayOf(
            -80, 25, 97, 20, 3, -70, -84, -19,
            88, -124, 48, 100, 19, -83, 116, 94,
            -20, -108, -24, 124, -113, -4, -99, -25,
            53, 71, -68, -10, -86, 51, -55, 99,
        )

        immutableSetOf(
            pub1 to prv1,
            pub2 to prv2,
            pub3 to prv3,
        )
    }
    val KEYWORDS by lazy { setOf(
        TorConfig.AutomapHostsOnResolve,
        TorConfig.AutomapHostsSuffixes,
        TorConfig.CacheDirectory,
        TorConfig.ClientOnionAuthDir,
        TorConfig.ConnectionPadding,
        TorConfig.ControlPortWriteToFile,
        TorConfig.CookieAuthFile,
        TorConfig.CookieAuthentication,
        TorConfig.DataDirectory,
        TorConfig.DisableNetwork,
        TorConfig.DormantCanceledByStartup,
        TorConfig.DormantClientTimeout,
        TorConfig.DormantOnFirstStartup,
        TorConfig.DormantTimeoutDisabledByIdleStreams,
        TorConfig.GeoIPExcludeUnknown,
        TorConfig.GeoIPFile,
        TorConfig.GeoIPv6File,
        TorConfig.HiddenServiceDir,
        TorConfig.ReducedConnectionPadding,
        TorConfig.RunAsDaemon,
        TorConfig.SyslogIdentityTag,
        TorConfig.VirtualAddrNetworkIPv4,
        TorConfig.VirtualAddrNetworkIPv6,
        TorConfig.__ControlPort,
        TorConfig.__DNSPort,
        TorConfig.__HTTPTunnelPort,
        TorConfig.__OwningControllerProcess,
        TorConfig.__SocksPort,
        TorConfig.__TransPort,
        TorConfig.AccelDir,
        TorConfig.AccelName,
        TorConfig.AccountingMax,
        TorConfig.AccountingRule,
        TorConfig.AccountingStart,
        TorConfig.Address,
        TorConfig.AddressDisableIPv6,
        TorConfig.AllowNonRFC953Hostnames,
        TorConfig.AlternateBridgeAuthority,
        TorConfig.AlternateDirAuthority,
        TorConfig.AndroidIdentityTag,
        TorConfig.AssumeReachable,
        TorConfig.AssumeReachableIPv6,
        TorConfig.AuthDirBadExit,
        TorConfig.AuthDirBadExitCCs,
        TorConfig.AuthDirFastGuarantee,
        TorConfig.AuthDirGuardBWGuarantee,
        TorConfig.AuthDirHasIPv6Connectivity,
        TorConfig.AuthDirInvalid,
        TorConfig.AuthDirInvalidCCs,
        TorConfig.AuthDirListBadExits,
        TorConfig.AuthDirListMiddleOnly,
        TorConfig.AuthDirMaxServersPerAddr,
        TorConfig.AuthDirMiddleOnly,
        TorConfig.AuthDirMiddleOnlyCCs,
        TorConfig.AuthDirPinKeys,
        TorConfig.AuthDirReject,
        TorConfig.AuthDirRejectCCs,
        TorConfig.AuthDirRejectRequestsUnderLoad,
        TorConfig.AuthDirSharedRandomness,
        TorConfig.AuthDirTestEd25519LinkKeys,
        TorConfig.AuthDirTestReachability,
        TorConfig.AuthDirVoteGuard,
        TorConfig.AuthDirVoteGuardBwThresholdFraction,
        TorConfig.AuthDirVoteGuardGuaranteeTimeKnown,
        TorConfig.AuthDirVoteGuardGuaranteeWFU,
        TorConfig.AuthDirVoteStableGuaranteeMTBF,
        TorConfig.AuthDirVoteStableGuaranteeMinUptime,
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
        TorConfig.CircuitPadding,
        TorConfig.CircuitPriorityHalflife,
        TorConfig.CircuitStreamTimeout,
        TorConfig.CircuitsAvailableTimeout,
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
        TorConfig.CompiledProofOfWorkHash,
        TorConfig.ConfluxClientUX,
        TorConfig.ConfluxEnabled,
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
        TorConfig.DoSConnectionConnectBurst,
        TorConfig.DoSConnectionConnectDefenseTimePeriod,
        TorConfig.DoSConnectionConnectRate,
        TorConfig.DoSConnectionDefenseType,
        TorConfig.DoSConnectionEnabled,
        TorConfig.DoSConnectionMaxConcurrentCount,
        TorConfig.DoSRefuseSingleHopClientRendezvous,
        TorConfig.DormantTimeoutEnabled,
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
        TorConfig.HiddenServiceAllowUnknownPorts,
        TorConfig.HiddenServiceDirGroupReadable,
        TorConfig.HiddenServiceEnableIntroDoSBurstPerSec,
        TorConfig.HiddenServiceEnableIntroDoSDefense,
        TorConfig.HiddenServiceEnableIntroDoSRatePerSec,
        TorConfig.HiddenServiceExportCircuitID,
        TorConfig.HiddenServiceMaxStreams,
        TorConfig.HiddenServiceMaxStreamsCloseCircuit,
        TorConfig.HiddenServiceNonAnonymousMode,
        TorConfig.HiddenServiceNumIntroductionPoints,
        TorConfig.HiddenServiceOnionBalanceInstance,
        TorConfig.HiddenServicePoWDefensesEnabled,
        TorConfig.HiddenServicePoWQueueBurst,
        TorConfig.HiddenServicePoWQueueRate,
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
        TorConfig.MetricsPort,
        TorConfig.MetricsPortPolicy,
        TorConfig.MiddleNodes,
        TorConfig.MinMeasuredBWsForAuthToIgnoreAdvertised,
        TorConfig.MinUptimeHidServDirectoryV2,
        TorConfig.MyFamily,
        TorConfig.NATDPort,
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
        TorConfig.OutboundBindAddress,
        TorConfig.OutboundBindAddressExit,
        TorConfig.OutboundBindAddressOR,
        TorConfig.OutboundBindAddressPT,
        TorConfig.OverloadStatistics,
        TorConfig.PaddingStatistics,
        TorConfig.PathBiasCircThreshold,
        TorConfig.PathBiasDropGuards,
        TorConfig.PathBiasExtremeRate,
        TorConfig.PathBiasExtremeUseRate,
        TorConfig.PathBiasNoticeRate,
        TorConfig.PathBiasNoticeUseRate,
        TorConfig.PathBiasScaleThreshold,
        TorConfig.PathBiasScaleUseThreshold,
        TorConfig.PathBiasUseThreshold,
        TorConfig.PathBiasWarnRate,
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
        TorConfig.RecommendedServerVersions,
        TorConfig.RecommendedVersions,
        TorConfig.ReducedCircuitPadding,
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
        TorConfig.Socks5ProxyUsername,
        TorConfig.SocksPolicy,
        TorConfig.SocksPort,
        TorConfig.SocksTimeout,
        TorConfig.StrictNodes,
        TorConfig.TCPProxy,
        TorConfig.TestSocks,
        TorConfig.TestingAuthDirTimeToLearnReachability,
        TorConfig.TestingAuthKeyLifetime,
        TorConfig.TestingAuthKeySlop,
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
        TorConfig.TestingLinkCertLifetime,
        TorConfig.TestingLinkKeySlop,
        TorConfig.TestingMinExitFlagThreshold,
        TorConfig.TestingMinFastFlagThreshold,
        TorConfig.TestingMinTimeToReportBandwidth,
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
        TorConfig.VanguardsLiteEnabled,
        TorConfig.VersioningAuthoritativeDirectory,
        TorConfig.WarnPlaintextPorts,
        TorConfig.__AllDirActionsPrivate,
        TorConfig.__AlwaysCongestionControl,
        TorConfig.__DirPort,
        TorConfig.__DisablePredictedCircuits,
        TorConfig.__DisableSignalHandlers,
        TorConfig.__ExtORPort,
        TorConfig.__HashedControlSessionPassword,
        TorConfig.__LeaveStreamsUnattached,
        TorConfig.__MetricsPort,
        TorConfig.__NATDPort,
        TorConfig.__ORPort,
        TorConfig.__OwningControllerFD,
        TorConfig.__ReloadTorrcOnSIGHUP,
        TorConfig.__SbwsExit,
    ) }
}
