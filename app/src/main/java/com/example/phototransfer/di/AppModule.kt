package com.example.phototransfer.di

import android.content.Context
import androidx.room.Room
import com.example.phototransfer.data.local.AppDatabase
import com.example.phototransfer.data.local.dao.TransferRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "phototransfer_database"
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideTransferRecordDao(database: AppDatabase): TransferRecordDao {
        return database.transferRecordDao()
    }
}
