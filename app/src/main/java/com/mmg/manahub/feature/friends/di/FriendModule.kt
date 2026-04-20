package com.mmg.manahub.feature.friends.di

import com.mmg.manahub.feature.friends.data.remote.FriendshipService
import com.mmg.manahub.feature.friends.data.repository.FriendRepositoryImpl
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendModule {

    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    companion object {
        @Provides
        @Singleton
        fun provideFriendshipService(
            @Named("supabase") retrofit: Retrofit,
        ): FriendshipService = retrofit.create(FriendshipService::class.java)
    }
}
