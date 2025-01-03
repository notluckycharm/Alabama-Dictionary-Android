@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.alabamadictionary

import androidx.compose.ui.text.font.FontStyle
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.alabamadictionary.ui.theme.AlabamaDictionaryTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.collections.listOf
import kotlin.text.Regex
import kotlin.text.slice


@Serializable
data class DictionaryEntry(
    val lemma: String,
    val definition: String,
    @SerialName("class")
    val wordClass: String? = null,
    val principalPart: String,
    val derivation: String,
    val notes: String? = null,
    val relatedTerms: List<String>? = null,
    val audio: List<String>,
    val sentences: List<Sentence> = emptyList(),
)

@Serializable
data class Sentence(
    @SerialName("alabama-example") val akz: String? = "",
    @SerialName("english-translation") val en: String? = ""
)

@Serializable
data class DictionaryData(
    val words: List<DictionaryEntry>
)

private val json = Json { ignoreUnknownKeys = true }

fun loadJsonFromAssets(context: Context, fileName: String): DictionaryData {
    val jsonString = context.assets.open(fileName).use { inputStream ->
        inputStream.readBytes().decodeToString()
    }
    return Json { ignoreUnknownKeys = true }.decodeFromString(jsonString)
}

fun reMatch(pattern: String, text: String): Boolean {
    val regex = pattern
        .replace("C", "[bcdfhklɬmnpstwy]")
        .replace("V", "[aeoiáóéíàòìè]")
    return Regex(regex).containsMatchIn(text)
}
fun longerOfTwoStrs(string: String, a: String, b: String): Int {
    val aPrefixLength = longestCommonPrefixLength(string, a)
    val bPrefixLength = longestCommonPrefixLength(string, b)
    if (aPrefixLength != bPrefixLength) return bPrefixLength - aPrefixLength

    val aContains = isValidSubstringMatch(a, string)
    val bContains = isValidSubstringMatch(b, string)
    if (aContains && !bContains) return -1
    if (bContains && !aContains) return 1

    else return a.compareTo(b)
}

fun stateMachineSort(string: String, a: DictionaryEntry, b: DictionaryEntry): Int {
    val search = removeAccents(string.lowercase())
    val aLemma = removeAccents(a.lemma.lowercase())
    val bLemma = removeAccents(b.lemma.lowercase())
    val aDefinition = removeAccents(a.definition.lowercase())
    val bDefinition = removeAccents(b.definition.lowercase())

    if (aLemma == search || aDefinition == search) return -1
    if (bLemma == search || bDefinition == search) return 1

    if (search.contains("#en")) {
        return longerOfTwoStrs(stripped(search), aDefinition, bDefinition) // Pass arguments positionally
    }
    if (search.contains("#akz") || search.contains("alabama")) {
        return longerOfTwoStrs(stripped(search), aLemma, bLemma)
    }

    if (!aLemma.contains(search)) {
        return longerOfTwoStrs(stripped(search), aLemma, bLemma)
    }
    else {
        return longerOfTwoStrs(stripped(search), aLemma, bLemma) // Pass arguments positionally
    }
}

fun longestCommonPrefixLength(search: String, target: String): Int {
    val common = search.zip(target).takeWhile { it.first == it.second }
    return common.size
}

fun isValidSubstringMatch(string: String, substring: String): Boolean {
    return string.contains(substring)
}

fun stripped(string: String): String {
    return string.replace("#english", "")
        .replace(" ", "")
        .replace("#en", "")
        .replace("#akz", "")
        .replace("#alabama", "")
}
fun dictSort(searchText: String) {
    val string = if (!reMode.value) removeAccents(searchText.lowercase()) else searchText

    var filteredEntries = if (!reMode.value) {
        allEntries.filter { entry ->
            removeAccents(stripped(entry.lemma.lowercase())).contains(stripped(string)) ||
                    stripped(entry.definition.lowercase()).contains(stripped(string))
        }
    } else {
        allEntries.filter { entry ->
            reMatch(string, entry.lemma)
        }
    }

    if (limitAudio.value) {
        filteredEntries = filteredEntries.filter { it.audio.isNotEmpty() }
    }

    filteredEntries = filteredEntries.sortedWith { a, b ->
        stateMachineSort(string, a, b)
    }

    shownMax.intValue = filteredEntries.size
    searchResults.value = filteredEntries.subList(
        shown.intValue,
        (shown.intValue + minOf(50, shownMax.intValue - shown.intValue)).coerceAtMost(filteredEntries.size)
    )
}

var reMode =  mutableStateOf(false)
val limitAudio = mutableStateOf(false)
var allEntries = listOf<DictionaryEntry>()
val shown = mutableIntStateOf(0)
val shownMax = mutableIntStateOf(0)
val searchResults = mutableStateOf(listOf<DictionaryEntry>())
val textSize = mutableIntStateOf(18)

