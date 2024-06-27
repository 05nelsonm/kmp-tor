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
package io.matthewnelson.kmp.tor.runtime.service

public class NotificationInfo private constructor(
    @JvmField
    public val channelID: String,
    @JvmField
    public val channelName: String,
    @JvmField
    public val channelDescription: String,
    @JvmField
    public val channelShowBadge: Boolean,
    @JvmField
    public val notificationID: Int,
) {

    public companion object {

        @JvmStatic
        public fun of(
            channelID: String,
            channelName: String,
            channelDescription: String,
            channelShowBadge: Boolean,
            notificationID: Int,
        ): NotificationInfo {
            // TODO: Validate

            return NotificationInfo(
                channelID,
                channelName,
                channelDescription,
                channelShowBadge,
                notificationID,
            )
        }
    }
}
