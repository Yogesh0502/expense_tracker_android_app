package com.example.expense_tracker_v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expense_tracker_v2.data.local.SpendingGroup
import com.example.expense_tracker_v2.viewmodel.AnalysisState
import com.example.expense_tracker_v2.viewmodel.AnalysisViewModel
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private fun analysisMoney(value: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(value)
private fun percent(value: Double, total: Double) = if (total <= 0) "0%" else String.format(Locale.getDefault(), "%.1f%%", value / total * 100)
private val analysisMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val chartColors = listOf(Color(0xFF4169B1), Color(0xFF238878), Color(0xFFD88B26),
    Color(0xFF8D5DA7), Color(0xFFC95264), Color(0xFF637381))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: AnalysisViewModel, back: () -> Unit, add: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") },
        navigationIcon = { TextButton(onClick = back) { Text("Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = add) { Text("+") } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(state.month.format(analysisMonthFormat), style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { vm.selectMonth(YearMonth.now()) }) { Text("This Month") }
                    TextButton(onClick = { vm.selectMonth(YearMonth.now().minusMonths(1)) }) { Text("Last Month") }
                    TextButton(onClick = { custom = true }) { Text("Custom") }
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
            else if (state.error != null) item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else {
                item { Text("Total Expenses: " + analysisMoney(state.totals.expense), style = MaterialTheme.typography.headlineSmall) }
                item { Text("Expense by Category", style = MaterialTheme.typography.titleLarge) }
                item { ExpenseDonut(state.categories) }
                if (state.categories.isEmpty()) item { Text("No expenses in this month.") }
                items(state.categories, key = { "category-" + it.label }) { SpendingRow(it, state.totals.expense) }
                item { Text("Needs vs Wants", style = MaterialTheme.typography.titleLarge) }
                if (state.needsWants.isEmpty()) item { Text("No expenses have a Need/Want value.") }
                items(state.needsWants, key = { "need-" + it.label }) { SpendingRow(it, state.needsWants.sumOf { group -> group.amount }) }
                item {
                    val classified = state.needsWants.sumOf { it.amount }
                    Text("Percentages include classified expenses only. Unclassified: " +
                        analysisMoney((state.totals.expense - classified).coerceAtLeast(0.0)), style = MaterialTheme.typography.bodySmall)
                }
                item { Text("Spending by Payment Account", style = MaterialTheme.typography.titleLarge) }
                if (state.accounts.isEmpty()) item { Text("No expenses in this month.") }
                items(state.accounts, key = { "account-" + it.label }) { SpendingRow(it, state.totals.expense) }
                item { MonthlyComparison(state) }
                item { BudgetCard(state, vm) }
                item { InsightsCard(state) }
            }
        }
    }
    if (custom) CustomMonthDialog(state.month, { custom = false }) { vm.selectMonth(it); custom = false }
}

@Composable
private fun CustomMonthDialog(initial: YearMonth, dismiss: () -> Unit, select: (YearMonth) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Choose month") },
        text = { Column {
            OutlinedTextField(text, { text = it }, label = { Text("Year-Month (2026-10)") }, singleLine = true)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            val month = runCatching { YearMonth.parse(text.trim()) }.getOrNull()
            if (month == null || month.year !in 1900..9999) error = "Enter a month such as 2026-10."
            else select(month)
        }) { Text("Select") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun SpendingRow(group: SpendingGroup, total: Double, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (color != null) Canvas(Modifier.size(12.dp)) { drawCircle(color) }
        Text(group.label, Modifier.weight(1f).padding(start = if (color == null) 0.dp else 8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(analysisMoney(group.amount))
            Text(percent(group.amount, total), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpenseDonut(groups: List<SpendingGroup>) {
    val total = groups.sumOf { it.amount }
    if (total <= 0) return
    // Five largest categories plus a combined remainder keep the chart readable.
    val slices = if (groups.size <= 6) groups else groups.take(5) +
        SpendingGroup("Remaining categories", groups.drop(5).sumOf { it.amount })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp).semantics { contentDescription = "Expense by category. Details and percentages are listed below." }) {
                val thickness = 32.dp.toPx()
                val inset = thickness / 2
                val chartSize = Size(size.width - thickness, size.height - thickness)
                var start = -90f
                slices.forEachIndexed { index, group ->
                    val sweep = (group.amount / total * 360).toFloat()
                    drawArc(chartColors[index], start, sweep, false, Offset(inset, inset), chartSize, style = Stroke(thickness))
                    start += sweep
                }
            }
            Text("Expenses", style = MaterialTheme.typography.titleMedium)
        }
        slices.forEachIndexed { index, group -> SpendingRow(group, total, chartColors[index]) }
    }
}

private fun changeText(current: Double, previous: Double): String {
    val difference = current - previous
    val amount = (if (difference > 0) "+" else if (difference < 0) "-" else "") + analysisMoney(abs(difference))
    val percentage = if (previous == 0.0) {
        if (current == 0.0) "0%" else "No previous baseline"
    } else String.format(Locale.getDefault(), "%+.1f%%", difference / abs(previous) * 100)
    return "$amount ($percentage)"
}

@Composable
private fun MonthlyComparison(state: AnalysisState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly Comparison", style = MaterialTheme.typography.titleLarge)
            Text(state.month.minusMonths(1).format(analysisMonthFormat) + " → " + state.month.format(analysisMonthFormat))
            ComparisonRow("Income", state.totals.income, state.previous.income)
            ComparisonRow("Expenses", state.totals.expense, state.previous.expense)
            ComparisonRow("Balance", state.totals.balance, state.previous.balance)
            if (state.previous.balance < 0) Text("Balance percentage uses the magnitude of last month's balance.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComparisonRow(label: String, current: Double, previous: Double) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(analysisMoney(previous) + " → " + analysisMoney(current))
    Text(changeText(current, previous), style = MaterialTheme.typography.bodySmall)
}

@Composable
fun BudgetCard(state: AnalysisState, vm: AnalysisViewModel) {
    var editing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly Budget", style = MaterialTheme.typography.titleLarge)
            Text(state.month.format(analysisMonthFormat))
            val budget = state.budget
            if (budget == null) Text("No budget set for this month.")
            else {
                Text("Budget: " + analysisMoney(budget.amount))
                Text("Spent: " + analysisMoney(state.totals.expense))
                val remaining = budget.amount - state.totals.expense
                Text((if (remaining < 0) "Over budget: " else "Remaining: ") + analysisMoney(abs(remaining)),
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(progress = { (state.totals.expense / budget.amount).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                Text(percent(state.totals.expense, budget.amount) + " used")
            }
            TextButton(onClick = { editing = true }) { Text(if (budget == null) "Set Budget" else "Change Budget") }
        }
    }
    if (editing) BudgetDialog(state, vm) { editing = false }
}

@Composable
private fun BudgetDialog(state: AnalysisState, vm: AnalysisViewModel, dismiss: () -> Unit) {
    val month = state.month
    var amount by rememberSaveable(month.toString()) { mutableStateOf(state.budget?.amount?.toString().orEmpty()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun save(value: Double?) {
        busy = true
        vm.setBudget(month, value) { message ->
            busy = false
            if (message == null) dismiss() else error = message
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Budget for " + month.format(analysisMonthFormat)) },
        text = { Column {
            OutlinedTextField(amount, { amount = it }, label = { Text("Monthly amount") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (state.budget != null) TextButton(enabled = !busy, onClick = { save(null) }) { Text("Remove Budget") }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val value = amount.toDoubleOrNull()
            if (value == null || !value.isFinite() || value <= 0) error = "Enter an amount greater than 0."
            else save(value)
        }) { Text("Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun InsightsCard(state: AnalysisState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.month == YearMonth.now()) "This Month" else state.month.format(analysisMonthFormat),
                style = MaterialTheme.typography.titleLarge)
            Text("You spent " + analysisMoney(state.totals.expense) + " on expenses.")
            state.categories.firstOrNull()?.let { Text(it.label + " is your highest spending category.") }
            val previous = state.previous.expense
            if (previous > 0) {
                val difference = state.totals.expense - previous
                Text("You spent " + percent(abs(difference), previous) +
                    (if (difference >= 0) " more" else " less") + " than the previous month.")
            } else if (state.totals.expense > 0) Text("There were no expenses in the previous month.")
            state.needsWants.firstOrNull { it.label == "Want" }?.let {
                Text("You spent " + analysisMoney(it.amount) + " on Wants.")
            }
        }
    }
}

