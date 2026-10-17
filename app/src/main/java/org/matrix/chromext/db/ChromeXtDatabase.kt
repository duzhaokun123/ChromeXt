package org.matrix.chromext.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [Script::class], version = 1)
@ColumnTypeConverters(Converters::class)
abstract class ChromeXtDatabase : RoomDatabase() {
  abstract fun scriptDao(): ScriptDao
}