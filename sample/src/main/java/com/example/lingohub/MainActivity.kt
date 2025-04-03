package com.example.lingohub

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lingohub.ui.theme.MyApplicationTheme
import com.helpers.core.Lingohub
import java.util.Locale


class MainActivity : BaseActivity() {
    private var currentLocale by mutableStateOf(Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                    LingohubDemoContent(
                        currentLocale = currentLocale,
                        onLanguageChange = { newLocale ->
                            // Only update Lingohub locale
                            Lingohub.setLocale(newLocale)
                            currentLocale = newLocale
                        }
                    )
            }
        }
    }

}

@Composable
private fun LingohubDemoContent(
    modifier: Modifier = Modifier,
    currentLocale: Locale,
    onLanguageChange: (Locale) -> Unit
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.title))


        Button(
            onClick = {
                val newLocale = if (currentLocale == Locale.ENGLISH) Locale.GERMAN else Locale.ENGLISH
                onLanguageChange(newLocale)
            }
        ) {
            Text(text = if (currentLocale == Locale.ENGLISH) "Switch to German" else "Switch to English")
        }


        Button(
            onClick = { Lingohub.update() }
        ) {
            Text(text = "Check for updates")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LingohubDemoPreview() {
    MyApplicationTheme {
        LingohubDemoContent(
            currentLocale = Locale.ENGLISH,
            onLanguageChange = {}
        )

    }
}