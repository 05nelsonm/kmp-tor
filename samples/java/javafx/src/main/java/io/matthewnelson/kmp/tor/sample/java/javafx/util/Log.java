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
package io.matthewnelson.kmp.tor.sample.java.javafx.util;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class Log {

    private Log() {}

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static void d(String tag, String message) {
        System.out.println(time() + "D/" + tag + ": " + message);
    }

    public static void e(String tag, Throwable cause) {
        System.out.println(time() + "E/" + tag + ": " + Arrays.toString(cause.getStackTrace()));
    }

    public static void w(String tag, String message, Throwable cause) {
        System.out.println(time() + "W/" + tag + ": " + message + cause.toString());
    }

    private static String time() {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN, Locale.US);
        return sdf.format(new Date());
    }
}
