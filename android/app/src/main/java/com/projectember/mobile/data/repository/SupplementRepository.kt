package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.SupplementEntryDao
import com.projectember.mobile.data.local.entities.SupplementEntry
import kotlinx.coroutines.flow.Flow

class SupplementRepository(private val dao: SupplementEntryDao) {

    fun getAll(): Flow<List<SupplementEntry>> = dao.getAll()

    suspend fun getById(id: Int): SupplementEntry? = dao.getById(id)

    suspend fun insert(entry: SupplementEntry): Long = dao.insert(entry)

    suspend fun update(entry: SupplementEntry) = dao.update(entry)

    suspend fun delete(entry: SupplementEntry) = dao.delete(entry)

    suspend fun getAllOnce(): List<SupplementEntry> = dao.getAllOnce()

    fun getByDefinitionId(definitionId: Int): Flow<List<SupplementEntry>> =
        dao.getByDefinitionId(definitionId)

    suspend fun getByDefinitionIdOnce(definitionId: Int): List<SupplementEntry> =
        dao.getByDefinitionIdOnce(definitionId)

    suspend fun deleteByDefinitionId(definitionId: Int) =
        dao.deleteByDefinitionId(definitionId)

    suspend fun replaceAll(entries: List<SupplementEntry>) {
        dao.deleteAll()
        entries.forEach { dao.insert(it) }
    }
}
