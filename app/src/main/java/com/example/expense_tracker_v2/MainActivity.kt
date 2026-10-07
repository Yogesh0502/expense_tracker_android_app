package com.example.expense_tracker_v2

import android.app.DatePickerDialog
import android.content.Intent
import android.content.ClipData
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.expense_tracker_v2.data.local.TransactionEntity
import com.example.expense_tracker_v2.data.local.TransactionOptions
import com.example.expense_tracker_v2.data.local.CategoryOption
import com.example.expense_tracker_v2.viewmodel.TransactionViewModel
import com.example.expense_tracker_v2.viewmodel.AnalysisViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ExpenseTrackerApp() } }
    }
}

private fun currency(amount: Double) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(amount)

@Composable
private fun ManageCategories(vm: TransactionViewModel, options: List<CategoryOption>, back: () -> Unit) {
    var type by rememberSaveable { mutableStateOf("Expense") }
    var parent by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<CategoryOption?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val categories = options.filter { it.type == type && it.parent.isEmpty() }
    val visible = options.filter { it.type == type && it.parent == parent }
    Page("Categories and Subcategories", back) {
        item { TypeSelector(type) { type = it; parent = ""; name = ""; editing = null } }
        if (type == "Expense") item {
            Picker("Manage", if (parent.isEmpty()) "Categories" else "Subcategories: $parent",
                listOf("Categories") + categories.map { it.name }) {
                parent = if (it == "Categories") "" else it
                name = ""; editing = null
            }
        }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (editing == null) "New name" else "Edit name") }) }
        item {
            Row {
                Button(enabled = !busy, onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) message = "Enter a name."
                    else if (visible.any { it.name.equals(trimmed, true) && it != editing })
                        message = "This name already exists."
                    else {
                        busy = true
                        val option = editing ?: CategoryOption(type, parent, trimmed)
                        vm.changeOption(option, if (editing == null) null else trimmed) { error ->
                            busy = false
                            message = error ?: "Saved."
                            if (error == null) { name = ""; editing = null }
                        }
                    }
                }) { Text(if (editing == null) "Add" else "Update") }
                if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("Cancel") }
            }
        }
        if (message.isNotEmpty()) item { Text(message) }
        item { Text("Renaming updates existing transactions. Names used by transactions cannot be deleted.",
            style = MaterialTheme.typography.bodySmall) }
        items(visible, key = { it.name }) { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(option.name, Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = { editing = option; name = option.name }) { Text("Edit") }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    vm.changeOption(option, null, delete = true) { error ->
                        busy = false; message = error ?: "Deleted."
                        if (error == null && editing == option) { editing = null; name = "" }
                    }
                }) { Text("Delete") }
            }
        }
    }
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
private fun ExpenseTrackerApp(vm: TransactionViewModel = viewModel()) {
    val nav = rememberNavController()
    val analysisVm: AnalysisViewModel = viewModel()
    val options by vm.categoryOptions.collectAsStateWithLifecycle()
    val accounts by vm.paymentAccounts.collectAsStateWithLifecycle()
    val transactions by vm.allTransactions.collectAsStateWithLifecycle()
    val back: () -> Unit = { nav.popBackStack(); Unit }
    NavHost(nav, startDestination = "dashboard") {
        composable("dashboard") {
            Dashboard(vm, analysisVm, { nav.navigate("add") }, { nav.navigate("history") },
                { nav.navigate("settings") }, { nav.navigate("analysis") })
        }
        composable("add") {
            TransactionForm(vm, null, options, accounts.map { it.name }) {
                nav.popBackStack("dashboard", false)
            }
        }
        composable("history") {
            History(transactions, options.filter { it.parent.isEmpty() }.map { it.name }.distinct(), accounts.map { it.name }, back) { nav.navigate("edit/$it") }
        }
        composable("edit/{id}") { entry ->
            val transaction = transactions.firstOrNull { it.id == entry.arguments?.getString("id")?.toLongOrNull() }
            if (transaction != null) TransactionForm(vm, transaction, options, accounts.map { it.name }, back)
            else Page("Transaction", back) { item { Text("Loading transaction...") } }
        }
        composable("settings") {
            Settings(vm, back, { nav.navigate("categories") }, { nav.navigate("accounts") })
        }
        composable("analysis") {
            AnalysisScreen(analysisVm, back, { nav.navigate("add") })
        }
        composable("categories") {
            ManageCategories(vm, options, back)
        }
        composable("accounts") {
            ManageOptions("Manage Payment Accounts", accounts.map { it.name }, back,
                { vm.addAccount(it) }, { name, callback -> vm.deleteAccount(name, callback); Unit })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, back: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = back) { Text("Back") } })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(vm: TransactionViewModel, analysisVm: AnalysisViewModel, add: () -> Unit,
                      history: () -> Unit, settings: () -> Unit, analysis: () -> Unit) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    val breakdown by analysisVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.month) { analysisVm.selectMonth(state.month) }
    Scaffold(topBar = { TopAppBar(title = { Text("Expense Tracker") },
        actions = { TextButton(onClick = settings) { Text("Settings") } }) },
        floatingActionButton = { FloatingActionButton(onClick = add) { Text("+") } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::previousMonth) { Text("<") }
                    Text(state.month.format(monthFormat), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::nextMonth) { Text(">") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Summary("Income", state.income, Color(0xFF1B7F3A), Modifier.weight(1f))
                    Summary("Expenses", state.expense, Color(0xFFB3261E), Modifier.weight(1f))
                }
            }
            item { Summary("Balance", state.income - state.expense, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) }
            item { Text("Transfers: " + currency(state.transfer), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { TextButton(onClick = analysis) { Text("Analysis") } }
            if (breakdown.month == state.month && !breakdown.loading && breakdown.error == null) {
                item { BudgetCard(breakdown, analysisVm) }
                item { Text("Expense Breakdown", style = MaterialTheme.typography.titleLarge) }
                if (breakdown.categories.isEmpty()) item { Text("No expenses for this month.") }
                items(breakdown.categories, key = { "breakdown-" + it.label }) { group ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(group.label, Modifier.weight(1f))
                        Text(currency(group.amount))
                    }
                }
                item { InsightsCard(breakdown) }
            }
            item {
                Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = history) { Text("All Transactions") }
            }
            if (state.transactions.isEmpty()) item { Text("No transactions for this month.") }
            items(state.transactions.take(10), key = { it.id }) { TransactionRow(it) }
        }
    }
}

