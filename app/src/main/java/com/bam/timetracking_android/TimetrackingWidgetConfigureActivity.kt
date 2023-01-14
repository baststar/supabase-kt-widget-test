package com.bam.timetracking_android

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import com.bam.timetracking_android.databinding.TimetrackingWidgetConfigureBinding

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

import kotlinx.coroutines.launch

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.*
import io.github.jan.supabase.gotrue.providers.Discord
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.runBlocking

/**
 * The configuration screen for the [TimetrackingWidget] AppWidget.
 */
class TimetrackingWidgetConfigureActivity : ComponentActivity() {



    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetText: EditText
    private var onClickListener = View.OnClickListener {
        val context = this@TimetrackingWidgetConfigureActivity

        // When the button is clicked, store the string locally
        val widgetText = appWidgetText.text.toString()
        saveTitlePref(context, appWidgetId, widgetText)

        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
    private lateinit var binding: TimetrackingWidgetConfigureBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val supabaseClient = createSupabaseClient(
            "",
            ""
        ) {
            install(GoTrue) {
                scheme = "supabase"
                host = "login"
            }
        }

        supabaseClient.handleDeeplinks(intent)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = TimetrackingWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetText = binding.appwidgetText as EditText
        binding.addButton.setOnClickListener(onClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        appWidgetText.setText(loadTitlePref(this@TimetrackingWidgetConfigureActivity, appWidgetId))

        val status = supabaseClient.gotrue.sessionStatus.value

        var isAuthenticated = false
        var email: String = "";
        if (status is SessionStatus.Authenticated) {
            isAuthenticated = true;
            if (status.session.user != null) {
                email = (status as SessionStatus.Authenticated).session.user?.email.toString()
            }
        }

        fun sendMagicLink() = runBlocking {
            launch {
                supabaseClient.gotrue.sendOtpTo(Email, false) {
                    email = "test@email.com"
                }
            }
        }

        setContent {
            SimpleScreen(isAuthenticated, email, onClose = {
                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            }, onSendMagicLink = {
                sendMagicLink()
            })
        }

    }

}

private const val PREFS_NAME = "com.bam.timetracking_android.TimetrackingWidget"
private const val PREF_PREFIX_KEY = "appwidget_"

// Write the prefix to the SharedPreferences object for this widget
internal fun saveTitlePref(context: Context, appWidgetId: Int, text: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
    prefs.putString(PREF_PREFIX_KEY + appWidgetId, text)
    prefs.apply()
}

// Read the prefix from the SharedPreferences object for this widget.
// If there is no preference saved, get the default from a resource
internal fun loadTitlePref(context: Context, appWidgetId: Int): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0)
    val titleValue = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
    return titleValue ?: context.getString(R.string.appwidget_text)
}

internal fun deleteTitlePref(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
    prefs.remove(PREF_PREFIX_KEY + appWidgetId)
    prefs.apply()
}

class SimpleScreenParameter {
    val isAuthenticated: Boolean = true;
    val onClose: () -> Unit = TODO()
    val onVerify: () -> Unit = TODO()
    val onLoginWithDiscord: () -> Unit = TODO()
    val onSendMagicLink: () -> Unit = TODO()
}

class SimpleScreenParameterProvider: PreviewParameterProvider<SimpleScreenParameter> {
    override val values = sequenceOf(SimpleScreenParameter())
}

@Composable
fun SimpleScreen(isAuthenticated: Boolean,
                 email: String,
                 onClose: () -> Unit,
                 onSendMagicLink: () -> Unit) {

    MaterialTheme {
        if (isAuthenticated) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()) {
                Text("Logged in as $email")
                Button(onClick = onClose) {
                    Text("OK")
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                Column {
                    TextField(email, { email = it }, placeholder = { Text("Email") })
                    TextField(
                        password,
                        { password = it },
                        placeholder = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(onClick = onSendMagicLink) {
                        Text("Send OTP")
                    }
                    //
                }
            }
        }
    }
}
