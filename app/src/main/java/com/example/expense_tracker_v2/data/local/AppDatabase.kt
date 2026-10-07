package com.example.expense_tracker_v2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionEntity::class, UserCategoryEntity::class, PaymentAccountEntity::class, CategoryOption::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun userCategoryDao(): UserCategoryDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    abstract fun categoryOptionDao(): CategoryOptionDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        private fun seedTypedOptions(db: SupportSQLiteDatabase) {
            fun add(type: String, parent: String, name: String) {
                db.execSQL("INSERT OR IGNORE INTO category_options(type,parent,name) VALUES (?,?,?)", arrayOf(type,parent,name))
            }
            TransactionOptions.categories.forEach { category ->
                add("Expense", "", category)
                TransactionOptions.subcategories(category).forEach { add("Expense", category, it) }
            }
            listOf("Salary", "Interest", "Other").forEach { add("Income", "", it) }
            listOf("Self", "ICICI Credit Card", "Axis CC", "IDFC CC", "Other").forEach { add("Transfer", "", it) }
            db.execSQL("INSERT OR IGNORE INTO category_options(type,parent,name) SELECT 'Expense','',name FROM custom_categories")
            db.execSQL("INSERT OR IGNORE INTO category_options(type,parent,name) SELECT DISTINCT type,'',category FROM transactions")
            db.execSQL("INSERT OR IGNORE INTO category_options(type,parent,name) SELECT DISTINCT 'Expense',category,subcategory FROM transactions WHERE type='Expense' AND subcategory IS NOT NULL")
        }
        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS category_options (type TEXT NOT NULL, parent TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(type,parent,name))")
                seedTypedOptions(db)
            }
        }
        private fun seedOptions(db: SupportSQLiteDatabase) {
            TransactionOptions.categories.forEach { db.execSQL("INSERT OR IGNORE INTO custom_categories(name) VALUES (?)", arrayOf(it)) }
            TransactionOptions.accounts.forEach { db.execSQL("INSERT OR IGNORE INTO payment_accounts(name) VALUES (?)", arrayOf(it)) }
            db.execSQL("INSERT OR IGNORE INTO custom_categories(name) SELECT DISTINCT category FROM transactions")
            db.execSQL("INSERT OR IGNORE INTO payment_accounts(name) SELECT DISTINCT paymentAccount FROM transactions WHERE paymentAccount IS NOT NULL")
        }
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { seedOptions(db) }
        }
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN subcategory TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN forPerson TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN paymentAccount TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN needWant TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (name TEXT NOT NULL, PRIMARY KEY(name))")
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_accounts (name TEXT NOT NULL, PRIMARY KEY(name))")
            }
        }
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expense_tracker.db")
                .addMigrations(migration1To2, migration2To3, migration3To4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) { seedOptions(db); seedTypedOptions(db) }
                }).build().also { instance = it }
        }
    }
}
