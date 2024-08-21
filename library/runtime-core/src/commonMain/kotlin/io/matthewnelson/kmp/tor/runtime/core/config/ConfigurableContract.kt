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
 * via [TorConfig2.BuilderScope.configure] (or alternatively the
 * [TorConfig2.BuilderScope.tryConfigure] call) for the implementing
 * [TorOption]. This is the "root" interface that all `Configurable*`
 * interface types extend.
 *
 * All [TorOption] that implement a [ConfigurableContract] type
 * will have an accompanying static function `asSetting` to create
 * the [TorSetting] outside of the [TorConfig2.BuilderScope], if
 * desired.
 *
 * @see [ConfigureBuildable]
 * @see [ConfigureBuildableTry]
 * @see [ConfigureBoolean]
 * @see [ConfigureDirectory]
 * @see [ConfigureFile]
 * */
public interface ConfigurableContract
// Inhibits TorOption from implementing
// multiple ConfigurableContract types.
<C: ConfigurableContract<C>>

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that [TorOption.buildable] is implemented and
 * able to produce [B] for [TorConfig2.BuilderScope.configure].
 * */
public interface ConfigureBuildable<B: TorSetting.BuilderScope>: ConfigurableContract<ConfigureBuildable<B>>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal inline fun <B: TorSetting.BuilderScope> ConfigureBuildable<B>.buildContract(
    block: ThisBlock<B>,
): TorSetting = (this as TorOption).buildBuildable(block)

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that [TorOption.buildable] is implemented and
 * able to produce [B] for [TorConfig2.BuilderScope.tryConfigure].
 *
 * This is distinctly different from [ConfigureBuildable] in
 * that builder scope [B] may throw exception due to requirements
 * not being met, such as the [TorOption] not being available for
 * the given host or environment, or the builder being misconfigured
 * resulting in an [IllegalArgumentException] when build is
 * called (automatically happens on lambda closure).
 * */
public interface ConfigureBuildableTry<B: TorSetting.BuilderScope>: ConfigurableContract<ConfigureBuildableTry<B>>

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

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a boolean argument,
 * available to be configured with an argument of `1` or `0`, for
 * [TorConfig2.BuilderScope.configure].
 * */
public interface ConfigureBoolean: ConfigurableContract<ConfigureBoolean>

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

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a [File] argument
 * to a directory location and is available to be configured via
 * [TorConfig2.BuilderScope.configure] when supplied one.
 *
 * [TorOption] implementors of [ConfigureDirectory] **MUST**
 * also contain [TorOption.Attribute.DIRECTORY].
 *
 * **NOTE:** Provided [File] is always sanitized for the resulting
 * [TorSetting] using [File.absoluteFile] + [File.normalize].
 * */
public interface ConfigureDirectory: ConfigurableContract<ConfigureDirectory>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal fun ConfigureDirectory.buildContract(
    directory: File,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(directory.absoluteNormalizedFile.path)
        .toSetting()
}

/**
 * Denotes a [TorOption] as implementing the [ConfigurableContract]
 * which declares that the [TorOption] uses a [File] argument
 * to a file location and is available to be configured via
 * [TorConfig2.BuilderScope.configure] when supplied one.
 *
 * [TorOption] implementors of [ConfigureDirectory] **MUST**
 * also contain [TorOption.Attribute.FILE].
 *
 * **NOTE:** Provided [File] is always sanitized for the resulting
 * [TorSetting] using [File.absoluteFile] + [File.normalize].
 * */
public interface ConfigureFile: ConfigurableContract<ConfigureFile>

@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
internal fun ConfigureFile.buildContract(
    file: File,
): TorSetting {
    @OptIn(ExperimentalKmpTorApi::class)
    return (this as TorOption)
        .toLineItem(file.absoluteNormalizedFile.path)
        .toSetting()
}
