package com.example.expense_tracker_v2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionEntity::class, UserCategoryEntity::class, PaymentAccountEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun userCategoryDao(): UserCategoryDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
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
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expense_tracker.db").addMigrations(migration1To2).build().also { instance = it }
        }
    }
}
