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
package io.matthewnelson.kmp.tor.sample.java.android;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.matthewnelson.kmp.tor.KmpTorLoaderAndroid;
import io.matthewnelson.kmp.tor.TorConfigProviderAndroid;
import io.matthewnelson.kmp.tor.common.address.OnionAddress;
import io.matthewnelson.kmp.tor.common.address.OnionUrl;
import io.matthewnelson.kmp.tor.common.address.Port;
import io.matthewnelson.kmp.tor.common.address.PortProxy;
import io.matthewnelson.kmp.tor.common.address.Scheme;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*;
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet;
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent;
import io.matthewnelson.kmp.tor.controller.common.file.Path;
import io.matthewnelson.kmp.tor.ext.callback.controller.common.TorCallback;
import io.matthewnelson.kmp.tor.ext.callback.manager.CallbackTorManager;
import io.matthewnelson.kmp.tor.manager.TorManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;

public class App extends Application {

    static CallbackTorManager torManager;

    @Override
    public void onCreate() {
        super.onCreate();

        TorConfigProviderAndroid provider = new TorConfigProviderAndroid(this) {

            @NonNull
            @Override
            protected TorConfig provide() {
                Ports.Socks socks = new Ports.Socks();
                socks.set(AorDorPort.Value.invoke(PortProxy.invoke(9055)));

                HiddenService myHiddenService = new HiddenService();

                myHiddenService.set(FileSystemDir.invoke(
                    getWorkDir()
                        .builder(Path.getFsSeparator())
                        .addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
                        .addSegment("my_hidden_service")
                        .build()
                ));

                Set<HiddenService.Ports> hsPorts = new HashSet<>(2);
                HiddenService.Ports virtPort80 = new HiddenService.Ports(Port.invoke(80), Port.invoke(8080));
                hsPorts.add(virtPort80);
                hsPorts.add(virtPort80.copy(Port.invoke(443), virtPort80.targetPort));

                myHiddenService.setPorts(hsPorts);

                return new TorConfig.Builder()
                    .put(socks)
                    .put(myHiddenService)
                    .build();
            }
        };

        KmpTorLoaderAndroid loader = new KmpTorLoaderAndroid(provider);

        new TorControlInfoGet.KeyWord.Accounting.Enabled();

        // Instantiate new TorManager
        TorManager manager = TorManager.newInstance(this, loader);

        // Wrap it in callback implementation
        torManager = new CallbackTorManager(manager, uncaughtException -> {
            Log.e("App", "Some RequestCallback isn't handling an exception...", uncaughtException);
        });

        torManager.debug(true);
        torManager.addListener(new TorListener());
    }

    private static class TorListener extends TorManagerEvent.Listener {

        @Override
        public void onEvent(@NonNull TorEvent.Type.SingleLineEvent singleLineEvent, @NonNull String s) {
            Log.d("TorListener", singleLineEvent + " - " + s);
            super.onEvent(singleLineEvent, s);
        }

        @Override
        public void onEvent(@NonNull TorManagerEvent torManagerEvent) {
            Log.d("TorListener", torManagerEvent.toString());
            super.onEvent(torManagerEvent);
        }

        @Override
        public void managerEventStartUpCompleteForTorInstance() {
            // Do stuff once bootstrapped

            Set<HiddenService.Ports> hsPorts = new HashSet<>(2);
            hsPorts.add(new HiddenService.Ports(Port.invoke(80), Port.invoke(8080))); // http
            hsPorts.add(new HiddenService.Ports(Port.invoke(443), Port.invoke(8080))); // https

            torManager.onionAddNew(
                OnionAddress.PrivateKey.Type.ED25519_V3.INSTANCE,
                hsPorts,
                null,
                null,
                TorCallback.THROW, // pipe error to uncaught exception handler
                hsEntry -> {
                    Log.d(
                        "TorListener",
                        "New HiddenService: \n - Address: " +
                        new OnionUrl(hsEntry.address, "", null, Scheme.HTTPS) +
                        "\n - PrivateKey: " + hsEntry.privateKey
                    );

                    torManager.onionDel(hsEntry.address, TorCallback.THROW, s -> {
                        Log.d("TorListener", "Aaaaaaaaand it's gone...");
                    });
                }
            );
        }
    }
}
