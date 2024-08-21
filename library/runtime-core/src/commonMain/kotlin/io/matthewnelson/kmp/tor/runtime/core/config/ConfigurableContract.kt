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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.Companion.buildableInternal
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.toSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.internal.absoluteNormalizedFile
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.jvm.JvmSynthetic

/**
 * Used as a contractual agreement between [TorConfig2.BuilderScope]
 * and [TorOption] such that factory-like functionality can be had
 * via function calls [TorConfig2.BuilderScope.configure] and
 * [TorConfig2.BuilderScope.tryConfigure] for the [TorOption].
 *
 * This is the "root" interface that all `Configurable*` interface
 * types extend. No [TorOption] implements this interface directly.
 *
 * All [TorOption] that implement a [ConfigurableContract] type
 * have an accompanying static function `asSetting` to create its
 * [TorSetting], outside of the [TorConfig2.BuilderScope], if needed.
 *
 * @see [ConfigureBuildable]
 * @see [ConfigureBuildableTry]
 * @see [ConfigureBoolean]
 * @see [ConfigureDirectory]
 * @see [ConfigureFile]
 * @see [ConfigureInterval]
 * @see [ConfigureIntervalMsec]
 * */
public interface ConfigurableContract
// Inhibits TorOption from implementing
// multiple ConfigurableContract types.
<C: ConfigurableContract<C>>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that [TorOption.buildable] is implemented and
 * able to produce [B] for a DSL builder scope.
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureBuildable<B: TorSetting.BuilderScope>: ConfigurableContract<ConfigureBuildable<B>>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that [TorOption.buildable] is implemented and
 * able to produce [B] for a DSL builder scope.
 *
 * This is distinctly different from [ConfigureBuildable] in
 * that builder scope [B] has exceptional requirements, such as
 * the [TorOption] not being available for the host/environment,
 * or configuration requirements for [B] not met resulting in
 * an [IllegalArgumentException] when build gets automatically
 * called upon [ThisBlock] lambda closure.
 *
 * Unless *absolutely certain* that no errors will occur (e.g. you
 * know for a fact that requirements for [B] will be met), a try/catch
 * block **is** advised.
 *
 * Consult the documentation for the builder scope [B] regarding
 * what its exceptional requirements are.
 *
 * @see [TorConfig2.BuilderScope.tryConfigure]
 * */
public interface ConfigureBuildableTry<B: TorSetting.BuilderScope>: ConfigurableContract<ConfigureBuildableTry<B>>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a boolean argument,
 * available to be configured with `true` or `false` (which will
 * resolve to `1` or `0`, respectively, for the [TorSetting] argument).
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureBoolean: ConfigurableContract<ConfigureBoolean>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a [File] argument which
 * points to a directory location, and is available to be configured
 * when supplied one.
 *
 * [TorOption] that implement [ConfigureDirectory] will **always**
 * contain the [TorOption.Attribute.DIRECTORY] attribute.
 *
 * **NOTE:** Provided [File] is always sanitized for the resulting
 * [TorSetting] using [File.absoluteFile] + [File.normalize].
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureDirectory: ConfigurableContract<ConfigureDirectory>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a [File] argument which
 * points to a file location, and is available to be configured
 * when supplied one.
 *
 * [TorOption] that implement [ConfigureFile] will **always**
 * contain the [TorOption.Attribute.FILE] attribute.
 *
 * **NOTE:** Provided [File] is always sanitized for the resulting
 * [TorSetting] using [File.absoluteFile] + [File.normalize].
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureFile: ConfigurableContract<ConfigureFile>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses an interval argument,
 * and is available to be configured when supplied a number and the
 * [IntervalUnit] expression.
 *
 * **NOTE:** No range validation is performed. Consult documentation
 * of the [TorOption] (which points to `tor-man`) for its acceptable
 * minimum and maximum values. Otherwise, tor may error out.
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureInterval: ConfigurableContract<ConfigureInterval>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses an interval argument (in
 * milliseconds) and is available to be configured when supplied a
 * number.
 *
 * **NOTE:** No range validation is performed. Consult documentation
 * of the [TorOption] (which points to `tor-man`) for its acceptable
 * minimum and maximum values. Otherwise, tor may error out.
 *
 * @see [TorConfig2.BuilderScope.configure]
 * */
public interface ConfigureIntervalMsec: ConfigurableContract<ConfigureIntervalMsec>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun <B: TorSetting.BuilderScope> ConfigureBuildable<B>.buildContract(
    block: ThisBlock<B>,
): TorSetting = (this as TorOption).buildBuildable(block)

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class, UnsupportedOperationException::class)
internal inline fun <B: TorSetting.BuilderScope> ConfigureBuildableTry<B>.buildContract(
    block: ThisBlock<B>,
): TorSetting = (this as TorOption).buildBuildable(block)

@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class, UnsupportedOperationException::class)
private inline fun <B: TorSetting.BuilderScope> TorOption.buildBuildable(
    block: ThisBlock<B>,
): TorSetting = try {
    @Suppress("UNCHECKED_CAST")
    buildableInternal() as B
} catch (_: NullPointerException) {
    // Tests ensure this never occurs, but in an abundance
    // of caution this ensures we are at least throwing
    // the "expected" exception type.
    throw ClassCastException("TorOption.$this has not implemented buildable()")
}.apply(block).build()

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun ConfigureBoolean.buildContract(
    enable: Boolean,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(enable.byte.toString())
        .toSetting()
}

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun ConfigureDirectory.buildContract(
    directory: File,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(directory.absoluteNormalizedFile.path)
        .toSetting()
}

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun ConfigureFile.buildContract(
    file: File,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(file.absoluteNormalizedFile.path)
        .toSetting()
}

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun ConfigureInterval.buildContract(
    num: Int,
    interval: IntervalUnit,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(interval.of(num))
        .toSetting()
}

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun ConfigureIntervalMsec.buildContract(
    milliseconds: Int,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem("$milliseconds msec")
        .toSetting()
}
