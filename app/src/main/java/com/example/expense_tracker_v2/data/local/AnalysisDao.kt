package com.example.expense_tracker_v2.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "monthly_budgets")
data class MonthlyBudget(@PrimaryKey val month: String, val amount: Double)

data class SpendingGroup(val label: String, val amount: Double)
data class PeriodTotals(val income: Double, val expense: Double) {
    val balance: Double get() = income - expense
}

@Dao
interface AnalysisDao {
    @Query("""SELECT COALESCE(SUM(CASE WHEN type='Income' THEN amount ELSE 0 END),0) AS income,
        COALESCE(SUM(CASE WHEN type='Expense' THEN amount ELSE 0 END),0) AS expense
        FROM transactions WHERE date >= :start AND date < :end""")
    fun totals(start: String, end: String): Flow<PeriodTotals>

    @Query("""SELECT category AS label, SUM(amount) AS amount FROM transactions
        WHERE type='Expense' AND date >= :start AND date < :end
        GROUP BY category ORDER BY amount DESC, category""")
    fun categories(start: String, end: String): Flow<List<SpendingGroup>>

    @Query("""SELECT COALESCE(NULLIF(TRIM(paymentAccount),''),'Not set') AS label, SUM(amount) AS amount
        FROM transactions WHERE type='Expense' AND date >= :start AND date < :end
        GROUP BY COALESCE(NULLIF(TRIM(paymentAccount),''),'Not set') ORDER BY amount DESC, label""")
    fun accounts(start: String, end: String): Flow<List<SpendingGroup>>

    @Query("""SELECT needWant AS label, SUM(amount) AS amount FROM transactions
        WHERE type='Expense' AND date >= :start AND date < :end AND needWant IN ('Need','Want')
        GROUP BY needWant ORDER BY needWant""")
    fun needsWants(start: String, end: String): Flow<List<SpendingGroup>>

    @Query("SELECT * FROM monthly_budgets WHERE month=:month")
    fun budget(month: String): Flow<MonthlyBudget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setBudget(budget: MonthlyBudget)

    @Query("DELETE FROM monthly_budgets WHERE month=:month")
    suspend fun clearBudget(month: String)
}

