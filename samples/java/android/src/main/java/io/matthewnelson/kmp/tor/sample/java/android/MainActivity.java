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

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicLong;

import io.matthewnelson.kmp.tor.ext.callback.common.Task;
import io.matthewnelson.kmp.tor.ext.callback.common.TorCallback;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final AtomicLong count = new AtomicLong(0);

    private Task startTask;
    private Task restartTask;
    private Task restartTask2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        long sCount = count.getAndIncrement();
        startTask = App.torManager.start(
            t -> Log.e(TAG, sCount + " - Failed to start Tor", t),
            startSuccess -> {

                Log.d(TAG, sCount + " - Tor started successfully");

                long r1Count = count.getAndIncrement();
                restartTask = App.torManager.restart(
                    null, // Can ignore failures if desired.

                    (TorCallback<Object>) restartSuccess -> {

                        Log.d(TAG, r1Count + " - Tor restarted successfully");

                        long r2Count = count.getAndIncrement();
                        restartTask2 = App.torManager.restart(
                            new TorCallback<Throwable>() {
                                @Override
                                public void invoke(Throwable throwable) {
                                    Log.e(TAG, r2Count + " - Failed to restart Tor", throwable);
                                }
                            },
                            new TorCallback<Object>() {
                                @Override
                                public void invoke(Object o) {
                                    Log.d(TAG, r2Count + " - Tor restarted successfully");
                                }
                            }
                        );
                    }
                );
            }
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (startTask != null) {
            startTask.cancel();
        }
        if (restartTask != null) {
            restartTask.cancel();
        }
        if (restartTask2 != null) {
            restartTask2.cancel();
        }
    }
}