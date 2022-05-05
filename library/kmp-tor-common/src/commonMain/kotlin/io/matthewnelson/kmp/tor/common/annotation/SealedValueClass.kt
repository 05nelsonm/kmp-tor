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
package io.matthewnelson.kmp.tor.common.annotation

import kotlin.jvm.JvmInline

/**
 * This annotation does absolutely nothing (currently)
 * and is for informational purposes only. It is marked
 * as [ExperimentalTorApi] and subject to change/removal
 * at any time.
 *
 * Denotes a sealed interface as having an underlying
 * value class annotated with [JvmInline] and can be
 * instantiated in Kotlin just like any other class.
 *
 * Because value classes compile to Java as their
 * underlying types, Java users are unable to benefit
 * from their awesomeness. By having the underlying
 * value class inherit from a sealed interface that
 * is returned upon invocation they can be extended
 * to Java users.
 *
 * ************************************ MySealedValueClass.kt
 * @[SealedValueClass]
 * sealed interface MySealedValueClass {
 *     val value: String
 *
 *     companion object {
 *         @JvmStatic
 *         operator fun invoke(value: String): MySealedValueClass {
 *             return RealMySealedValueClass(value)
 *         }
 *     }
 * }
 *
 * @[JvmInline]
 * private value class RealMySealedValueClass(
 *     override val value: String
 * ): MySealedValueClass {
 *     init {
 *         require(value.isNotBlank()) {
 *             "value cannot be blank"
 *         }
 *     }
 * }
 * ************************************
 *
 * ************************************ Example.kt
 * class Example {
 *     fun doSomething(value: MySealedValueClass): String {
 *         return value.value
 *     }
 * }
 * ************************************
 *
 * Instantiating a [SealedValueClass]:
 *  - From kotlin:
 *      val myClass = MySealedValueClass("some text")
 *  - From Java:
 *      MySealedValueClass myClass = MySealedValueClass.invoke("some text");
 *
 * @examples [io.matthewnelson.kmp.tor.common.address.Port]
 * */
@ExperimentalTorApi
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SealedValueClass
