package com.example.expense_tracker_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expense_tracker_v2.data.local.*
import com.example.expense_tracker_v2.repository.AnalysisRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.YearMonth

data class AnalysisState(
    val month: YearMonth = YearMonth.now(),
    val totals: PeriodTotals = PeriodTotals(0.0, 0.0),
    val previous: PeriodTotals = PeriodTotals(0.0, 0.0),
    val categories: List<SpendingGroup> = emptyList(),
    val accounts: List<SpendingGroup> = emptyList(),
    val needsWants: List<SpendingGroup> = emptyList(),
    val budget: MonthlyBudget? = null,
    val loading: Boolean = true,
    val error: String? = null
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AnalysisRepository(AppDatabase.get(application).analysisDao())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = selectedMonth.flatMapLatest { month ->
        val totals = combine(repository.totals(month), repository.totals(month.minusMonths(1))) { current, previous -> current to previous }
        val groups = combine(repository.categories(month), repository.accounts(month), repository.needsWants(month)) { categories, accounts, needs ->
            Triple(categories, accounts, needs)
        }
        combine(totals, groups, repository.budget(month)) { amounts, breakdown, budget ->
            AnalysisState(month, amounts.first, amounts.second, breakdown.first, breakdown.second, breakdown.third, budget, loading = false)
        }.flowOn(Dispatchers.Default)
            .onStart { emit(AnalysisState(month)) }
            .catch { emit(AnalysisState(month, loading = false, error = "Unable to load analysis.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisState())

    fun selectMonth(month: YearMonth) { selectedMonth.value = month }
    fun setBudget(month: YearMonth, amount: Double?, result: (String?) -> Unit) = viewModelScope.launch {
        try { repository.setBudget(month, amount); result(null) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { result("Unable to save budget. Please try again.") }
    }
}

