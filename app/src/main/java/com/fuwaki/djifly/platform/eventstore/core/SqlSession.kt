package com.fuwaki.djifly.platform.eventstore.core

import java.io.Closeable

/** Small boundary so the same store is exercised against SQLite on the JVM and Android. */
interface SqlSession : Closeable {
    fun execute(sql: String, vararg args: Any?)
    fun query(sql: String, vararg args: Any?): List<List<Any?>>
    fun <T> transaction(block: () -> T): T
}
