package com.example.expense_tracker_v2.repository

import com.example.expense_tracker_v2.data.local.AnalysisDao
import com.example.expense_tracker_v2.data.local.MonthlyBudget
import java.time.YearMonth

class AnalysisRepository(private val dao: AnalysisDao) {
    private fun start(month: YearMonth) = month.atDay(1).toString()
    private fun end(month: YearMonth) = month.plusMonths(1).atDay(1).toString()
    fun totals(month: YearMonth) = dao.totals(start(month), end(month))
    fun categories(month: YearMonth) = dao.categories(start(month), end(month))
    fun accounts(month: YearMonth) = dao.accounts(start(month), end(month))
    fun needsWants(month: YearMonth) = dao.needsWants(start(month), end(month))
    fun budget(month: YearMonth) = dao.budget(month.toString())
    suspend fun setBudget(month: YearMonth, amount: Double?) {
        if (amount == null) dao.clearBudget(month.toString())
        else {
            require(amount.isFinite() && amount > 0)
            dao.setBudget(MonthlyBudget(month.toString(), amount))
        }
    }
}

