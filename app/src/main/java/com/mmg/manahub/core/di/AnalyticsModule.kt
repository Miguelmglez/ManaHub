package com.mmg.manahub.core.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.mmg.manahub.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics {
        val analytics = FirebaseAnalytics.getInstance(context)
        // Only enable analytics collection in release builds.
        analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        return analytics
    }
}
