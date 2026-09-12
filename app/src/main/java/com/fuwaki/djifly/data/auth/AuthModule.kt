package com.fuwaki.djifly.data.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 鉴权相关 Hilt 绑定：AuthRepository 实现 AccessTokenProvider，
 * 供 TokenAuthenticator（OkHttp authenticator）注入。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAccessTokenProvider(impl: AuthRepository): AccessTokenProvider
}
