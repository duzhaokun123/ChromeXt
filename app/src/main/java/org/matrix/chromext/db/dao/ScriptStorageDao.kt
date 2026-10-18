package org.matrix.chromext.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import org.matrix.chromext.db.entity.ScriptStorage

@Dao
interface ScriptStorageDao {
  @Query("SELECT * FROM ScriptStorage WHERE id = :id")
  suspend fun getById(id: String): ScriptStorage?

  @Upsert
  suspend fun upsert(vararg script: ScriptStorage)

  @Upsert
  suspend fun upsert(script: List<ScriptStorage>)

  @Query("DELETE FROM ScriptStorage WHERE id In (:id)")
  suspend fun deleteById(vararg id: String)
}