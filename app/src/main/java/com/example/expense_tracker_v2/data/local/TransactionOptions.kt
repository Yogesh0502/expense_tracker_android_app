package com.example.expense_tracker_v2.data.local

object TransactionOptions {
    val categories = listOf("Food", "Travel", "Shopping", "Bills & Subscriptions", "Family", "Personal", "Health", "Entertainment", "Investment", "Education", "Other")
    val accounts = listOf("ICICI Debit", "ICICI Credit Card", "HDFC Credit Card", "Cash", "UPI", "Bank Transfer", "Other")
    val people = listOf("Self", "Wife", "Daughter", "Family", "Household", "Other")
    val types = listOf("Expense", "Income", "Transfer")
    fun subcategories(category: String): List<String> = (when (category) {
        "Food" -> listOf("Restaurant", "Swiggy", "Zomato", "Groceries", "Tea/Coffee")
        "Travel" -> listOf("Fuel", "Cab", "Metro", "Bus", "Train", "Parking")
        "Shopping" -> listOf("Clothing", "Electronics", "Household", "Online Shopping")
        "Bills & Subscriptions" -> listOf("Electricity", "Internet", "Mobile", "Credit Card Bill", "OTT")
        "Personal" -> listOf("Nicotine", "Grooming")
        "Health" -> listOf("Medicine", "Doctor", "Tests")
        else -> emptyList()
    }) + "Other"
}
