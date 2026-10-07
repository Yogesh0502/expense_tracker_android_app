package com.example.expense_tracker_v2

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expense_tracker_v2.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AnalysisDatabaseTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun expenseAggregationExcludesTransfersAndIncomeAndKeepsUnknownNeedUnclassified() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.transactionDao()
            suspend fun add(amount: Double, type: String, date: String = "2026-10-01", need: String? = null) {
                dao.insertTransaction(TransactionEntity(date = date, description = "", amount = amount,
                    type = type, category = "Food", paymentAccount = "Cash", needWant = need))
            }
            add(100.0, "Expense", need = "Need")
            add(50.0, "Expense", need = "Want")
            add(25.0, "Expense")
            add(900.0, "Transfer")
            add(1000.0, "Income")
            add(500.0, "Expense", date = "2026-09-30")
            add(600.0, "Expense", date = "2026-11-01")
            val analysis = db.analysisDao()
            val totals = analysis.totals("2026-10-01", "2026-11-01").first()
            assertEquals(175.0, totals.expense, 0.001)
            assertEquals(1000.0, totals.income, 0.001)
            assertEquals(825.0, totals.balance, 0.001)
            assertEquals(175.0, analysis.categories("2026-10-01", "2026-11-01").first().single().amount, 0.001)
            assertEquals(175.0, analysis.accounts("2026-10-01", "2026-11-01").first().single().amount, 0.001)
            assertEquals(150.0, analysis.needsWants("2026-10-01", "2026-11-01").first().sumOf { it.amount }, 0.001)
        } finally { db.close() }
    }

    @Test
    fun phaseOneMigrationPreservesTransactionAndBudgetPersistsAfterReopen() = runBlocking {
        val name = "migration-" + UUID.randomUUID() + ".db"
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        legacy.execSQL("""CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL, description TEXT NOT NULL, amount REAL NOT NULL,
            type TEXT NOT NULL, category TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
        legacy.execSQL("INSERT INTO transactions VALUES (1,'2026-10-01','Original expense',125.5,'Expense','Food',100)")
        legacy.version = 1
        legacy.close()
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.migrations()).build()
        var db = open()
        try {
            val transaction = db.transactionDao().getAllTransactions().first().single()
            assertEquals("Original expense", transaction.description)
            assertEquals(125.5, transaction.amount, 0.001)
            assertNull(transaction.needWant)
            assertFalse(transaction.recurring)
            db.analysisDao().setBudget(MonthlyBudget("2026-10", 60000.0))
            db.close()
            db = open()
            assertEquals(60000.0, db.analysisDao().budget("2026-10").first()!!.amount, 0.001)
            assertEquals(1, db.transactionDao().getAllTransactions().first().size)
            assertNull(db.analysisDao().budget("2026-11").first())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}

