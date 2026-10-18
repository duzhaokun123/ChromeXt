package org.matrix.chromext.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import org.matrix.chromext.db.entity.Script
import java.util.UUID

@Dao
interface ScriptDao {
  @Query("SELECT * FROM Script")
  suspend fun getAll(): List<Script>

  @Query("SELECT * FROM Script WHERE id = :id")
  suspend fun getById(id: String): Script?

  @Upsert
  suspend fun upsert(vararg script: Script)

  @Upsert
  suspend fun upsert(scripts: List<Script>)

  @Query("DELETE FROM Script WHERE id In (:id)")
  suspend fun deleteById(vararg id: String)

  @Query("DELETE FROM Script WHERE uuid in (:uuid)")
  suspend fun deleteByUUID(vararg uuid: UUID)

  @Query("UPDATE Script SET meta = :meta WHERE id = :id")
  suspend fun updateMeta(id: String, meta: String)
}