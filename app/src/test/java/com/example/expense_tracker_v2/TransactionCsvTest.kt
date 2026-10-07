package com.example.expense_tracker_v2

import com.example.expense_tracker_v2.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class TransactionCsvTest {
    @Test
    fun keepsExistingFormatIncludingEscapingUnicodeNullsAndAllRows() {
        val rows = listOf(
            TransactionEntity(date = "2026-10-07", description = "Tea, \"coffee\"\nshop", amount = 25.5,
                type = "Expense", category = "Food", subcategory = "Tea/Coffee", forPerson = "Self",
                paymentAccount = "Cash", merchant = "Café", needWant = "Want", recurring = true),
            TransactionEntity(date = "2026-10-06", description = "", amount = 1000.0, type = "Transfer", category = "Self")
        )
        val output = StringWriter()
        TransactionCsv.write(output, rows)
        assertEquals(
            "\uFEFFDate,Description,Amount,Type,Category,Subcategory,For,Payment Account,Merchant,Need/Want,Recurring\r\n" +
            "\"2026-10-07\",\"Tea, \"\"coffee\"\"\nshop\",\"25.5\",\"Expense\",\"Food\",\"Tea/Coffee\",\"Self\",\"Cash\",\"Café\",\"Want\",\"true\"\r\n" +
            "\"2026-10-06\",\"\",\"1000.0\",\"Transfer\",\"Self\",\"\",\"\",\"\",\"\",\"\",\"false\"\r\n",
            output.toString()
        )
    }
}

