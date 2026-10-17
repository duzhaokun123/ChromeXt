package org.matrix.chromext.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import org.json.JSONObject

@Dao
interface ScriptDao {
  @Query("SELECT * FROM Script")
  suspend fun getAll(): List<Script>

  @Upsert
  suspend fun upsert(vararg script: Script)

  @Upsert
  suspend fun upsert(scripts: List<Script>)

  @Query("DELETE FROM Script WHERE id In (:id)")
  suspend fun deleteById(vararg id: String)

  @Query("UPDATE Script SET storage = :storage WHERE id = :id")
  suspend fun updateStorage(id: String, storage: JSONObject)
}