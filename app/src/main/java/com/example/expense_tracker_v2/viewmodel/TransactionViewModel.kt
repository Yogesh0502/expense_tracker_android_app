package com.example.expense_tracker_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expense_tracker_v2.data.local.AppDatabase
import com.example.expense_tracker_v2.data.local.TransactionEntity
import com.example.expense_tracker_v2.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.YearMonth

data class DashboardState(val month: YearMonth, val transactions: List<TransactionEntity> = emptyList(), val income: Double = 0.0, val expense: Double = 0.0, val transfer: Double = 0.0)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = TransactionRepository(database.transactionDao(), database.userCategoryDao(), database.paymentAccountDao())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private fun bounds(month: YearMonth) = month.atDay(1).toString() to month.atEndOfMonth().toString()
    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard = selectedMonth.flatMapLatest { month ->
        val (start, end) = bounds(month)
        combine(repository.transactionsForMonth(start, end), repository.monthlyIncome(start, end), repository.monthlyExpense(start, end), repository.monthlyTransfer(start, end)) { transactions, income, expense, transfer -> DashboardState(month, transactions, income ?: 0.0, expense ?: 0.0, transfer ?: 0.0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState(YearMonth.now()))
    val allTransactions = repository.allTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customCategories = repository.customCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paymentAccounts = repository.paymentAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun save(transaction: TransactionEntity) = viewModelScope.launch { if (transaction.id == 0L) repository.insert(transaction) else repository.update(transaction) }
    fun delete(transaction: TransactionEntity) = viewModelScope.launch { repository.delete(transaction) }
    fun addCategory(name: String) = viewModelScope.launch { repository.addCategory(name.trim()) }
    fun deleteCategory(name: String, result: (Boolean) -> Unit) = viewModelScope.launch { result(repository.deleteCategory(name)) }
    fun addAccount(name: String) = viewModelScope.launch { repository.addAccount(name.trim()) }
    fun deleteAccount(name: String, result: (Boolean) -> Unit) = viewModelScope.launch { result(repository.deleteAccount(name)) }
    fun today() = LocalDate.now().toString()
}
