package com.example.expense_tracker_v2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val description: String,
    val amount: Double,
    val type: String,
    val category: String,
    val subcategory: String? = null,
    val forPerson: String? = null,
    val paymentAccount: String? = null,
    val merchant: String? = null,
    val needWant: String? = null,
    val recurring: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_categories")
data class UserCategoryEntity(@PrimaryKey val name: String)

@Entity(tableName = "payment_accounts")
data class PaymentAccountEntity(@PrimaryKey val name: String)
