package com.ncc.fitnesstracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject

class AuthActivity : BaseActivity() {
    private var registering = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (store.token.isNotEmpty()) { openDashboard(); return }
        registering=savedInstanceState?.getBoolean("registering") ?: false
        draw()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("registering",registering);super.onSaveInstanceState(outState) }
    private fun openDashboard() { startActivity(Intent(this,MainActivity::class.java));finish() }
    private fun draw() {
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(36),dp(28),dp(24)) }
        val scroll=ScrollView(this).apply { isFillViewport=true;addView(content) };setContentView(scroll)
        content.addView(label("FITNESS TRACKER",14f,true).apply { setTextColor(getColor(R.color.green));letterSpacing=.15f })
        content.addView(label(if(registering) "Your next chapter\nstarts here." else "Small steps.\nStronger you.",36f,true))
        content.addView(label(if(registering) "Create an account to keep your workouts and goals together." else "Log your movement, follow your progress, and make every session count.",16f))
        content.addView(label(if(registering) "Create your account" else "Welcome back",23f,true))
        val name = if(registering) field(content,"Full name") else null
        name?.id=R.id.auth_name
        val email=field(content,"Email address",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply { id=R.id.auth_email }
        val password=field(content,"Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply { id=R.id.auth_password }
        (password.parent.parent as? TextInputLayout)?.endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val error=label("",14f).apply { setTextColor(0xffa32929.toInt()) };content.addView(error)
        val submit=button(if(registering) "Create account" else "Sign in") {}
        submit.setOnClickListener {
            val e=email.text.toString().trim();val p=password.text.toString()
            when {
                registering && name!!.text.toString().trim().isEmpty() -> name.error="Enter your name"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> email.error="Enter a valid email"
                p.toByteArray().size !in 8..72 -> password.error="Use 8 to 72 bytes (at least 8 ASCII characters)"
                else -> {
                    submit.isEnabled=false;error.text="Connecting…"
                    val data=JSONObject().put("email",e).put("password",p)
                    if(registering)data.put("name",name!!.text.toString().trim())
                    api.call(if(registering) "register" else "login",data,{store.signedIn(it);openDashboard()}, {
                        submit.isEnabled=true;error.text=it.message
                    })
                }
            }
        }
        content.addView(submit,LinearLayout.LayoutParams(-1,dp(58)))
        content.addView(button(if(registering) "Already registered? Sign in" else "New here? Create an account") { registering=!registering;draw() })
        content.addView(button("Server connection") { configureServer() })
        content.addView(label("Your workout history belongs to your account. Location is optional and requested only when you attach it.",12f))
    }
    private fun configureServer() {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),0) }
        val address=field(form,"Server base URL",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply { setText(store.server) }
        form.addView(label("Android emulator: http://10.0.2.2:8088\nUSB device with adb reverse: http://127.0.0.1:8088\nHosted server: use HTTPS.",13f))
        val dialog=MaterialAlertDialogBuilder(this).setTitle("Server connection").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val raw=address.text.toString().trim().trimEnd('/');val uri=Uri.parse(raw)
            val local=BuildConfig.DEBUG && uri.scheme=="http" && uri.host in listOf("10.0.2.2","127.0.0.1","localhost")
            if(uri.host.isNullOrBlank() || (uri.scheme!="https" && !local) || uri.query!=null || uri.fragment!=null || uri.userInfo!=null) address.error="Use HTTPS, or a supported local debug address"
            else {store.server=raw;dialog.dismiss();message("Server address saved")}
        }};dialog.show()
    }
}
