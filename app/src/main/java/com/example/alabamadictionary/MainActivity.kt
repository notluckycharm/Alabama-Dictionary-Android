package com.example.alabamadictionary

import android.content.Context
import android.icu.text.CaseMap.Title
import android.media.Image
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role.Companion.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.alabamadictionary.ui.theme.AlabamaDictionaryTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.collections.listOf
import kotlin.text.Regex


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

fun stateMachineSort(string: String, a: DictionaryEntry, b: DictionaryEntry): Int {
    val search = removeAccents(string.lowercase())
    val aLemma = removeAccents(a.lemma.lowercase())
    val bLemma = removeAccents(b.lemma.lowercase())
    val aDefinition = removeAccents(a.definition.lowercase())
    val bDefinition = removeAccents(b.definition.lowercase())

    if (aLemma == search || aDefinition == search) return -1
    if (bLemma == search || bDefinition == search) return 1

    val aPrefixLength = longestCommonPrefixLength(search, aLemma)
    val bPrefixLength = longestCommonPrefixLength(search, bLemma)
    if (aPrefixLength != bPrefixLength) return bPrefixLength - aPrefixLength

    val aContains = isValidSubstringMatch(aLemma, search)
    val bContains = isValidSubstringMatch(bLemma, search)
    if (aContains && !bContains) return -1
    if (bContains && !aContains) return 1

    return aLemma.compareTo(bLemma)
}

fun longestCommonPrefixLength(search: String, target: String): Int {
    val common = search.zip(target).takeWhile { it.first == it.second }
    return common.size
}

fun isValidSubstringMatch(string: String, substring: String): Boolean {
    return string.contains(substring)
}

fun dictSort(searchText: String) {
    val string = if (!reMode.value) removeAccents(searchText.lowercase()) else searchText

    var filteredEntries = if (!reMode.value) {
        allEntries.filter { entry ->
            removeAccents(entry.lemma.lowercase()).contains(string) ||
                    entry.definition.lowercase().contains(string)
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

    shownMax.value = filteredEntries.size
    searchResults.value = filteredEntries.subList(
        shown.value,
        (shown.value + minOf(50, shownMax.value - shown.value)).coerceAtMost(filteredEntries.size)
    )
}

val reMode = mutableStateOf(false)
val limitAudio = mutableStateOf(false)
var allEntries = listOf<DictionaryEntry>()
val shown = mutableIntStateOf(0)
val shownMax = mutableIntStateOf(0)
val searchResults = mutableStateOf(listOf<DictionaryEntry>())
val textSize = mutableStateOf(18)
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
            AlabamaDictionaryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column() {
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
                        Row() {
                            SearchField()
                        }
                        Results()
                    }
                }
            }
        }
    }
}

@Composable
fun Results() {
    LazyColumn {
        items(searchResults.value) { entry ->
            Result(entry)
        }
    }
}

@Composable
fun Result(entry: DictionaryEntry) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = entry.lemma,
                fontSize = (textSize.value + 1).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom=4.dp)
            )
            if (entry.wordClass != "nan") {
                Text(
                    text = "[" + entry.wordClass + "]",
                    fontSize = textSize.value.sp,
                    modifier = Modifier.padding(bottom=4.dp)
                )
            }
        }
        if (entry.derivation != "nan") {
            Text(
                text = entry.derivation,
                fontSize = textSize.value.sp,
            )
        }
        Text(
            text = entry.definition,
            fontSize = textSize.value.sp,
        )
        if (entry.principalPart != "nan") {
            val pPs = entry.principalPart.split(", ")
            val persons = listOf("second person singular", "first person plural", "second person plural")
            val principalPartsWithPersons = pPs.zip(persons)
            principalPartsWithPersons.forEach { (part, person) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = part,
                        fontSize = textSize.value.sp,
                        modifier = Modifier.padding(start = 15.dp)
                    )
                    Text(
                        text = person,
                        fontSize = textSize.value.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun SearchField() {
    var text by remember { mutableStateOf("") } // State variable for the TextField
    Column() {
        Row() {
            TextField(
                value = text,
                onValueChange = { newText -> text = newText },
                label = { Text("Enter Alabama or English word") },
                singleLine = true, // Ensures input is single line
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Go // Maps the Enter/Return key to "Go"
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        println("User pressed Enter/Go with text: $text") // Debug print
                        dictSort(text) // Trigger search or desired action
                    }
                ),
                modifier = Modifier
                    .padding(16.dp)
            )
        }
        CharRow(onCharClick = { char -> text += char })
    }
}

@Composable
fun CharRow(onCharClick: (Char) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlabamaDictionaryTheme {
    }
}