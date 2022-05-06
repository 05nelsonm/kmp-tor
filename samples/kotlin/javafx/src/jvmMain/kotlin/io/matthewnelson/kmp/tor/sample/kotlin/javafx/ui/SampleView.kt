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
package io.matthewnelson.kmp.tor.sample.kotlin.javafx.ui

import io.matthewnelson.kmp.tor.sample.kotlin.javafx.util.Log
import tornadofx.*

class SampleView: View() {

    override val root = hbox {
        label("See Logs")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(this.javaClass.simpleName, "onCreate")
    }

    override fun onDock() {
        super.onDock()
        Log.d(this.javaClass.simpleName, "onDock")
    }

    override fun onUndock() {
        super.onUndock()
        Log.d(this.javaClass.simpleName, "onUndock")
    }

    override fun onBeforeShow() {
        super.onBeforeShow()
        Log.d(this.javaClass.simpleName, "onBeforeShow")
    }

    override fun onRefresh() {
        super.onRefresh()
        Log.d(this.javaClass.simpleName, "onRefresh")
    }

    override fun onSave() {
        super.onSave()
        Log.d(this.javaClass.simpleName, "onSave")
    }

    override fun onDelete() {
        super.onDelete()
        Log.d(this.javaClass.simpleName, "onDelete")
    }
}
