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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.net.Uri
import com.example.expense_tracker_v2.data.local.CategoryOption
import java.time.LocalDate
import java.time.YearMonth

data class DashboardState(val month: YearMonth, val transactions: List<TransactionEntity> = emptyList(), val income: Double = 0.0, val expense: Double = 0.0, val transfer: Double = 0.0)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = TransactionRepository(database.transactionDao(), database.userCategoryDao(), database.paymentAccountDao())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    val categoryOptions = database.categoryOptionDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun changeOption(option: CategoryOption, newName: String?, delete: Boolean = false, result: (String?) -> Unit) = viewModelScope.launch {
        try {
            if (delete) {
                if (!database.categoryOptionDao().delete(option)) { result("Cannot delete: used by a transaction."); return@launch }
            } else if (newName == null) {
                database.categoryOptionDao().insert(option)
                if (option.type == "Expense" && option.parent.isEmpty())
                    database.categoryOptionDao().insert(CategoryOption("Expense", option.name, "Other"))
            } else database.categoryOptionDao().rename(option, newName.trim())
            result(null)
        } catch (e: Exception) { result("Could not update. Check that this name does not already exist.") }
    }
    private fun bounds(month: YearMonth) = month.atDay(1).toString() to month.atEndOfMonth().toString()
    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard = selectedMonth.flatMapLatest { month ->
        val (start, end) = bounds(month)
        combine(repository.recentTransactionsForMonth(start, end), repository.monthlyIncome(start, end), repository.monthlyExpense(start, end), repository.monthlyTransfer(start, end)) { transactions, income, expense, transfer -> DashboardState(month, transactions, income ?: 0.0, expense ?: 0.0, transfer ?: 0.0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState(YearMonth.now()))
    val allTransactions = repository.allTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customCategories = repository.customCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paymentAccounts = repository.paymentAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun save(transaction: TransactionEntity, result: (String?) -> Unit) = viewModelScope.launch {
        try {
            require(transaction.amount.isFinite() && transaction.amount > 0 && transaction.category.isNotBlank())
            if (transaction.id == 0L) repository.insert(transaction) else repository.update(transaction)
            result(null)
        } catch (e: Exception) { result("Could not save transaction. Please try again.") }
    }
    fun delete(transaction: TransactionEntity, result: (String?) -> Unit) = viewModelScope.launch {
        try { repository.delete(transaction); result(null) }
        catch (e: Exception) { result("Could not delete transaction.") }
    }
    fun exportCsv(uri: Uri, result: (String?) -> Unit) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val transactions = repository.allTransactions().first()
                val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("Unable to open document")
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFFDate,Description,Amount,Type,Category,Subcategory,For,Payment Account,Merchant,Need/Want,Recurring\r\n")
                    transactions.forEach { t ->
                        val fields = listOf(t.date, t.description, t.amount.toString(), t.type, t.category,
                            t.subcategory.orEmpty(), t.forPerson.orEmpty(), t.paymentAccount.orEmpty(),
                            t.merchant.orEmpty(), t.needWant.orEmpty(), t.recurring.toString())
                        writer.write(fields.joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" })
                        writer.write("\r\n")
                    }
                }
            }
            result(null)
        } catch (e: Exception) { result("CSV export failed. Please choose a writable location and try again.") }
    }
    fun addCategory(name: String) = viewModelScope.launch { repository.addCategory(name.trim()) }
    fun deleteCategory(name: String, result: (Boolean) -> Unit) = viewModelScope.launch { result(repository.deleteCategory(name)) }
    fun addAccount(name: String) = viewModelScope.launch { repository.addAccount(name.trim()) }
    fun deleteAccount(name: String, result: (Boolean) -> Unit) = viewModelScope.launch { result(repository.deleteAccount(name)) }
    fun today() = LocalDate.now().toString()
}
