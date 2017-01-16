/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.script.lang.kotlin

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Allows the container to be configured, creating missing objects as they are referenced.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 */
inline operator fun <T : Any, C : NamedDomainObjectContainer<T>> C.invoke(
    configuration: NamedDomainObjectContainerConfiguration<T>.() -> Unit): C =

    apply {
        configuration(NamedDomainObjectContainerConfiguration(this))
    }


class NamedDomainObjectContainerConfiguration<T : Any>(
    private val container: NamedDomainObjectContainer<T>) : NamedDomainObjectContainer<T> by container {

    inline operator fun String.invoke(configuration: T.() -> Unit): T =
        this().apply(configuration)

    operator fun String.invoke(): T =
        container.maybeCreate(this)

    inline operator fun <U : T> String.invoke(type: KClass<U>, configuration: U.() -> Unit): U =
        this(type).apply(configuration)

    operator fun <U : T> String.invoke(type: KClass<U>): U =
        polymorphicDomainObjectContainer().maybeCreate(this, type.java)

    private fun polymorphicDomainObjectContainer() =
        // We must rely on the dynamic cast and possible runtime failure here
        // due to a Kotlin extension member limitation.
        // Kotlin currently can't disambiguate between invoke operators with
        // more specific receivers in a type hierarchy.
        container as? PolymorphicDomainObjectContainer<T>
            ?: throw IllegalArgumentException("Container '$container' is not polymorphic.")
}


val <T : Any> NamedDomainObjectContainer<T>.creating
    get() = NamedDomainObjectContainerDelegateProvider(this, {})


fun <T : Any> NamedDomainObjectContainer<T>.creating(configuration: T.() -> Unit) =
    NamedDomainObjectContainerDelegateProvider(this, configuration)


class NamedDomainObjectContainerDelegateProvider<T : Any>(
    val container: NamedDomainObjectContainer<T>, val configuration: T.() -> Unit) {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        container.apply {
            create(property.name).apply(configuration)
        }
}


inline fun <T : Any, reified U : T> PolymorphicDomainObjectContainer<T>.creating(
    type: KClass<U>) = creating(type.java, {})


inline fun <T : Any, reified U : T> PolymorphicDomainObjectContainer<T>.creating(
    type: KClass<U>, noinline configuration: U.() -> Unit) = creating(type.java, configuration)


fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: Class<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerDelegateProvider(this, type, configuration)


class PolymorphicDomainObjectContainerDelegateProvider<T : Any, U : T>(
    val container: PolymorphicDomainObjectContainer<T>, val type: Class<U>, val configuration: U.() -> Unit) {

    @Suppress("unchecked_cast")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        container.apply {
            create(property.name, type).apply(configuration)
        } as PolymorphicDomainObjectContainer<U>
}
