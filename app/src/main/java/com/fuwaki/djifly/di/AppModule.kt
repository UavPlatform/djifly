package com.fuwaki.djifly.di

import android.content.Context
import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.data.auth.AuthInterceptor
import com.fuwaki.djifly.data.auth.TokenAuthenticator
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.fuwaki.djifly.platform.eventstore.android.AndroidEventStore
import com.fuwaki.djifly.platform.eventstore.core.EventPacketStore
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EventUploadClient

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
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)           // Token 注入
            .authenticator(tokenAuthenticator)         // 401 自动刷新重试（FLY 高-1）
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

    @Provides
    @Singleton
    fun provideEventPacketStore(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    ): EventPacketStore {
        val directory = java.io.File(context.noBackupFilesDir, "event-packets")
        val database = java.io.File(directory, "packets.sqlite")
        val identity = java.io.File(directory, "recording-source")
        return if (database.isFile || identity.isFile) {
            AndroidEventStore.recover(context)
        } else {
            AndroidEventStore.initializeNew(context)
        }
    }

    @Provides
    @Singleton
    @EventUploadClient
    fun provideEventUploadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()
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