fun updateResults(count: Int) {
    shown.intValue = when {
        shown.intValue + count < 0 -> 0
        shown.intValue + count > shownMax.intValue -> shownMax.intValue
        else -> shown.intValue + count
    }
}

fun removeAccents(input: String): String {
    return input
        .replace("à", "a")
        .replace("á", "a")
        .replace("ó", "o")
        .replace("ò", "o")
        .replace("í", "i")
        .replace("ì", "i")
        .replace("\u2081", "")
        .replace("\u2082", "")
        .replace("\u2083", "")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val loadedEntries = loadJsonFromAssets(this, "dict.json").words
        allEntries = loadedEntries
        setContent {
            var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val navController = rememberNavController()
            AlabamaDictionaryTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ModalNavigationDrawer(
                        drawerContent = {
                            SettingsDrawer()
                        },
                        drawerState = drawerState,
                        gesturesEnabled = true,
                    ) {
                        Column{
                            Row(
                                modifier = Modifier
                                    .padding(top = 20.dp)
                            ) {
                                val imageModifier = Modifier
                                    .size(100.dp) // Adjust size here (e.g., 100.dp)
                                    .padding(10.dp)
                                Image(
                                    painter = painterResource(id = R.drawable.alabamacoushatta),
                                    contentDescription = "Alabama-Coushatta logo",
                                    modifier = imageModifier // Apply the imageModifier here
                                )
                                Text(
                                    text = "Alabama Dictionary",
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            NavHost(navController = navController, startDestination = "home") {
                                composable("home") { MainContent(drawerState, navController) }
                                composable("entry/{lemma}") { backStackEntry ->
                                    val lemma = backStackEntry.arguments?.getString("lemma")
                                    val entry = searchResults.value.find { it.lemma == lemma }
                                    entry?.let {
                                        Details(it, navController)
                                    }
                                }                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(drawerState: DrawerState, navController: NavHostController) {
    Column {
        SearchField(drawerState)
        Results(navController)
    }
}

@Composable
fun Results(navController: NavHostController) {
    LazyColumn {
        items(searchResults.value) { entry ->
            Result(entry, navController)
        }
    }
}

@Composable
fun Result(entry: DictionaryEntry, navController: NavHostController) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 15.dp)
            .clickable {
                navController.navigate("entry/${entry.lemma}")
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                    text = entry.lemma,
                    fontSize = (textSize.intValue + 1).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom=4.dp)

                )
            if (entry.wordClass != "nan") {
                Text(
                    text = "[" + entry.wordClass + "]",
                    fontSize = textSize.intValue.sp,
                    modifier = Modifier.padding(bottom=4.dp)
                )
            }
        }
        if (entry.derivation != "nan") {
            Text(
                text = entry.derivation,
                fontSize = textSize.intValue.sp,
            )
        }
        Text(
            text = entry.definition,
            fontSize = textSize.intValue.sp,
        )
        if (entry.principalPart != "nan") {
            val pPs = entry.principalPart.split(", ")
            val persons = listOf("second person singular", "first person plural", "second person plural")
            val principalPartsWithPersons = pPs.zip(persons)
            principalPartsWithPersons.forEach { (part, person) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically // Optional: Aligns items vertically
                ) {
                    Text(
                        text = part,
                        fontSize = textSize.intValue.sp,
                        modifier = Modifier
                            .weight(1f) // Occupies flexible space
                            .padding(start = 15.dp)
                    )
                    Text(
                        text = person,
                        fontSize = textSize.intValue.sp,
                        modifier = Modifier
                            .weight(1f) // Occupies flexible space
                            .padding(start = 15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchField(drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") } // State variable for the TextField
    Column {
        Row(verticalAlignment = Alignment.CenterVertically ) {
            TextField(
                value = text,
                onValueChange = { newText -> text = newText },
                label = { Text(
                    text = "Enter Alabama or English word",
                    fontSize = (textSize.intValue - 3).sp,
                    ) },
                singleLine = true, // Ensures input is single line
                textStyle = TextStyle(
                    fontSize = textSize.intValue.sp // Adjusts the size of the inputted text
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Go // Maps the Enter/Return key to "Go"
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        dictSort(text) // Trigger search or desired action
                    }
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 300.dp) // Set a maximum width for the TextField
            )
            Icon(
                imageVector = Icons.Default.Settings, // Settings icon from Material Icons
                contentDescription = "Settings",
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            if (drawerState.isClosed) {
                                drawerState.open()
                            } else {
                                drawerState.close()
                            }
                        }
                    } // Handle click action
                    .padding(8.dp)
            )
        }
        CharRow(onCharClick = { char -> text += char })
    }
}

@Composable
fun CharRow(onCharClick: (Char) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        items("ɬⁿáàíìóò".toList()) { char ->
            Box(
                modifier = Modifier
                    .background(Color.LightGray)
                    .clickable { onCharClick(char) }
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(char.toString())
            }
        }
    }
}

@Composable
fun SettingsDrawer() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp) // Adjust the width as needed
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp), // Ensures the column takes up the available space
            verticalArrangement = Arrangement.Top, // Clusters children to the top
        ) {
            Text(
                text = "Search Settings",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(25.dp),
            )
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f) // Allows the text to occupy flexible space
                ) {
                    Text(
                        text = "Regular Expression Mode",
                        fontSize = textSize.intValue.sp,
                        modifier = Modifier.wrapContentWidth(Alignment.Start) // Wrap text to the left
                    )
                }
                Switch(
                    checked = reMode.value,
                    onCheckedChange = {
                        reMode.value = it
                    },
                    modifier = Modifier.padding(start = 8.dp) // Adds padding to the toggle
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f) // Allows the text to occupy flexible space
                ) {
                    Text(
                        text = "Limit to entries with audio",
                        fontSize = textSize.intValue.sp,
                        modifier = Modifier.wrapContentWidth(Alignment.Start) // Wrap text to the left
                    )
                }
                Switch(
                    checked = limitAudio.value,
                    onCheckedChange = {
                        limitAudio.value = it
                    },
                    modifier = Modifier.padding(start = 8.dp) // Adds padding to the toggle
                )
            }
            Text(
                text = "Adjust Text Size",
                fontSize = textSize.intValue.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Slider(
                value = textSize.intValue.toFloat(),
                onValueChange = { newValue ->
                    textSize.intValue = newValue.toInt()
                },
                valueRange = 12f..30f,
                steps = 17
            )
        }
    }
}

