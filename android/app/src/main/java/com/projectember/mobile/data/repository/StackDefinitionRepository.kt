package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.StackDefinitionDao
import com.projectember.mobile.data.local.entities.StackDefinition
import kotlinx.coroutines.flow.Flow

class StackDefinitionRepository(private val dao: StackDefinitionDao) {

    fun getAll(): Flow<List<StackDefinition>> = dao.getAll()

    suspend fun getById(id: Int): StackDefinition? = dao.getById(id)

    suspend fun insert(definition: StackDefinition): Long = dao.insert(definition)

    suspend fun update(definition: StackDefinition) = dao.update(definition)

    suspend fun delete(definition: StackDefinition) = dao.delete(definition)

    suspend fun getAllOnce(): List<StackDefinition> = dao.getAllOnce()

    suspend fun findByBarcode(barcode: String): StackDefinition? = dao.findByBarcode(barcode)

    suspend fun replaceAll(definitions: List<StackDefinition>) {
        dao.deleteAll()
        definitions.forEach { dao.insert(it) }
    }
}
