package com.manalejandro.alejabber.di

import android.content.Context
import androidx.room.Room
import com.manalejandro.alejabber.data.local.AppDatabase
import com.manalejandro.alejabber.data.local.dao.AccountDao
import com.manalejandro.alejabber.data.local.dao.ContactDao
import com.manalejandro.alejabber.data.local.dao.MessageDao
import com.manalejandro.alejabber.data.local.dao.RoomDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "alejabber.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideRoomDao(db: AppDatabase): RoomDao = db.roomDao()
}