@Composable
fun Details(entry: DictionaryEntry, navController: NavHostController) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = 20.dp),
        topBar = {
            TopAppBar(
                title = { Text("Back") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // Handles back navigation
                        Icon(
                            imageVector = Icons.Default.ArrowBack, // Back arrow icon
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column( modifier = Modifier.padding(innerPadding)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                ,
                horizontalArrangement = Arrangement.Center

            ){
                Text(
                    text = entry.lemma,
                    fontWeight = FontWeight.Bold,
                    fontSize = (textSize.intValue + 4).sp,
                )
            }
            val negative = allEntries.find { it.definition == "Negative form of " + entry.lemma }
            negative?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Negative stem: " + negative.lemma,
                        fontStyle = FontStyle.Italic,
                        fontSize = textSize.intValue.sp,
                    )
                }
            }
            val defs = entry.definition.split(";")
            defs.forEachIndexed { index, def ->
                if (def.take(3) == "to " && entry.wordClass != "nan" && !entry.wordClass?.contains(";")!!) {
                    Text(
                        text = entry.wordClass + " Verb",
                        color = Color.Gray,
                        fontSize = textSize.intValue.sp,
                    )
                }
                Text(
                    text = "${index + 1}. $def", // Add numbering
                    fontSize = textSize.intValue.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                val lastDef = defs.last()
                if (lastDef != def) {
                    HorizontalDivider(
                    )
                }
            }
            if (entry.principalPart != "nan" && entry.wordClass?.contains("-LI") == true) {
                Box(
                    modifier = Modifier.background(Color.LightGray).fillMaxWidth()
                ) {
                    Text(
                       text = "Inflectional Stems",
                        modifier = Modifier.padding(15.dp),
                        fontSize = textSize.intValue.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Text(entry.lemma + "li", fontSize = textSize.intValue.sp, modifier=Modifier.padding(end = 5.dp))
                    Text("first person singular", fontSize = textSize.intValue.sp, fontStyle = FontStyle.Italic)
                }
                val pPs = entry.principalPart.split(", ")
                val persons = listOf("second person singular", "first person plural", "second person plural")
                val principalPartsWithPersons = pPs.zip(persons)
                principalPartsWithPersons.forEach { (part, person) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ){
                        Text(part, fontSize = textSize.intValue.sp, modifier=Modifier.padding(end = 5.dp))
                        Text(person, fontSize = textSize.intValue.sp, fontStyle = FontStyle.Italic)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Text("ho" + entry.lemma, fontSize = textSize.intValue.sp, modifier=Modifier.padding(end = 5.dp))
                    Text("third person plural", fontSize = textSize.intValue.sp, fontStyle = FontStyle.Italic)
                }
            }
            if (entry.sentences.isNotEmpty()) {
                Box(
                    modifier = Modifier.background(Color.LightGray).fillMaxWidth()
                ) {
                    Text(
                        text = "Example Sentences",
                        modifier = Modifier.padding(15.dp),
                        fontSize = textSize.intValue.sp
                    )
                }
                    Column(modifier = Modifier.padding(10.dp)) {
                        entry.sentences.forEach { sentence ->
                            sentence.akz?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = textSize.intValue.sp ) }
                            sentence.en?.let { Text(it, fontSize = textSize.intValue.sp) }
                        }
                    }

            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlabamaDictionaryTheme {
    }
}