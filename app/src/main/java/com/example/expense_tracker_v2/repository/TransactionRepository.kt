package com.example.expense_tracker_v2.repository

import com.example.expense_tracker_v2.data.local.TransactionDao
import com.example.expense_tracker_v2.data.local.TransactionEntity
import com.example.expense_tracker_v2.data.local.UserCategoryDao
import com.example.expense_tracker_v2.data.local.UserCategoryEntity
import com.example.expense_tracker_v2.data.local.PaymentAccountDao
import com.example.expense_tracker_v2.data.local.PaymentAccountEntity

class TransactionRepository(private val dao: TransactionDao, private val categoryDao: UserCategoryDao, private val accountDao: PaymentAccountDao) {
    fun allTransactions() = dao.getAllTransactions()
    fun transactionsForMonth(start: String, end: String) = dao.getTransactionsForMonth(start, end)
    fun monthlyIncome(start: String, end: String) = dao.getMonthlyIncome(start, end)
    fun monthlyExpense(start: String, end: String) = dao.getMonthlyExpense(start, end)
    fun monthlyTransfer(start: String, end: String) = dao.getMonthlyTransfer(start, end)
    fun customCategories() = categoryDao.getAll()
    fun paymentAccounts() = accountDao.getAll()
    suspend fun insert(transaction: TransactionEntity) = dao.insertTransaction(transaction)
    suspend fun update(transaction: TransactionEntity) = dao.updateTransaction(transaction)
    suspend fun delete(transaction: TransactionEntity) = dao.deleteTransaction(transaction)
    suspend fun addCategory(name: String) = categoryDao.insert(UserCategoryEntity(name))
    suspend fun deleteCategory(name: String): Boolean = categoryDao.deleteIfUnused(name) > 0
    suspend fun addAccount(name: String) = accountDao.insert(PaymentAccountEntity(name))
    suspend fun deleteAccount(name: String): Boolean = accountDao.deleteIfUnused(name) > 0
}
