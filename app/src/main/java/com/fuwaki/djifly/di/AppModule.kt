package com.fuwaki.djifly.di

import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(PlatformHeadersInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.BASIC
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun providePlatformApiService(
        endpointResolver: PlatformEndpointResolver,
        gson: Gson,
        client: OkHttpClient
    ): PlatformApiService {
        return Retrofit.Builder()
            .baseUrl(endpointResolver.requireHttpBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PlatformApiService::class.java)
    }
}

private class PlatformHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "djifly/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        return chain.proceed(request)
    }
}
