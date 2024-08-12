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
package io.matthewnelson.kmp.tor.runtime.service.internal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Handler
import android.os.Process

@Suppress("NOTHING_TO_INLINE")
internal inline fun Context.isPermissionGranted(permission: String): Boolean {
    val result = checkPermission(permission, Process.myPid(), Process.myUid())
    return result == PERMISSION_GRANTED
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Context.register(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
    permission: String?,
    scheduler: Handler?,
    exported: Boolean?,
    flags: Int = 0,
): Intent? {
    var f = flags

    if (exported != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // API 33+
        f = f or if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        // API 25-
        return registerReceiver(receiver, filter, permission, scheduler)
    }

    // API 26+
    return registerReceiver(receiver, filter, permission, scheduler, f)
}
