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
package io.matthewnelson.kmp.tor.sample.java.javafx;

import io.matthewnelson.kmp.tor.binary.extract.TorBinaryResource;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm;
import io.matthewnelson.kmp.tor.PlatformInstaller;
import io.matthewnelson.kmp.tor.PlatformInstaller.InstallOption;
import io.matthewnelson.kmp.tor.TorConfigProviderJvm;
import io.matthewnelson.kmp.tor.common.address.OnionAddress;
import io.matthewnelson.kmp.tor.common.address.Port;
import io.matthewnelson.kmp.tor.common.address.PortProxy;
import io.matthewnelson.kmp.tor.common.address.ProxyAddress;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*;
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet;
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent;
import io.matthewnelson.kmp.tor.controller.common.file.Path;
import io.matthewnelson.kmp.tor.ext.callback.common.TorCallback;
import io.matthewnelson.kmp.tor.ext.callback.manager.CallbackTorManager;
import io.matthewnelson.kmp.tor.ext.callback.manager.common.CallbackTorControlManager;
import io.matthewnelson.kmp.tor.ext.callback.manager.common.CallbackTorOperationManager;
import io.matthewnelson.kmp.tor.manager.TorManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;
import io.matthewnelson.kmp.tor.sample.java.javafx.util.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    private CallbackTorManager torManager;

    // Only expose necessary interfaces
    public CallbackTorOperationManager torOperationManager() {
        return torManager;
    }
    public CallbackTorControlManager torControlManager() {
        return torManager;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Label l = new Label("See Logs");
        Scene scene = new Scene(new StackPane(l), 640, 480);
        primaryStage.setScene(scene);

        torManager = setupTorManager();

        setupOnCloseIntercept(primaryStage);

        torManager.debug(true);
        torManager.addListener(new TorListener(torControlManager()));
        torManager.startQuietly();

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        torManager.destroy(/* stopCleanly = */ false, null);
        super.stop();
    }

    /**
     * Must call [CallbackTorManager.destroy] to stop Tor and clean up so that the
     * Application does not hang on exit.
     *
     * See [stop] also.
     * */
    private void setupOnCloseIntercept(Stage stage) {
        stage.setOnCloseRequest( event -> {

            // `destroy` launches a coroutine using TorManager's scope in order
            // to stop Tor cleanly via it's control port. This takes ~500ms if Tor
            // is running.
            //
            // Upon destruction completion, Platform.exit() will be invoked.
            torManager.destroy(/* stopCleanly = */ true, whenComplete -> {
                // Exit when complete
                Platform.exit();
            });
            event.consume();
        });
    }

    private CallbackTorManager setupTorManager() throws RuntimeException {
        PlatformInstaller installer = platformInstaller();
        TorConfigProviderJvm providerJvm = configProviderJvm();
        KmpTorLoaderJvm loaderJvm = new KmpTorLoaderJvm(installer, providerJvm);

        // Instantiate new TorManager
        TorManager instance = TorManager.newInstance(loaderJvm);

        // Wrap it in callback implementation
        return new CallbackTorManager(instance, uncaughtException -> {
            Log.e("TorUncaughtExceptionHandler", uncaughtException);
        });
    }

    private PlatformInstaller platformInstaller() throws RuntimeException {
        String osName = System.getProperty("os.name");

        if (osName == null) {
            throw new RuntimeException("os.name was null wtf?");
        } else if (osName.contains("Windows")) {
            return PlatformInstaller.mingwX64(InstallOption.CleanInstallIfMissing);
        } else if (osName.equals("Mac OS X")) {
            return PlatformInstaller.macosX64(InstallOption.CleanInstallIfMissing);
        } else if (osName.contains("Mac") || osName.contains("Darwin")) {
            return PlatformInstaller.macosArm64(InstallOption.CleanInstallIfMissing);
        } else if (osName.contains("Linux")) {

            // Example of providing your own packaged binary resources in the event a
            // platform or architecture is not currently supported by kmp-tor-binary.
            //
            // Note that there IS a linux x64 binary dependency provided by
            // kmp-tor-binary and that should be used instead; this is just an example.
            //
            // Files are located in this sample's resources/kmptor/linux/x64 directory
            return PlatformInstaller.custom(
                InstallOption.CleanInstallIfMissing,
                TorBinaryResource.from(
                    /* os */ TorBinaryResource.OS.Linux,
                    /* arch */"x64",
                    /* sha256sum */ "7ea1e0a19f63d2542b34e1cfe8f8135b278a0eea5a7fd8d25e78e12972834ae2",
                    /* resourceManifest */ List.of(
                        "libcrypto.so.1.1.gz",
                        "libevent-2.1.so.7.gz",
                        "libssl.so.1.1.gz",
                        "libstdc++.so.6.gz",
                        "tor.gz"
                    )
                )
            );
        } else {
            throw new RuntimeException("Could not identify OS from 'os.name-" + osName);
        }
    }

    private TorConfigProviderJvm configProviderJvm() throws RuntimeException {
        // Note that for this example the temp directory is utilized. Keep in mind
        // that all processes and users have access to the temporary directory and
        // its use should be avoided in production.
        String tempDir = System.getProperty("java.io.tmpdir");

        if (tempDir == null) {
            throw new RuntimeException("Could not identify OS's temporary directory");
        }

        Path tempDirPath = Path.invoke(tempDir)
                .builder()
                .addSegment("kmptor-j")
                .build();

        return new TorConfigProviderJvm() {
            final Path workDir = tempDirPath.builder()
                    .addSegment("work")
                    .build();

            final Path cacheDir = tempDirPath.builder()
                    .addSegment("cache")
                    .build();

            @NotNull
            @Override
            public Path getWorkDir() {
                return workDir;
            }

            @NotNull
            @Override
            public Path getCacheDir() {
                return cacheDir;
            }

            @NotNull
            @Override
            protected TorConfig provide() {
                Ports.Socks socks = new Ports.Socks();

                socks.set(AorDorPort.Value.invoke(PortProxy.invoke(9055)));

                Set<Ports.IsolationFlag> socksIsoFlags = new HashSet<>(2);
                socksIsoFlags.add(Ports.IsolationFlag.IsolateDestAddr.INSTANCE);
                socksIsoFlags.add(new Ports.IsolationFlag.SessionGroup(5));
                socks.setIsolationFlags(socksIsoFlags);

                Set<Ports.Socks.Flag> socksFlags = new HashSet<>(1);
                socksFlags.add(Ports.Socks.Flag.OnionTrafficOnly.INSTANCE);
                socks.setFlags(socksFlags);

                Ports.HttpTunnel http = new Ports.HttpTunnel();
                http.set(AorDorPort.Auto.INSTANCE);

                // Use a UnixSocket instead of TCP for the ControlPort.
                //
                // This is just for demonstration purposes because it is not
                // needed as if neither `Ports.Control` or `UnixSockets.Control`
                // are provided here, if there is support for `UnixSockets.Control`,
                // it will be the preferred way for establishing a Tor control
                // connection and automatically added for you.
                UnixSockets.Control control = new UnixSockets.Control();
                control.set(FileSystemFile.invoke(
                    workDir.builder()

                        // Put the file in the "data" directory
                        // so that we avoid any directory permission
                        // issues.
                        //
                        // Note that DataDirectory is automatically added
                        // for you if it is not present in your provided
                        // config. If you set a custom Path for it, you
                        // should use it here.
                        .addSegment(DataDirectory.DEFAULT_NAME)

                        .addSegment(UnixSockets.Control.DEFAULT_NAME)
                        .build()
                ));

                HiddenService myHiddenService = new HiddenService();
                Path hsDirPath = workDir.builder()
                    .addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                    .addSegment("my_hidden_service")
                    .build();

                myHiddenService.set(FileSystemDir.invoke(hsDirPath));

                Set<HiddenService.VirtualPort> hsPorts = new HashSet<>(3);
                HiddenService.Ports httpPort = new HiddenService.Ports(Port.invoke(80), Port.invoke(8080));
                HiddenService.Ports httpsPort = httpPort.copy(Port.invoke(443), httpPort.targetPort);
                HiddenService.UnixSocket httpUnixSocket = new HiddenService.UnixSocket(
                    Port.invoke(80),
                    hsDirPath.builder()
                        .addSegment(HiddenService.UnixSocket.DEFAULT_UNIX_SOCKET_NAME)
                        .build()
                );

                // May show an error in IDE, but it will compile b/c
                // `HiddenService.Ports` and `HiddenService.UnixSocket`
                // both extend `HiddenService.VirtualPort`
                hsPorts.add(httpPort);
                hsPorts.add(httpsPort);
                hsPorts.add(httpUnixSocket);

                myHiddenService.setPorts(hsPorts);

                return new TorConfig.Builder()
                    .put(socks)
                    .put(http)
                    .put(control)
                    .put(myHiddenService)
                    .build();
            }
        };
    }

    /*
     * Note that AndroidStudio shows override errors because the TorManagerEvent.Listener
     * extends TorEvent.Listener which is not picked up for some reason when kotlin
     * compiles it to Java. Everything still works, it is just an inconvenience.
     *
     * See https://github.com/05nelsonm/kmp-tor/issues/123
     * */
    private static class TorListener extends TorManagerEvent.Listener {

        private final CallbackTorControlManager torControlManager;

        public TorListener(CallbackTorControlManager torControlManager) {
            this.torControlManager = torControlManager;
        }

        @Override
        public void onEvent(TorEvent.Type.SingleLineEvent singleLineEvent, String s) {
            Log.d("TorListener", singleLineEvent + " - " + s);
            super.onEvent(singleLineEvent, s);
        }

        @Override
        public void onEvent(TorManagerEvent torManagerEvent) {
            Log.d("TorListener", torManagerEvent.toString());
            super.onEvent(torManagerEvent);
        }

        @Override
        public void managerEventError(Throwable t) {
            t.printStackTrace();
        }

        @Override
        public void managerEventAddressInfo(TorManagerEvent.AddressInfo info) {
            if (info.isNull) {
                // Tear down HttpClient
            } else {
                try {
                    ProxyAddress socks = info.socksInfoToProxyAddress().iterator().next();
                    InetSocketAddress socketAddress = new InetSocketAddress(
                        socks.address.getValue(),
                        socks.port.getValue()
                    );

                    // Build HttpClient
                } catch (Exception e) {
                    Log.e("TorListener", e);
                }
            }
        }

        @Override
        public void managerEventStartUpCompleteForTorInstance() {
            // Do one-time things after we're bootstrapped

            Set<HiddenService.Ports> hsPorts = new HashSet<>(2);
            hsPorts.add(new HiddenService.Ports(Port.invoke(80), Port.invoke(8080))); // http
            hsPorts.add(new HiddenService.Ports(Port.invoke(443), Port.invoke(8080))); // https

            torControlManager.onionAddNew(
                OnionAddress.PrivateKey.Type.ED25519_V3.INSTANCE,
                hsPorts,
                null,
                null,
                TorCallback.THROW, // pipe error to uncaught exception handler
                hsEntry -> {
                    Log.d(
                        "TorListener",
                        "New HiddenService: \n - Address: " +
                        "https://" + hsEntry.address.canonicalHostname() +
                        "\n - PrivateKey: " + hsEntry.privateKey
                    );

                    torControlManager.onionDel(hsEntry.address, TorCallback.THROW, s -> {
                        Log.d("TorListener", "Aaaaaaaaand it's gone...");

                        torControlManager.infoGet(
                            new TorControlInfoGet.KeyWord.Uptime(),
                            TorCallback.THROW,
                            uptime -> {
                                Log.d("TorListener", "Uptime - " + uptime);
                            }
                        );
                    });
                }
            );
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
