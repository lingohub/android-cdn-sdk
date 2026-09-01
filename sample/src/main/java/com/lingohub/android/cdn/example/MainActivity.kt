package com.lingohub.android.cdn.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.example.ui.theme.LingoHubSampleTheme
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

/** Languages of the Wanderly demo project. Only en and de ship in the APK — es,
 *  fr, and ja can only appear once the SDK has downloaded them over the air. */
private val demoLocales = listOf("en", "de", "es", "fr", "ja").map(Locale::forLanguageTag)

class MainActivity : BaseActivity() {
    // Initialized from the SDK so the state survives recreate() after updates
    private var currentLocale by mutableStateOf(LingoHub.getCurrentLocale())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LingoHubSampleTheme {
                WanderlyDemoScreen(
                    currentLocale = currentLocale,
                    onLocaleSelected = { locale ->
                        LingoHub.setLocale(locale)
                        currentLocale = locale
                    }
                )
            }
        }
    }
}

@Composable
private fun WanderlyDemoScreen(
    currentLocale: Locale,
    onLocaleSelected: (Locale) -> Unit,
    modifier: Modifier = Modifier
) {
    var travelers by remember { mutableIntStateOf(1) }
    val departure = remember(currentLocale) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale)
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.time)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.trips_greeting, "Alex"),
            style = MaterialTheme.typography.titleMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.trips_summary, "Lisbon", departure))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { if (travelers > 1) travelers-- }) { Text("−") }
                    Text(text = pluralStringResource(R.plurals.trips_members, travelers, travelers))
                    TextButton(onClick = { travelers++ }) { Text("+") }
                }

                Button(onClick = { /* demo only */ }) {
                    Text(text = stringResource(R.string.trips_create))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            demoLocales.forEach { locale ->
                val selected = locale.language == currentLocale.language
                TextButton(onClick = { onLocaleSelected(locale) }) {
                    Text(
                        text = locale.language.uppercase(),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Button(onClick = { LingoHub.update() }) {
            Text(text = stringResource(R.string.action_check_updates))
        }
    }
}
