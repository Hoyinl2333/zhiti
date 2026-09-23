package com.xiaoyunduo.zhiti

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.xiaoyunduo.zhiti.data.AppContainer
import com.xiaoyunduo.zhiti.data.user.UserDatabase

class ZhitiApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, UserDatabase::class.java, "user-data.db")
            .fallbackToDestructiveMigration(false)
            .build()
        container = AppContainer(this, database)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}

