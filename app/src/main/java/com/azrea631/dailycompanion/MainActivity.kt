package com.azrea631.dailycompanion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

private const val OPEN_WEATHER_API_KEY = "805c2391ffd170a595abd992b7dde661"

private val LOCATIONS = mapOf(
    "Loveland" to Pair(40.3978, -105.074),
    "Longmont" to Pair(40.1672, -105.1019),
    "Boulder" to Pair(40.01499, -105.2705),
    "Fort Collins" to Pair(40.5853, -105.0844)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notesFile = File(filesDir, "notes.json")
        if (!notesFile.exists()) notesFile.writeText("[]")
        setContent {
            DailyCompanionApp()
        }
    }
}

@Composable
fun DailyCompanionApp() {
    val navController = rememberNavController()
    val scaffoldState = rememberScaffoldState()
    MaterialTheme {
        Scaffold(
            scaffoldState = scaffoldState,
            topBar = { TopBar(navController) },
            bottomBar = { BottomNav(navController) }
        ) { innerPadding ->
            NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
                composable("home") { HomeScreen(navController) }
                composable("weather") { WeatherScreen() }
                composable("notes") { NotesScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

@Composable
fun TopBar(navController: NavHostController) {
    TopAppBar(
        backgroundColor = MaterialTheme.colors.primary,
        elevation = 8.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DailyCompanion", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("•", color = MaterialTheme.colors.secondary)
            }
        }
    )
}

@Composable
fun BottomNav(navController: NavHostController) {
    val items = listOf("home", "weather", "notes", "settings")
    BottomNavigation(
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 12.dp
    ) {
        items.forEach { route ->
            val icon = when (route) {
                "home" -> Icons.Default.CalendarToday
                "weather" -> Icons.Default.Cloud
                "notes" -> Icons.Default.Note
                else -> Icons.Default.Settings
            }
            BottomNavigationItem(
                icon = { Icon(icon, contentDescription = route) },
                selected = currentRoute(navController) == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colors.primary, MaterialTheme.colors.primaryVariant))
    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                elevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Good morning!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your daily snapshot", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { navController.navigate("weather") }) { Text("Weather") }
                        Button(onClick = { navController.navigate("notes") }) { Text("Notes") }
                        Button(onClick = { navController.navigate("settings") }) { Text("Settings") }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            LazyColumn {
                item {
                    QuickTile(title = "Motivational Quote") { /* TODO */ }
                }
                item {
                    QuickTile(title = "Add Water Reminder") { /* TODO */ }
                }
                item {
                    QuickTile(title = "Backup Now") { /* TODO */ }
                }
            }
        }
    }
}

@Composable
fun QuickTile(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        elevation = 6.dp
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WeatherScreen() {
    val coroutineScope = rememberCoroutineScope()
    val client = remember { OkHttpClient() }
    var results by remember { mutableStateOf<Map<String, Pair<Double, String>>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, Pair<Double, String>>()
            LOCATIONS.forEach { (name, coords) ->
                val (lat, lon) = coords
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$OPEN_WEATHER_API_KEY&units=imperial"
                try {
                    val req = Request.Builder().url(url).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val temp = json.getJSONObject("main").getDouble("temp")
                        val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
                        map[name] = Pair(temp, desc)
                    }
                } catch (e: Exception) {
                    map[name] = Pair(Double.NaN, "error")
                }
            }
            results = map
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Weather Snapshot", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (results == null) {
            CircularProgressIndicator()
        } else {
            results!!.forEach { (city, pair) ->
                val (temp, desc) = pair
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), elevation = 6.dp) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(city, fontWeight = FontWeight.SemiBold)
                            Text(desc.replaceFirstChar { it.uppercase() })
                        }
                        Text(if (temp.isNaN()) "--°F" else "${temp.toInt()}°F", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(context)) }
    var editText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Notes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a new note...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (editText.isNotBlank()) {
                    notes = notes + editText.trim()
                    saveNotes(context, notes)
                    editText = ""
                }
            }) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            itemsIndexed(notes) { idx, note ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), elevation = 4.dp) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note)
                        }
                        IconButton(onClick = {
                            notes = notes.toMutableList().also { it.removeAt(idx) }
                            saveNotes(context, notes)
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "delete") // TODO: replace with trash icon
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var threshold by rememberSaveable { mutableStateOf(90f) }
    var selectedCloud by rememberSaveable { mutableStateOf("None") }
    val cloudOptions = listOf("None", "Google Drive", "OneDrive", "Dropbox")
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Text("Temperature alert threshold: ${threshold.toInt()}°F")
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 50f..120f, steps = 7)

        Spacer(Modifier.height(12.dp))

        Text("Cloud backup")
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedCloud)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                cloudOptions.forEach { option ->
                    DropdownMenuItem(onClick = {
                        selectedCloud = option
                        expanded = false
                    }) {
                        Text(option)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Connected accounts and backup schedule will be implemented with OAuth flows.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { /* TODO: run a backup now */ }) {
            Text("Backup Now (placeholder)")
        }
    }
}

fun loadNotes(context: Context): List<String> {
    return try {
        val f = File(context.filesDir, "notes.json")
        if (!f.exists()) {
            f.writeText("[]")
            return emptyList()
        }
        val txt = f.readText()
        val arr = JSONObject("{\"a\":$txt}").getJSONArray("a")
        List(arr.length()) { i -> arr.getString(i) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveNotes(context: Context, notes: List<String>) {
    try {
        val f = File(context.filesDir, "notes.json")
        val j = org.json.JSONArray(notes)
        f.writeText(j.toString())
    } catch (_: Exception) {}
}

fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}
