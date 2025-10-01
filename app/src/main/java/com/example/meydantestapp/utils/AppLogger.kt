package com.example.meydantestapp.utils

import java.io.PrintStream
import java.lang.reflect.Method

/**
 * Lightweight logging utility that avoids a hard dependency on [android.util.Log].
 * When running on Android, it delegates to Log via reflection. On the JVM (unit tests),
 * it falls back to stdout/stderr so tests do not crash with "Log not mocked" errors.
 */
object AppLogger {

    private enum class Level(val prefix: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    private val androidLogClass: Class<*>? = runCatching {
        Class.forName("android.util.Log")
    }.getOrNull()

    private val logNoThrowable: Map<Level, Method> = androidLogClass?.let { clazz ->
        Level.values().associateWithNotNull { level ->
            clazz.safeMethod(level.methodName(), String::class.java, String::class.java)
        }
    } ?: emptyMap()

    private val logWithThrowable: Map<Level, Method> = androidLogClass?.let { clazz ->
        Level.values().associateWithNotNull { level ->
            clazz.safeMethod(level.methodName(), String::class.java, String::class.java, Throwable::class.java)
        }
    } ?: emptyMap()

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        if (androidLogClass != null) {
            val method = if (throwable != null) logWithThrowable[level] else logNoThrowable[level]
            if (method != null) {
                runCatching {
                    if (throwable != null) {
                        method.invoke(null, tag, message, throwable)
                    } else {
                        method.invoke(null, tag, message)
                    }
                }.onFailure {
                    fallback(level, tag, message, throwable)
                }
                return
            }
        }
        fallback(level, tag, message, throwable)
    }

    private fun fallback(level: Level, tag: String, message: String, throwable: Throwable?) {
        val stream: PrintStream = when (level) {
            Level.ERROR, Level.WARN -> System.err
            else -> System.out
        }
        stream.println("${level.prefix}/$tag: $message")
        if (throwable != null) {
            throwable.printStackTrace(stream)
        }
    }

    private fun Level.methodName(): String = when (this) {
        Level.DEBUG -> "d"
        Level.INFO -> "i"
        Level.WARN -> "w"
        Level.ERROR -> "e"
    }

    private fun Class<*>.safeMethod(name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching { getMethod(name, *parameterTypes) }.getOrNull()

    private inline fun <T, reified V> Array<T>.associateWithNotNull(transform: (T) -> V?): Map<T, V> where T : Enum<T> =
        buildMap {
            for (element in this@associateWithNotNull) {
                val value = transform(element)
                if (value != null) {
                    put(element, value)
                }
            }
        }
}
