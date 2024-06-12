# runtime-service

### Android

By default, the `TorService` will run in the background and no configuration is 
necessary. However, if you would like to run it as a Foreground Service, some 
configuration is needed. 

Configuration of the Foreground Service is done using `<meta-data>` tags in your 
`AndroidManifest.xml` file within its `<application>` block. Those `<meta-data>` 
tags point to resources that you then define in `attrs.xml`.

Simply copy/paste from the below examples and modify to your needs.

<details>
    <summary>res/values/attrs.xml</summary>

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
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
-->
<resources>
    <!--
    Required if Foreground
    Default: false

    If set to `true`, TorService will run in the foreground. Upon setting to
    `true`, the bellow attributes that are denoted as `Required` must be set.

    Must add to AndroidManifest.xml the following permission for API 28+:
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    Note that setting this to `true` means that if your application is swiped from
    the recent app's tray and then Tor is stopped (either by user action, or if
    `tor_service_stop_service_on_task_removed` is unset or `true`), Tor takes a
    moment to shut down. As such, there is approximately 1 second where your
    application's State is still started (as if the app was not swiped from the
    recent app's tray). See attribute
    `tor_service_if_foreground_exit_process_on_destroy_when_task_removed` below
    for more information.
    -->
    <bool name="tor_service_enable_foreground">true</bool>

    <!--
    Not Required
    Default: true

    If set to `false`, when the user swipes your application from the recent app's
    tray, stop service will not be called.

    This can be useful if:
      - You are running TorService in the background, with another
        service that is running in the Foreground keeping the application alive
        past Task removal.
      - You are running TorService in the foreground and wish to keep the application
        alive until an explicit call to `stop` TorService is made.
    -->
    <bool name="tor_service_stop_service_on_task_removed">true</bool>

    <!--
    Not Required
    Default: whatever `tor_service_enable_foreground` is set to

    If `tor_service_enable_foreground` is set to `true`, no matter what value is
    set for `tor_service_stop_service_on_task_removed` your application will _not_
    be killed when TorService.onDestroy is called when the Task has been removed.
    This is attributed to how Foreground Services work in Android, not kmp-tor.

    When TorService.onDestroy is called, all instances of TorRuntime are also destroyed
    (it takes approximately 1s to shut down cleanly). Upon TorRuntime destruction completion,
    TorService will check to see if the Task is still removed (user has not returned
    to the application). If it is still removed and this setting is `true`, exitProcess(0)
    will be called to kill the application. This would be the same behavior as if
    TorService was running as a background service and the user swipes the application
    from recent app's tray.

    If this setting is `false`, exitProcess will _not_ be called after TorRuntime destruction
    completes. Your application will continue to run in the background until either:
      - The user returns to the application
          - Note that TorService will automatically re-start if the last action was _not_
            Action.StopDaemon.
      - Android kills the process to recoup memory (approximately 1m)
    -->
    <bool name="tor_service_if_foreground_exit_process_on_destroy_when_task_removed">true</bool>


    <!-- NOTIFICATION INFO -->

    <!--
    Required if Foreground
    Value must be between 1 and 9999
    -->
    <integer name="tor_service_notification_id">21</integer>
    <!--
    Required if Foreground
    Value must not be empty
    -->
    <string name="tor_service_notification_channel_id">TorService Channel Id</string>
    <!--
    Required if Foreground
    Value must not be empty
    -->
    <string name="tor_service_notification_channel_name">TorService Channel Name</string>
    <!--
    Required if Foreground
    Value must not be empty
    -->
    <string name="tor_service_notification_channel_description">TorService Channel Description</string>
    <!--
    Not Required
    Default: false
    -->
    <bool name="tor_service_notification_channel_show_badge">false</bool>


    <!-- NOTIFICATION ICONS -->

    <!--
    Required if Foreground
    -->
    <drawable name="tor_service_notification_icon_network_enabled">@drawable/replace_me_network_enabled</drawable>
    <!--
    Not Required
    Default: whatever is set to `tor_service_notification_icon_network_enabled`
    -->
    <drawable name="tor_service_notification_icon_network_disabled">@drawable/replace_me_network_disabled</drawable>
    <!--
    Not Required
    Default: whatever is set to `tor_service_notification_icon_network_enabled`
    -->
    <drawable name="tor_service_notification_icon_data_xfer">@drawable/replace_me_network_dataxfer</drawable>
    <!--
    Required if Foreground
    -->
    <drawable name="tor_service_notification_icon_error">@drawable/replace_me_notifyerr</drawable>


    <!-- NOTIFICATION COLORS -->

    <!--
    Not Required
    Default: android.R.color.white
    -->
    <color name="tor_service_notification_color_when_bootstrapped_true">#FFBB86FC</color>
    <!--
    Not Required
    Default: android.R.color.white
    -->
    <color name="tor_service_notification_color_when_bootstrapped_false">#FFFFFFFF</color>


    <!-- NOTIFICATION VISIBILITY -->

    <!--
    Not Required
    Options: @null, public, private, secret
    Default: private
    -->
    <string name="tor_service_notification_visibility">private</string>


    <!-- NOTIFICATION ACTIONS (BUTTONS) -->

    <!--
    Not Required
    Default: false
    -->
    <bool name="tor_service_notification_action_enable_restart">false</bool>
    <!--
    Not Required
    Default: false
    -->
    <bool name="tor_service_notification_action_enable_stop">false</bool>
