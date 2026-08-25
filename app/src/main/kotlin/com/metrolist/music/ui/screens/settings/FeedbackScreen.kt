package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val FEEDBACK_EMAIL = "yangcyb7@gmail.com"
private const val FEEDBACK_WHATSAPP = "17866124534"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(navController: NavController) {
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.feedback_title)) },
            navigationIcon = {
                IconButton(onClick = navController::backToMain, onLongClick = {}) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )

        Text(
            text = stringResource(R.string.feedback_subtitle),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feedback_hint)) },
            minLines = 5,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val body = buildFeedbackBody(context, message)
                val intent =
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:$FEEDBACK_EMAIL".toUri()
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_email_subject))
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.feedback_send_email)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feedback_send_email))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val body = buildFeedbackBody(context, message)
                val encoded = URLEncoder.encode(body, StandardCharsets.UTF_8.toString())
                val uri = "https://wa.me/$FEEDBACK_WHATSAPP?text=$encoded".toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feedback_send_whatsapp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun buildFeedbackBody(
    context: android.content.Context,
    userMessage: String,
): String {
    val device = "${Build.MANUFACTURER} ${Build.MODEL}"
    val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    return buildString {
        appendLine(userMessage.ifBlank { context.getString(R.string.feedback_no_message) })
        appendLine()
        appendLine("---")
        appendLine("App: YT Music $appVersion")
        appendLine("Device: $device")
        appendLine("OS: $androidVersion")
        appendLine("Package: ${BuildConfig.APPLICATION_ID}")
    }
}
