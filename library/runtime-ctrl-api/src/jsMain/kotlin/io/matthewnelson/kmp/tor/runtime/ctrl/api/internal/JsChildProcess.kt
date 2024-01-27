@file:Suppress("FunctionName")
@file:JsModule("child_process")
@file:JsNonModule

package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

/** [docs](https://nodejs.org/api/child_process.html#child_processexecsynccommand-options) */
@JsName("execSync")
internal external fun child_process_execSync(command: String): dynamic