</resources>
```

</details>

<details>
    <summary>AndroidManifest.xml</summary>

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
*  Copyright 2021 Matthew Nelson
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
* */
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!--
    Required for API 28+ if enabling foreground service
    -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!--
    Required for usage of NetworkObserver to automatically toggle Tor's
    `DisableNetwork` config setting upon device connectivity loss/gain.
    -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <!--
    Required for connecting to the Tor network.
    -->
    <uses-permission android:name="android.permission.INTERNET" />
    <!--
    Required when targeting API 33+ and are using foreground service.
    -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application android:name=".APPLICATION BLOCK">

        <!--
        TorService configuration to run as a Foreground Service.

        ** See also the above permissions needed **
        -->
        <meta-data
            android:name="io.matthewnelson.kmp.tor.enable_foreground"
            android:value="@bool/tor_service_enable_foreground" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.stop_service_on_task_removed"
            android:value="@bool/tor_service_stop_service_on_task_removed" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.if_foreground_exit_process_on_destroy_when_task_removed"
            android:value="@bool/tor_service_if_foreground_exit_process_on_destroy_when_task_removed" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_id"
            android:value="@integer/tor_service_notification_id" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_channel_id"
            android:value="@string/tor_service_notification_channel_id" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_channel_name"
            android:value="@string/tor_service_notification_channel_name" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_channel_description"
            android:value="@string/tor_service_notification_channel_description" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_channel_show_badge"
            android:value="@bool/tor_service_notification_channel_show_badge" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_icon_network_enabled"
            android:resource="@drawable/tor_service_notification_icon_network_enabled" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_icon_network_disabled"
            android:resource="@drawable/tor_service_notification_icon_network_disabled" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_icon_data_xfer"
            android:resource="@drawable/tor_service_notification_icon_data_xfer" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_icon_error"
            android:resource="@drawable/tor_service_notification_icon_error" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_color_when_bootstrapped_true"
            android:resource="@color/tor_service_notification_color_when_bootstrapped_true" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_color_when_bootstrapped_false"
            android:resource="@color/tor_service_notification_color_when_bootstrapped_false" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_visibility"
            android:value="@string/tor_service_notification_visibility" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_action_enable_restart"
            android:value="@bool/tor_service_notification_action_enable_restart" />
        <meta-data
            android:name="io.matthewnelson.kmp.tor.notification_action_enable_stop"
            android:value="@bool/tor_service_notification_action_enable_stop" />
    </application>

</manifest>
```

</details>
