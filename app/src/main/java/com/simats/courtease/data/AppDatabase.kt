package com.simats.courtease.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Version 2 adds claimant, respondent and stage. Dev builds just rebuild the table.
@Database(entities = [CaseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "courtease.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}