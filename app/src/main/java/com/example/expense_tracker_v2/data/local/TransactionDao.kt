package com.example.expense_tracker_v2.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, createdAt DESC LIMIT 10")
    fun getRecentTransactionsForMonth(startDate: String, endDate: String): Flow<List<TransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransaction(transaction: TransactionEntity)
    @Update suspend fun updateTransaction(transaction: TransactionEntity)
    @Delete suspend fun deleteTransaction(transaction: TransactionEntity)
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC") fun getAllTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, createdAt DESC") fun getTransactionsForMonth(startDate: String, endDate: String): Flow<List<TransactionEntity>>
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'Income' AND date >= :startDate AND date <= :endDate") fun getMonthlyIncome(startDate: String, endDate: String): Flow<Double?>
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'Expense' AND date >= :startDate AND date <= :endDate") fun getMonthlyExpense(startDate: String, endDate: String): Flow<Double?>
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'Transfer' AND date >= :startDate AND date <= :endDate") fun getMonthlyTransfer(startDate: String, endDate: String): Flow<Double?>
    @Query("SELECT COUNT(*) FROM transactions WHERE category = :category") suspend fun categoryUseCount(category: String): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE paymentAccount = :account") suspend fun accountUseCount(account: String): Int
}

@Dao
interface UserCategoryDao {
    @Query("DELETE FROM custom_categories WHERE name = :name AND NOT EXISTS (SELECT 1 FROM transactions WHERE category = :name)")
    suspend fun deleteIfUnused(name: String): Int
    @Query("SELECT * FROM custom_categories ORDER BY name") fun getAll(): Flow<List<UserCategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(category: UserCategoryEntity)
    @Delete suspend fun delete(category: UserCategoryEntity)
}

@Dao
interface PaymentAccountDao {
    @Query("DELETE FROM payment_accounts WHERE name = :name AND NOT EXISTS (SELECT 1 FROM transactions WHERE paymentAccount = :name)")
    suspend fun deleteIfUnused(name: String): Int
    @Query("SELECT * FROM payment_accounts ORDER BY name") fun getAll(): Flow<List<PaymentAccountEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(account: PaymentAccountEntity)
    @Delete suspend fun delete(account: PaymentAccountEntity)
}
