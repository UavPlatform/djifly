package com.fuwaki.djifly.platform.network

import org.junit.Assert.assertNotNull
import org.junit.Test

/** DJI initialization uses these third-party classes even when app sources do not reference them. */
class DjiRuntimeDependenciesTest {
    @Test
    fun sdk_runtime_dependencies_are_available_without_initializing_native_code() {
        val requiredClasses = listOf(
            "org.aspectj.runtime.reflect.Factory",
            "androidx.room.RoomDatabase",
            "androidx.room.rxjava3.RxRoom",
            "net.sqlcipher.database.SQLiteDatabase",
            "io.reactivex.Observable",
            "xcrash.XCrash"
        )
        requiredClasses.forEach { name ->
            assertNotNull(name, Class.forName(name, false, javaClass.classLoader))
        }
    }
}
