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
package io.matthewnelson.kmp.tor.manager

interface Destroyable {

    val isDestroyed: Boolean

    /**
     * Destroys the instance rendering it unusable. In [TorManager]'s case,
     * it stops Tor, removes all listeners, cancels it's scope, shuts down
     * threads, etc.
     *
     * Tf [stopCleanly] is true, [RealTorManager.destroy] launches a coroutine
     * in order to stop Tor via it's control port so it can clean up properly.
     * This takes approximately 500ms (if Tor is running).
     *
     * By passing a lambda via [onCompletion] you can perform other necessary
     * post destruction tasks and cleanup after Tor has been shutdown (or killed
     * if [stopCleanly] is set to false).
     * */
    fun destroy(stopCleanly: Boolean = true, onCompletion: (() -> Unit)? = null)
}
