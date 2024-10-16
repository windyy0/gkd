package com.nnnn.myg.db

import androidx.room.Room
import com.nnnn.myg.app
import com.nnnn.myg.util.dbFolder

object DbSet {

    private fun buildDb(): AppDb {
        return Room.databaseBuilder(
            app, AppDb::class.java, dbFolder.resolve("myg.db").absolutePath
        ).fallbackToDestructiveMigration().build()
    }

    private val db by lazy { buildDb() }
    val subsItemDao
        get() = db.subsItemDao()
    val subsConfigDao
        get() = db.subsConfigDao()
    val snapshotDao
        get() = db.snapshotDao()
    val clickLogDao
        get() = db.clickLogDao()
    val categoryConfigDao
        get() = db.categoryConfigDao()
    val activityLogDao
        get() = db.activityLogDao()
}