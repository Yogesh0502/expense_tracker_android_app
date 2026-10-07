package com.example.expense_tracker_v2

import com.example.expense_tracker_v2.data.local.TransactionEntity
import java.io.Writer

// Export and Share deliberately use the same encoding, columns, escaping and row order.
object TransactionCsv {
    fun write(writer: Writer, transactions: List<TransactionEntity>) {
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

