package com.projectember.mobile.import

import android.content.Context
import com.projectember.mobile.data.local.db.AppDatabase

object ImportRepositoryProvider {
    @Volatile
    private var cached: ImportRepository? = null

    fun provide(context: Context): ImportRepository {
        return cached ?: synchronized(this) {
            cached ?: RoomImportRepository(
                weightDao = AppDatabase.getInstance(context).weightDao()
            ).also { cached = it }
        }
    }
}

