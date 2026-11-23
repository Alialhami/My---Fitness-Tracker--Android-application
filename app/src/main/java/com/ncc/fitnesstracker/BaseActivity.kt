package com.ncc.fitnesstracker

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Inherited by all screens: shared session checks, insets, controls and navigation. */
open class BaseActivity : AppCompatActivity() {
    protected lateinit var store: Store
    protected lateinit var api: Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = Store(this); api = Api(this)
    }
    override fun setContentView(view: View?) { super.setContentView(view); view?.let { applyInsets(it) } }
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyInsets(findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
    }
    private fun applyInsets(view: View) {
        val l = view.paddingLeft; val t = view.paddingTop; val r = view.paddingRight; val b = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom); insets
        }
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    protected fun requireSession(): Boolean {
        if (store.token.isNotEmpty()) return true
        startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); return false
    }
    protected fun apiFailure(error: ApiError) {
        if (error.status == 401) { store.token = ""; requireSession() }
        else message(error.message)
    }
    protected fun label(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(24,46,39))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }
    protected fun button(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; cornerRadius = dp(16); minHeight = dp(52)
        setOnClickListener { action() }
    }
    protected fun field(parent: LinearLayout, title: String, input: Int = android.text.InputType.TYPE_CLASS_TEXT): TextInputEditText {
        val wrapper = TextInputLayout(this).apply {
            hint = title; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat(),dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) }
        }
        val edit = TextInputEditText(wrapper.context).apply { inputType=input; setSingleLine(true); minHeight=dp(56) }
        wrapper.addView(edit); parent.addView(wrapper); return edit
    }
}
