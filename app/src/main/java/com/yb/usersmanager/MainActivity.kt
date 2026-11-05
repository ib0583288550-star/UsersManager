package com.yb.usersmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class DeviceUser(val id: Int, val name: String, val current: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UsersScreen() }
    }
}

private fun root(command: String): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code == 0) output else "שגיאת Root: $error"
    } catch (e: Exception) {
        "לא ניתן להפעיל Root: ${e.message}"
    }
}

private suspend fun loadUsers(): Pair<List<DeviceUser>, String> = withContext(Dispatchers.IO) {
    val output = root("pm list users")
    val currentText = root("am get-current-user").trim()
    val currentId = currentText.toIntOrNull()
    val users = Regex("""UserInfo\{(\d+):([^:}]+).*?\}""").findAll(output).map {
        val id = it.groupValues[1].toInt()
        DeviceUser(id, it.groupValues[2], id == currentId)
    }.toList()
    users to output
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersScreen() {
    val navy = Color(0xFF101827)
    val panel = Color(0xFF1B2940)
    val accent = Color(0xFF70E1C1)
    var users by remember { mutableStateOf<List<DeviceUser>>(emptyList()) }
    var status by remember { mutableStateOf("בודק הרשאת Root…") }
    var maxUsers by remember { mutableIntStateOf(4) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("משתמש חדש") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        busy = true
        val result = loadUsers()
        users = result.first
        status = if (result.first.isNotEmpty()) "מחובר ל-Root • ${result.first.size} משתמשים" else result.second
        val max = withContext(Dispatchers.IO) { root("pm get-max-users").substringAfterLast(" ").trim().toIntOrNull() }
        if (max != null) maxUsers = max
        busy = false
    }
    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = accent, background = navy, surface = panel,
        onPrimary = navy, onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = navy, topBar = {
            TopAppBar(
                title = { Column { Text("מנהל משתמשים", fontWeight = FontWeight.Bold); Text("ניהול משתמשי המכשיר", fontSize = 12.sp, color = Color.LightGray) } },
                actions = { IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Default.Refresh, null, tint = accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = navy, titleContentColor = Color.White)
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("המשתמשים שלי", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(status, fontSize = 12.sp, color = Color.LightGray)
                        }
                        Text("${users.size}/$maxUsers", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("חשבונות במכשיר", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { refresh() } }) { Text("רענון") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(users, key = { it.id }) { user ->
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(Color(0xFF30445F), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = accent)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.name, fontWeight = FontWeight.SemiBold)
                                    Text("מזהה משתמש: ${user.id}" + if (user.current) " • פעיל עכשיו" else "", fontSize = 12.sp, color = Color.LightGray)
                                }
                                if (!user.current) {
                                    FilledTonalButton(onClick = {
                                        scope.launch {
                                            busy = true
                                            status = withContext(Dispatchers.IO) { root("am switch-user ${user.id}") }
                                            refresh()
                                        }
                                    }, enabled = !busy) { Text("מעבר") }
                                } else {
                                    Text("פעיל", color = accent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (users.isEmpty()) item { Text("לא נמצאו משתמשים או שאין הרשאת Root.", color = Color.LightGray) }
                }
                Button(
                    onClick = { showCreate = true },
                    enabled = users.size < maxUsers && !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (users.size >= maxUsers) "הגעת למגבלת המשתמשים" else "יצירת משתמש חדש")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { scope.launch { refresh() } }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "טוען…" else "רענון")
                }
            }
        }
        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false },
                title = { Text("יצירת משתמש חדש") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("שם המשתמש") }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        showCreate = false
                        scope.launch {
                            busy = true
                            status = withContext(Dispatchers.IO) {
                                root("pm create-user '${newName.replace("'", "")}'")
                            }
                            refresh()
                        }
                    }) { Text("צור משתמש") }
                },
                dismissButton = { TextButton(onClick = { showCreate = false }) { Text("ביטול") } }
            )
        }
    }
}
