package com.example.expense_tracker_v2.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "category_options", primaryKeys = ["type", "parent", "name"])
data class CategoryOption(val type: String, val parent: String = "", val name: String)

@Dao
abstract class CategoryOptionDao {
    @Query("SELECT * FROM category_options ORDER BY name COLLATE NOCASE")
    abstract fun all(): Flow<List<CategoryOption>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(option: CategoryOption)
    @Query("DELETE FROM category_options WHERE type=:type AND parent=:parent AND name=:name AND NOT EXISTS (SELECT 1 FROM transactions WHERE type=:type AND ((:parent='' AND category=:name) OR (:parent!='' AND category=:parent AND subcategory=:name)))")
    abstract suspend fun deleteUnused(type: String, parent: String, name: String): Int
    @Query("DELETE FROM category_options WHERE type=:type AND parent=:name")
    abstract suspend fun deleteChildren(type: String, name: String)
    @Query("UPDATE category_options SET name=:newName WHERE type=:type AND parent=:parent AND name=:oldName")
    abstract suspend fun renameOption(type: String, parent: String, oldName: String, newName: String)
    @Query("UPDATE category_options SET parent=:newName WHERE type=:type AND parent=:oldName")
    abstract suspend fun renameChildren(type: String, oldName: String, newName: String)
    @Query("UPDATE transactions SET category=:newName WHERE type=:type AND category=:oldName")
    abstract suspend fun renameCategoryTransactions(type: String, oldName: String, newName: String)
    @Query("UPDATE transactions SET subcategory=:newName WHERE type=:type AND category=:parent AND subcategory=:oldName")
    abstract suspend fun renameSubTransactions(type: String, parent: String, oldName: String, newName: String)
    @Transaction
    open suspend fun rename(option: CategoryOption, newName: String) {
        renameOption(option.type, option.parent, option.name, newName)
        if (option.parent.isEmpty()) {
            renameChildren(option.type, option.name, newName)
            renameCategoryTransactions(option.type, option.name, newName)
        } else renameSubTransactions(option.type, option.parent, option.name, newName)
    }
    @Transaction
    open suspend fun delete(option: CategoryOption): Boolean {
        val deleted = deleteUnused(option.type, option.parent, option.name) > 0
        if (deleted && option.parent.isEmpty()) deleteChildren(option.type, option.name)
        return deleted
    }
}
