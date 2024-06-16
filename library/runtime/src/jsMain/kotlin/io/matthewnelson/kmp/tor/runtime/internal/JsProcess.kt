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
@file:JsModule("process")
@file:JsNonModule
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.tor.runtime.internal

/** [docs](https://nodejs.org/api/process.html#processkillpid-signal) */
@JsName("kill")
internal external fun process_kill(pid: Number, signal: String)