@Composable
private fun Summary(label: String, amount: Double, color: Color, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) {
        Text(label)
        Text(currency(amount), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    } }
}

@Composable
private fun TransactionRow(t: TransactionEntity, click: (() -> Unit)? = null) {
    val color = when (t.type) { "Income" -> Color(0xFF1B7F3A); "Expense" -> Color(0xFFB3261E); else -> MaterialTheme.colorScheme.primary }
    val prefix = when (t.type) { "Income" -> "+ "; "Expense" -> "- "; else -> "" }
    Card(Modifier.fillMaxWidth().then(if (click == null) Modifier else Modifier.clickable { click() })) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Text(t.description.ifBlank { t.category }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(prefix + currency(t.amount), color = color, fontWeight = FontWeight.Bold)
            }
            Text(listOfNotNull(t.category, t.subcategory, t.date, t.type).joinToString(" / "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val details = listOfNotNull(t.paymentAccount, t.forPerson, t.needWant)
            if (details.isNotEmpty()) Text(details.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TransactionForm(vm: TransactionViewModel, old: TransactionEntity?, options: List<CategoryOption>,
                            accounts: List<String>, done: () -> Unit) {
    val id = old?.id ?: 0L
    var amount by rememberSaveable(id) { mutableStateOf(old?.amount?.toString().orEmpty()) }
    var type by rememberSaveable(id) { mutableStateOf(old?.type ?: "Expense") }
    var category by rememberSaveable(id) { mutableStateOf(old?.category ?: "") }
    var description by rememberSaveable(id) { mutableStateOf(old?.description.orEmpty()) }
    var date by rememberSaveable(id) { mutableStateOf(old?.date ?: vm.today()) }
    var subcategory by rememberSaveable(id) { mutableStateOf(old?.subcategory) }
    var person by rememberSaveable(id) { mutableStateOf(old?.forPerson) }
    var account by rememberSaveable(id) { mutableStateOf(old?.paymentAccount) }
    var merchant by rememberSaveable(id) { mutableStateOf(old?.merchant.orEmpty()) }
    var need by rememberSaveable(id) { mutableStateOf(if (old == null) "Need" else old.needWant) }
    var recurring by rememberSaveable(id) { mutableStateOf(old?.recurring ?: false) }
    var more by rememberSaveable(id) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Page(if (old == null) "Add Transaction" else "Edit Transaction", done) {
        item { TypeSelector(type) {
            if (type != it) { type = it; category = ""; subcategory = null; more = false }
        } }
        item { OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount") },
            prefix = { Text("\u20B9 ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        item { Picker("Category", category.ifBlank { "Select category" }, (options.filter { it.type == type && it.parent.isEmpty() }.map { it.name } + listOfNotNull(old?.category?.takeIf { old?.type == type })).distinct()) {
            category = it; subcategory = null
        } }
        item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description (optional)") }) }
        item { DateField(date) { date = it } }
        if (type != "Income") item { TextButton(onClick = { more = !more }) { Text(if (more) "Hide More Options" else "More Options") } }
        if (more && type != "Income") {
            if (type == "Expense") item { OptionalPicker("Subcategory", subcategory, options.filter { it.type == "Expense" && it.parent == category }.map { it.name }) { subcategory = it } }
            item { OptionalPicker("For", person, TransactionOptions.people) { person = it } }
            item { OptionalPicker("Payment Account", account, accounts) { account = it } }
            item { OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("Merchant (optional)") }) }
            item { OptionalPicker("Need / Want", need, listOf("Need", "Want")) { need = it } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recurring", Modifier.weight(1f)); Switch(recurring, { recurring = it })
            } }
        }
        if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Button(onClick = {
            val value = amount.toDoubleOrNull()
            when {
                value == null || !value.isFinite() || value <= 0 -> error = "Enter an amount greater than 0."
                category.isBlank() -> error = "Select a category."
                else -> {
                    busy = true
                    vm.save(TransactionEntity(id = id, date = date, description = description.trim(), amount = value,
                        type = type, category = category, subcategory = if (type == "Expense") subcategory else null, forPerson = person,
                        paymentAccount = account, merchant = merchant.trim().ifBlank { null }, needWant = need,
                        recurring = recurring, createdAt = old?.createdAt ?: System.currentTimeMillis())) { message ->
                        busy = false
                        if (message == null) done() else error = message
                    }
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving..." else "Save Transaction") } }
        if (old != null) item { OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete Transaction") } }
    }
    if (confirm && old != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Delete Transaction?") }, text = { Text("Are you sure you want to delete this transaction?") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            vm.delete(old) { message ->
                busy = false; confirm = false
                if (message == null) done() else error = message
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: String, choose: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TransactionOptions.types.forEachIndexed { index, type ->
            SegmentedButton(selected = selected == type, onClick = { choose(type) },
                shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(type) }
        }
    }
}

@Composable
private fun History(transactions: List<TransactionEntity>, categories: List<String>, accounts: List<String>,
                    back: () -> Unit, edit: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("All") }
    var type by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    var account by rememberSaveable { mutableStateOf("All") }
    var need by rememberSaveable { mutableStateOf("All") }
    var filters by rememberSaveable { mutableStateOf(false) }
    val months = (transactions.map { it.date.take(7) } + YearMonth.now().toString()).distinct().sortedDescending()
    val filtered = transactions.filter { t ->
        (month == "All" || t.date.startsWith(month)) && (type == "All" || t.type == type) &&
        (category == "All" || t.category == category) && (account == "All" || t.paymentAccount == account) &&
        (need == "All" || t.needWant == need) &&
        (query.isBlank() || t.description.contains(query.trim(), true) || t.merchant.orEmpty().contains(query.trim(), true))
    }
    Page("All Transactions", back) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search description or merchant") }) }
        item { TextButton(onClick = { filters = !filters }) { Text(if (filters) "Hide filters" else "Filters") } }
        if (filters) {
            item { Picker("Month", month, listOf("All") + months) { month = it } }
            item { Picker("Type", type, listOf("All") + TransactionOptions.types) { type = it } }
            item { Picker("Category", category, listOf("All") + (categories + transactions.map { it.category }).distinct()) { category = it } }
            item { Picker("Payment Account", account, listOf("All") + (accounts + transactions.mapNotNull { it.paymentAccount }).distinct()) { account = it } }
            item { Picker("Need / Want", need, listOf("All", "Need", "Want")) { need = it } }
            item { TextButton(onClick = { month = "All"; type = "All"; category = "All"; account = "All"; need = "All"; query = "" }) { Text("Clear filters") } }
        }
        item { Text("${filtered.size} transactions") }
        if (filtered.isEmpty()) item { Text("No matching transactions.") }
        items(filtered, key = { it.id }) { t -> TransactionRow(t) { edit(t.id) } }
    }
}

@Composable
private fun Settings(vm: TransactionViewModel, back: () -> Unit, categories: () -> Unit, accounts: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            busy = true
            vm.exportCsv(uri) { error -> busy = false; message = error ?: "CSV exported successfully" }
        }
    }
    Page("Settings", back) {
        item { OutlinedButton(onClick = categories, modifier = Modifier.fillMaxWidth()) { Text("Manage Categories") } }
        item { OutlinedButton(onClick = accounts, modifier = Modifier.fillMaxWidth()) { Text("Manage Payment Accounts") } }
        item { Button(onClick = { export.launch("expenses-${LocalDate.now()}.csv") }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Exporting..." else "Export CSV") } }
        item { OutlinedButton(onClick = {
            busy = true
            message = ""
            vm.prepareCsvShare { uri, error ->
                busy = false
                if (uri == null) message = error ?: "Could not create CSV."
                else try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Expense Tracker CSV Export")
                        putExtra(Intent.EXTRA_TEXT, "Expense Tracker transactions exported on ${LocalDate.now()}.")
                        clipData = ClipData.newUri(context.contentResolver, "Expense Tracker CSV", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share expense data").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: Exception) {
                    message = "Unable to open the share sheet. Please try again."
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Share CSV") } }
        if (message.isNotEmpty()) item { Text(message) }
        item { Text("About", style = MaterialTheme.typography.titleLarge); Text("Expense Tracker\nYour personal offline expense tracker. Recurring is a label only; it does not create transactions automatically.") }
    }
}

@Composable
private fun ManageOptions(title: String, options: List<String>, back: () -> Unit,
                          add: (String) -> Unit, delete: (String, (Boolean) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Page(title, back) {
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New name") }) }
        item { Button(onClick = {
            val trimmed = name.trim()
            when {
                trimmed.isBlank() -> message = "Enter a name."
                options.any { it.equals(trimmed, true) } -> message = "This name already exists."
                else -> { add(trimmed); name = ""; message = "" }
            }
        }) { Text("Add") } }
        if (message.isNotEmpty()) item { Text(message) }
        items(options, key = { it }) { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(option, Modifier.weight(1f))
                TextButton(onClick = { delete(option) { ok ->
                    message = if (ok) "Deleted." else "Cannot delete: this name is used by a transaction."
                } }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun OptionalPicker(label: String, value: String?, options: List<String>, choose: (String?) -> Unit) {
    Picker(label, value ?: "Not set", listOf("Not set") + (options + listOfNotNull(value)).distinct()) {
        choose(if (it == "Not set") null else it)
    }
}

@Composable
private fun Picker(label: String, value: String, options: List<String>, choose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { choose(option); open = false }) }
        }
    }
}

@Composable
private fun DateField(date: String, choose: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val current = LocalDate.parse(date)
        DatePickerDialog(context, { _, year, month, day ->
            choose(LocalDate.of(year, month + 1, day).toString())
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("Date: $date") }
}

