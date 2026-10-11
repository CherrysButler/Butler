package dagger.hilt

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class EntryPoint

@Target(AnnotationTarget.CLASS)
annotation class InstallIn(vararg val value: KClass<*>)
