package com.example.expense_tracker_v2

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.expense_tracker_v2.data.local.TransactionEntity
import com.example.expense_tracker_v2.viewmodel.TransactionViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { ExpenseTrackerApp() } } } }
private val categories = listOf("Food", "Travel", "Shopping", "Bills & Subscriptions", "Family", "Personal", "Health", "Entertainment", "Investment", "Education", "Other")
private val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

@Composable private fun ExpenseTrackerApp(vm: TransactionViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "dashboard") {
        composable("dashboard") { Dashboard(vm, { nav.navigate("add") }, { nav.navigate("history") }) }
        composable("add") { TransactionForm(vm, null) { nav.popBackStack() } }
        composable("history") { History(vm) { nav.navigate("edit/$it") } }
        composable("edit/{id}") { entry -> vm.allTransactions.collectAsStateWithLifecycle().value.firstOrNull { it.id == entry.arguments?.getString("id")?.toLongOrNull() }?.let { TransactionForm(vm, it) { nav.popBackStack() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Dashboard(vm: TransactionViewModel, onAdd: () -> Unit, onHistory: () -> Unit) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } }) { pad -> LazyColumn(Modifier.padding(pad).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Expense Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = vm::previousMonth) { Text("‹ Previous") }; Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontWeight = FontWeight.Bold); TextButton(onClick = vm::nextMonth) { Text("Next ›") } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Summary("Income", state.income, Color(0xFF1B7F3A), Modifier.weight(1f)); Summary("Expenses", state.expense, Color(0xFFB3261E), Modifier.weight(1f)) } }
        item { Summary("Balance", state.income - state.expense, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Recent Transactions", style = MaterialTheme.typography.titleLarge); TextButton(onClick = onHistory) { Text("All Transactions") } } }
        if (state.transactions.isEmpty()) item { Text("No transactions for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.transactions.take(10), key = { it.id }) { TransactionRow(it) }
    } }
}
@Composable private fun Summary(label: String, amount: Double, color: Color, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(money.format(amount), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) } } }
@Composable private fun TransactionRow(item: TransactionEntity, onClick: (() -> Unit)? = null) { Card(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable { onClick() })) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.description.ifBlank { item.category }, fontWeight = FontWeight.SemiBold); Text("${item.category} · ${displayDate(item.date)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text((if (item.type == "Income") "+ " else "- ") + money.format(item.amount), color = if (item.type == "Income") Color(0xFF1B7F3A) else Color(0xFFB3261E), fontWeight = FontWeight.Bold) } } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun History(vm: TransactionViewModel, onEdit: (Long) -> Unit) { val list by vm.allTransactions.collectAsStateWithLifecycle(); Scaffold(topBar = { TopAppBar(title = { Text("All Transactions") }) }) { pad -> LazyColumn(Modifier.padding(pad).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (list.isEmpty()) item { Text("No transactions yet.") }; items(list, key = { it.id }) { TransactionRow(it) { onEdit(it.id) } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TransactionForm(vm: TransactionViewModel, old: TransactionEntity?, done: () -> Unit) {
    var amount by remember(old) { mutableStateOf(old?.amount?.toString().orEmpty()) }; var type by remember(old) { mutableStateOf(old?.type ?: "Expense") }; var category by remember(old) { mutableStateOf(old?.category ?: categories.first()) }; var description by remember(old) { mutableStateOf(old?.description.orEmpty()) }; var date by remember(old) { mutableStateOf(old?.date ?: vm.today()) }; var error by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (old == null) "Add Transaction" else "Edit Transaction") }) }) { pad -> Column(Modifier.padding(pad).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("Expense", "Income").forEachIndexed { i, name -> SegmentedButton(type == name, { type = name }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(name) } } }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("₹ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        CategoryPicker(category) { category = it }; OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description (optional)") }); DateField(date) { date = it }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = { val value = amount.toDoubleOrNull(); if (value == null || value <= 0) error = "Enter an amount greater than 0." else { vm.save(TransactionEntity(id = old?.id ?: 0, date = date, description = description.trim(), amount = value, type = type, category = category, createdAt = old?.createdAt ?: System.currentTimeMillis())); done() } }, modifier = Modifier.fillMaxWidth()) { Text("Save Transaction") }
        if (old != null) OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Transaction") }
    } }
    if (confirm && old != null) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete Transaction?") }, text = { Text("Are you sure you want to delete this transaction?") }, confirmButton = { TextButton(onClick = { vm.delete(old); done() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
@Composable private fun CategoryPicker(selected: String, choose: (String) -> Unit) { var open by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: $selected", Modifier.weight(1f)); Text("▾") }; DropdownMenu(open, { open = false }) { categories.forEach { DropdownMenuItem({ Text(it) }, { choose(it); open = false }) } } } }
@Composable private fun DateField(date: String, choose: (String) -> Unit) { val context = androidx.compose.ui.platform.LocalContext.current; OutlinedButton(onClick = { val now = LocalDate.parse(date); DatePickerDialog(context, { _, y, m, d -> choose(LocalDate.of(y, m + 1, d).toString()) }, now.year, now.monthValue - 1, now.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${displayDate(date)}") } }
private fun displayDate(date: String) = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM"))
