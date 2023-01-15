package com.bam.timetracking_android

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import com.bam.timetracking_android.databinding.TimetrackingWidgetConfigureBinding

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

import kotlinx.coroutines.launch

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.*
import io.github.jan.supabase.gotrue.providers.Discord
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.ktor.client.plugins.*

/**
 * The configuration screen for the [TimetrackingWidget] AppWidget.
 */
class TimetrackingWidgetConfigureActivity : ComponentActivity() {

    val supabaseClient = createSupabaseClient(
        "",
        ""
    ) {
        install(GoTrue) {
            scheme = "supabase"
            host = "login"
        }
    }

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

        setContent {

            MaterialTheme {

                val status by supabaseClient.gotrue.sessionStatus.collectAsState()
                val scope = rememberCoroutineScope()

                if (status is SessionStatus.Authenticated) {

                    fun logout() {
                        scope.launch {
                            try {
                                supabaseClient.gotrue.invalidateSession()
                            } catch (e: RestException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestTimeoutException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestException) {
                                Log.e("TIMETRACKING", e.toString())
                            }
                        }
                    }

                    fun close() {
                        val resultValue = Intent()
                        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(RESULT_OK, resultValue)
                        finish()
                    }

                    val authenticationScreenParameter = AuthenticationScreenParameter(
                        (status as SessionStatus.Authenticated).session.user?.email ?: "",
                        ::close,
                        ::logout
                    )

                    AuthenticatedScreen(authenticationScreenParameter)

                } else {

                    fun sendMagicLink(email: String) {
                        scope.launch {
                            try {
                                supabaseClient.gotrue.sendOtpTo(Email, false, "supabase://login") {
                                    this.email = email
                                }
                            } catch (e: RestException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestTimeoutException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestException) {
                                Log.e("TIMETRACKING", e.toString())
                            }
                        }
                    }

                    fun loginWithDiscord() {
                        scope.launch {
                            try {
                                supabaseClient.gotrue.loginWith(Discord)
                            } catch (e: RestException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestTimeoutException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestException) {
                                Log.e("TIMETRACKING", e.toString())
                            }
                        }
                    }

                    fun verifyEmail(email: String, onetimepassword: String): Unit {
                        scope.launch {
                            try {
                                supabaseClient.gotrue.verifyEmailOtp(
                                    OtpType.Email.MAGIC_LINK,
                                    email,
                                    onetimepassword
                                )
                            } catch (e: RestException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestTimeoutException) {
                                Log.e("TIMETRACKING", e.toString())
                            } catch (e: HttpRequestException) {
                                Log.e("TIMETRACKING", e.toString())
                            }
                        }
                    }

                    val loginParameter =
                        LoginScreenParameter(::verifyEmail, ::loginWithDiscord, ::sendMagicLink);
                    LoginScreen(loginParameter)
                }
            }
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

class AuthenticationScreenParameterProvider() :
    PreviewParameterProvider<AuthenticationScreenParameter> {
    override val values = sequenceOf(AuthenticationScreenParameter(
        email = "max@mustermann.de",
        onClose = {},
        onLogout = {}
    ))
}

class AuthenticationScreenParameter(
    val email: String,
    val onClose: () -> Unit,
    val onLogout: () -> Unit
) {}

@Preview
@Composable
fun AuthenticatedScreen(@PreviewParameter(AuthenticationScreenParameterProvider::class) authenticationScreenParameter: AuthenticationScreenParameter) {

    Box(
        contentAlignment = Alignment.Center, modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {

        Column {
            Text("Logged in as ${authenticationScreenParameter.email}", color = Color.White)
            Button(
                onClick = authenticationScreenParameter.onClose,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Add Widget")
            }
            Button(
                onClick = authenticationScreenParameter.onLogout,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Logout")
            }
        }
    }
}

class LoginScreenParameter(
    val onVerify: (email: String, onetimepassword: String) -> Unit,
    val onLoginWithDiscord: () -> Unit,
    val onSendMagicLink: (email: String) -> Unit
) {}

class LoginScreenParameterProvider() : PreviewParameterProvider<LoginScreenParameter> {
    override val values = sequenceOf(LoginScreenParameter(
        onVerify = { _: String, _: String -> { TODO() } },
        onSendMagicLink = { _: String -> { TODO() } },
        onLoginWithDiscord = {}
    ))
}

@Preview()
@Composable
fun LoginScreen(@PreviewParameter(LoginScreenParameterProvider::class) loginScreenParameter: LoginScreenParameter) {

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {

        var email by remember { mutableStateOf("") }
        var onetimepassword by remember { mutableStateOf("") }

        Column {
            TextField(
                email, { email = it },
                placeholder = { Text("E-Mail", color = Color.White) },
                colors = TextFieldDefaults.textFieldColors(textColor = Color.White)
            )
            TextField(
                onetimepassword, { onetimepassword = it },
                placeholder = { Text("One Time Password", color = Color.White) },
                colors = TextFieldDefaults.textFieldColors(textColor = Color.White)
            )
            Button(
                onClick = { loginScreenParameter.onSendMagicLink.invoke(email) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Send OTP to E-Mail")
            }
            Button(
                onClick = { loginScreenParameter.onVerify(email, onetimepassword) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Login")
            }
            Button(
                onClick = loginScreenParameter.onLoginWithDiscord,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Login with Discord")
            }
        }
    }
}
