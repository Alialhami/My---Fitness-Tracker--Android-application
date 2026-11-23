# Appendix A Source Code

This appendix lists the implementation source. The complete project also contains the build wrapper, setup instructions and tests. Files are listed by project-relative path. Device screenshots are to be captured separately after the application is run.

## app/build.gradle.kts

```kotlin
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.ncc.fitnesstracker"
    compileSdk = 35
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.ncc.fitnesstracker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}
```

## app/src/debug/res/xml/network_security_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <application android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" android:label="@string/app_name"
        android:icon="@drawable/ic_fitness" android:theme="@style/Theme.FitnessTracker"
        android:networkSecurityConfig="@xml/network_security_config" android:supportsRtl="true">
        <activity android:name=".AddWorkoutActivity" android:exported="false" android:windowSoftInputMode="adjustResize" />
        <activity android:name=".MainActivity" android:exported="false" />
        <activity android:name=".AuthActivity" android:exported="true" android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## app/src/main/java/com/ncc/fitnesstracker/AddWorkoutActivity.kt

```kotlin
package com.ncc.fitnesstracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class AddWorkoutActivity:BaseActivity() {
    private var lat:Double?=null;private var lon:Double?=null
    private var date=LocalDate.now()
    private var clientId=UUID.randomUUID().toString()
    private var source:JSONObject?=null
    private lateinit var locationLabel:TextView
    private var locationToken:CancellationTokenSource?=null
    private val locationPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {result->
        if(result.values.any {it})captureLocation() else locationLabel.text="Permission declined. You can still save your workout without a location."
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);if(!requireSession())return
        source=intent.getStringExtra("workout")?.let {JSONObject(it)}
        clientId=savedInstanceState?.getString("clientId") ?: source?.optString("client_id") ?: clientId
        date=LocalDate.parse(savedInstanceState?.getString("date") ?: source?.optString("date") ?: date.toString())
        if(savedInstanceState!=null) {
            if(savedInstanceState.containsKey("latitude")){lat=savedInstanceState.getDouble("latitude");lon=savedInstanceState.getDouble("longitude")}
        } else if(source!=null && !source!!.isNull("latitude")){lat=source!!.getDouble("latitude");lon=source!!.getDouble("longitude")}
        setContentView(R.layout.activity_add_workout)
        val form=findViewById<LinearLayout>(R.id.workoutForm)
        form.addView(button("Back to your activity"){finish()})
        form.addView(label(if(source==null) "Make it count." else "Update your workout",30f,true))
        form.addView(label("Record the details of your session.",16f))
        form.addView(label("Activity",14f,true))
        val types=listOf("Running","Cycling","Weightlifting","Walking")
        val type=Spinner(this).apply {id=R.id.workout_activity;adapter=ArrayAdapter(this@AddWorkoutActivity,android.R.layout.simple_spinner_dropdown_item,types)}
        form.addView(type,LinearLayout.LayoutParams(-1,dp(56)))
        val duration=field(form,"Duration (minutes)",InputType.TYPE_CLASS_NUMBER).apply {id=R.id.workout_duration}
        val distance=field(form,"Distance (km)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {id=R.id.workout_distance}
        val weight=field(form,"Weight per repetition (kg)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {id=R.id.workout_weight}
        val sets=field(form,"Sets",InputType.TYPE_CLASS_NUMBER).apply {id=R.id.workout_sets}
        val reps=field(form,"Repetitions per set",InputType.TYPE_CLASS_NUMBER).apply {id=R.id.workout_reps}
        fun wrapper(v:View)=v.parent.parent as View
        fun fields(){val strength=type.selectedItem.toString()=="Weightlifting";wrapper(distance).visibility=if(strength)View.GONE else View.VISIBLE;listOf(weight,sets,reps).forEach{wrapper(it).visibility=if(strength)View.VISIBLE else View.GONE}}
        type.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){fields()}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        }
        source?.let {row->
            type.setSelection(types.indexOf(row.getString("type")))
            mapOf("duration" to duration,"distance" to distance,"weight" to weight,"sets" to sets,"reps" to reps).forEach{(key,edit)->if(!row.isNull(key))edit.setText(row.optString(key))}
        };fields()
        val dateButton=button("Workout date: $date") {}
        dateButton.setOnClickListener {DatePickerDialog(this,{_,y,m,d->date=LocalDate.of(y,m+1,d);dateButton.text="Workout date: $date"},date.year,date.monthValue-1,date.dayOfMonth).apply {datePicker.maxDate=System.currentTimeMillis()}.show()}
        form.addView(dateButton)
        locationLabel=label(if(lat==null) "No location attached. This is optional." else "Location attached: $lat, $lon",13f);form.addView(locationLabel)
        form.addView(button("Attach current location") {
            val coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
            if(coarse)captureLocation() else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        })
        form.addView(button("Remove location") {lat=null;lon=null;locationLabel.text="No location attached. This is optional."})
        val notes=field(form,"Notes (optional)").apply {id=R.id.workout_notes;setText(source?.optString("notes")?:"");setSingleLine(false);maxLines=4;filters=arrayOf(android.text.InputFilter.LengthFilter(500))}
        val save=button(if(source==null) "Save workout" else "Save changes") {}
        save.setOnClickListener {
            fun number(edit:TextInputEditText,min:Double,max:Double,integer:Boolean=false):Double? {
                val value=edit.text.toString().toDoubleOrNull()
                if(value==null || !value.isFinite() || value<min || value>max || (integer && value%1!=0.0)){edit.error="Enter ${if(integer) "a whole number" else "a number"} from $min to $max";edit.requestFocus();return null}
                return value
            }
            if(notes.text.toString().trim().toByteArray(Charsets.UTF_8).size>500){notes.error="Shorten your notes to 500 UTF-8 bytes";return@setOnClickListener}
            val mins=number(duration,1.0,1440.0,true)?.toInt() ?: return@setOnClickListener
            val row=JSONObject().put("id",source?.optLong("id")?:0).put("client_id",clientId).put("type",type.selectedItem.toString()).put("duration",mins).put("date",date.toString()).put("notes",notes.text.toString().trim())
            if(date>LocalDate.now()){message("Choose today or a past date");return@setOnClickListener}
            if(type.selectedItem.toString()=="Weightlifting") {
                row.put("weight",number(weight,0.01,99999.0)?:return@setOnClickListener)
                row.put("sets",number(sets,1.0,1000.0,true)?.toInt()?:return@setOnClickListener)
                row.put("reps",number(reps,1.0,1000.0,true)?.toInt()?:return@setOnClickListener)
            } else row.put("distance",number(distance,0.01,99999.0)?:return@setOnClickListener)
            row.put("latitude",lat?:JSONObject.NULL).put("longitude",lon?:JSONObject.NULL)
            save.isEnabled=false
            if(source==null) {store.queue(row);message("Workout saved on this device. Syncing when connected.");finish()}
            else api.call("save_workout",row,{message("Workout updated");finish()},{save.isEnabled=true;apiFailure(it)})
        }
        form.addView(save,LinearLayout.LayoutParams(-1,dp(58)))
        form.addView(label("New workouts can be recorded offline after signing in. Editing and deleting saved workouts requires a connection.",12f))
    }
    @Suppress("MissingPermission")
    private fun captureLocation() {
        locationToken?.cancel();locationToken=CancellationTokenSource()
        locationLabel.text="Finding your location…"
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,locationToken!!.token)
            .addOnSuccessListener {location->
                if(location==null)locationLabel.text="Location unavailable. Check device location settings, then retry."
                else {lat=location.latitude;lon=location.longitude;locationLabel.text="Location attached: ${String.format(java.util.Locale.US,"%.5f, %.5f",lat,lon)} (accuracy ${location.accuracy.toInt()} m)"}
            }.addOnFailureListener {locationLabel.text="Couldn't get a location. You can save without it."}
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("date",date.toString());outState.putString("clientId",clientId)
        lat?.let{outState.putDouble("latitude",it);outState.putDouble("longitude",lon!!)}
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy(){locationToken?.cancel();super.onDestroy()}
}
```

## app/src/main/java/com/ncc/fitnesstracker/AuthActivity.kt

```kotlin
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
```

## app/src/main/java/com/ncc/fitnesstracker/BaseActivity.kt

```kotlin
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
```

## app/src/main/java/com/ncc/fitnesstracker/Data.kt

```kotlin
package com.ncc.fitnesstracker

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("fitness_private", Context.MODE_PRIVATE)
    var server: String
        get() = prefs.getString("server", "http://10.0.2.2:8088")!!
        set(value) { prefs.edit().putString("server", value.trimEnd('/')).apply() }
    var token: String
        get() = prefs.getString("token", "")!!
        set(value) { prefs.edit().putString("token", value).apply() }
    val name get() = prefs.getString("name", "Athlete")!!
    private val scope get() = "${server}_${prefs.getLong("user", 0)}"
    fun signedIn(result: JSONObject) {
        prefs.edit().putString("token", result.getString("token"))
            .putLong("user", result.getJSONObject("user").getLong("id"))
            .putString("name", result.getJSONObject("user").getString("name")).apply()
    }
    fun read(key: String): JSONArray = JSONArray(prefs.getString("${scope}_$key", "[]"))
    fun write(key: String, value: JSONArray) { prefs.edit().putString("${scope}_$key", value.toString()).commit() }
    fun queue(workout: JSONObject) {
        val items = read("pending"); items.put(workout); write("pending", items)
    }
    fun removePending(clientId: String) {
        val next = JSONArray(); val old = read("pending")
        for (i in 0 until old.length()) if (old.getJSONObject(i).getString("client_id") != clientId) next.put(old.getJSONObject(i))
        write("pending", next)
    }
    fun history(): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        val pending = read("pending"); val cached = read("workouts")
        val seen = mutableSetOf<String>()
        for (array in listOf(pending, cached)) for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (seen.add(row.getString("client_id"))) rows.add(row)
        }
        return rows.sortedWith(compareByDescending<JSONObject> { it.optString("date") }.thenByDescending { it.optLong("id") })
    }
}

data class ApiError(val message: String, val status: Int) {
    val temporary get() = status == 0 || status >= 500
}
class Api(context: Context) {
    private val store = Store(context)
    private val queue = Volley.newRequestQueue(context.applicationContext)
    fun call(action: String, body: JSONObject? = null, success: (JSONObject) -> Unit, failure: (ApiError) -> Unit) {
        val request = object : JsonObjectRequest(
            if (body == null) Request.Method.GET else Request.Method.POST,
            "${store.server}/index.php?action=$action", body, success,
            { error ->
                val status = error.networkResponse?.statusCode ?: 0
                val message = try { JSONObject(String(error.networkResponse.data, Charsets.UTF_8)).optString("error", "Request failed.") }
                    catch (_: Exception) { if (status == 0) "Cannot reach the server. Check your connection and server address." else "Server error ($status). Try again." }
                failure(ApiError(message, status))
            }) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf("Authorization" to "Bearer ${store.token}")
        }
        request.retryPolicy = DefaultRetryPolicy(8000, 0, 1f)
        request.setShouldCache(false)
        queue.add(request)
    }
}

fun JSONObject.workout(): Workout {
    val id = optLong("id"); val duration = getInt("duration"); val date = getString("date")
    return when (getString("type")) {
        "Running" -> RunningWorkout(id, duration, date, optDouble("distance", 0.0))
        "Cycling" -> CyclingWorkout(id, duration, date, optDouble("distance", 0.0))
        "Walking" -> WalkingWorkout(id, duration, date, optDouble("distance", 0.0))
        else -> WeightliftingWorkout(id, duration, date, optDouble("weight", 0.0), optInt("sets"), optInt("reps"))
    }
}
```

## app/src/main/java/com/ncc/fitnesstracker/MainActivity.kt

```kotlin
package com.ncc.fitnesstracker

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

class MainActivity : BaseActivity() {
    private var busy=false
    private var selected=0
    private var callback: ConnectivityManager.NetworkCallback?=null
    private val rows get()=store.history()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);if(!requireSession())return
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.greeting).text="Let's move, ${store.name.substringBefore(' ')}."
        findViewById<RecyclerView>(R.id.recyclerView).layoutManager=LinearLayoutManager(this)
        val tabs=findViewById<TabLayout>(R.id.tabs)
        listOf("History","Goals","Progress").forEach {tabs.addTab(tabs.newTab().setText(it))}
        tabs.addOnTabSelectedListener(object:TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab:TabLayout.Tab) {selected=tab.position;draw()}
            override fun onTabUnselected(tab:TabLayout.Tab) {}
            override fun onTabReselected(tab:TabLayout.Tab) {}
        })
        selected=savedInstanceState?.getInt("tab") ?: 0;tabs.getTabAt(selected)?.select()
        findViewById<View>(R.id.fabAdd).setOnClickListener {if(selected==1) newGoal() else startActivity(Intent(this,AddWorkoutActivity::class.java))}
        findViewById<View>(R.id.refresh).setOnClickListener {sync()}
        findViewById<View>(R.id.signOut).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Sign out?")
                .setMessage(if(store.read("pending").length()>0) "Unsynced workouts will stay on this device for this account. Sign in with the same account to sync them." else "Your saved workouts will be here when you return.")
                .setNegativeButton("Cancel",null).setPositiveButton("Sign out") {_,_->
                    api.call("logout",JSONObject(),{},{});store.token="";requireSession()
                }.show()
        }
    }
    override fun onResume() {
        super.onResume();if(store.token.isEmpty())return
        draw();sync()
        callback=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){runOnUiThread {if(!isFinishing)sync()}}}
        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(callback!!)
    }
    override fun onPause() {
        callback?.let {(getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)}
        callback=null;super.onPause()
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putInt("tab",selected);super.onSaveInstanceState(outState)}
    private fun status(text:String){if(!isDestroyed)findViewById<TextView>(R.id.status).text=text}
    private fun sync() {
        if(busy || store.token.isEmpty() || isFinishing)return
        busy=true;status("Syncing your activity…")
        fun uploadNext() {
            if(isFinishing){busy=false;return}
            val pending=store.read("pending")
            if(pending.length()>0) {
                val row=pending.getJSONObject(0)
                api.call("save_workout",row,{
                    row.put("id",it.getLong("id"))
                    val cached=store.read("workouts");cached.put(row);store.write("workouts",cached)
                    store.removePending(row.getString("client_id"));uploadNext()
                }, {error->busy=false;status(if(error.temporary) "Offline · ${pending.length()} workout(s) waiting to sync" else error.message);draw();if(error.status==401)apiFailure(error)})
                return
            }
            api.call("workouts",success={
                store.write("workouts",it.getJSONArray("workouts"));draw()
                api.call("goals",success={goals->store.write("goals",goals.getJSONArray("goals"));busy=false;status("All changes saved · Tap Sync to refresh");draw()},failure={error->busy=false;status(error.message);if(error.status==401)apiFailure(error)})
            },failure={error->busy=false;status(if(error.temporary) "Offline · Showing workouts saved on this device" else error.message);if(error.status==401)apiFailure(error)})
        }
        uploadNext()
    }
    private fun draw() {
        if(isDestroyed || isFinishing)return
        val history=rows;val today=LocalDate.now();val week=history.filter {it.getString("date")>=today.minusDays(6).toString() && it.getString("date")<=today.toString()}
        findViewById<TextView>(R.id.weeklyMinutes).text="${week.sumOf {it.getInt("duration")}} min"
        val km=week.sumOf {it.optDouble("distance",0.0)}
        findViewById<TextView>(R.id.weeklyDetail).text="${week.size} workouts  ·  ${String.format(Locale.US,"%.1f",km)} km recorded"
        findViewById<TextView>(R.id.sectionTitle).text=listOf("Recent workouts","Your fitness goals","Your progress")[selected]
        findViewById<RecyclerView>(R.id.recyclerView).apply {visibility=if(selected==0)View.VISIBLE else View.GONE;adapter=WorkoutAdapter(history){showWorkout(it)}}
        findViewById<View>(R.id.empty).visibility=if(selected==0 && history.isEmpty())View.VISIBLE else View.GONE
        findViewById<View>(R.id.detailsScroll).visibility=if(selected==0)View.GONE else View.VISIBLE
        findViewById<View>(R.id.fabAdd).apply {visibility=if(selected==2)View.GONE else View.VISIBLE;contentDescription=if(selected==1)"Add goal" else "Add workout"}
        val details=findViewById<LinearLayout>(R.id.details);details.removeAllViews()
        if(selected==1) drawGoals(details)
        if(selected==2) drawProgress(details)
    }
    private fun showWorkout(row:JSONObject) {
        val location=if(row.isNull("latitude")) "No location attached" else "Location: ${row.optString("latitude")}, ${row.optString("longitude")}"
        val info="${row.getString("date")} · ${row.getInt("duration")} minutes\n${row.workout().statistics()}\n$location\n${row.optString("notes")}"
        val builder=MaterialAlertDialogBuilder(this).setTitle(row.getString("type")).setMessage(info).setNegativeButton("Close",null)
        if(row.optLong("id")>0)builder.setPositiveButton("Edit") {_,_->startActivity(Intent(this,AddWorkoutActivity::class.java).putExtra("workout",row.toString()))}
        builder.setNeutralButton("Delete") {_,_->
            MaterialAlertDialogBuilder(this).setTitle("Delete this workout?").setMessage("This removes the workout and updates your goal progress.")
                .setNegativeButton("Keep",null).setPositiveButton("Delete") {_,_->
                    if(row.optLong("id")==0L){store.removePending(row.getString("client_id"));draw()}
                    else api.call("delete_workout",JSONObject().put("id",row.getLong("id")),{sync()},{apiFailure(it)})
                }.show()
        }.show()
    }
    private fun drawGoals(parent:LinearLayout) {
        val goals=store.read("goals")
        if(goals.length()==0)parent.addView(label("Make space for your next milestone. Tap + to set a goal in activity minutes.",17f))
        for(i in 0 until goals.length()) {
            val g=goals.getJSONObject(i);val target=g.getInt("target_minutes")
            val done=rows.filter {it.getString("type")==g.getString("type") && it.getString("date")>=g.getString("start_date") && it.getString("date")<=g.getString("end_date")}.sumOf {it.getInt("duration")}
            parent.addView(label("${g.getString("type")} · $target minutes",20f,true))
            parent.addView(label("${g.getString("start_date")} to ${g.getString("end_date")}",13f))
            parent.addView(LinearProgressIndicator(this).apply {max=target;progress=done.coerceAtMost(target);trackThickness=dp(8);setIndicatorColor(getColor(R.color.green))},LinearLayout.LayoutParams(-1,dp(16)))
            val ended=g.getString("end_date")<LocalDate.now().toString()
            parent.addView(label("$done / $target min · ${if(done>=target) "Goal achieved!" else if(ended) "Period ended" else "${target-done} min to go"}",15f,true))
            parent.addView(button("Remove goal") {
                MaterialAlertDialogBuilder(this).setTitle("Remove this goal?").setNegativeButton("Cancel",null).setPositiveButton("Remove") {_,_->api.call("delete_goal",JSONObject().put("id",g.getLong("id")),{sync()},{apiFailure(it)})}.show()
            })
        }
    }
    private fun drawProgress(parent:LinearLayout) {
        parent.addView(label("Minutes over the last 7 days",17f,true))
        val today=LocalDate.now();val totals=(6 downTo 0).map {offset->val date=today.minusDays(offset.toLong()).toString();rows.filter {it.getString("date")==date}.sumOf {it.getInt("duration")}}
        parent.addView(WeeklyChart(this,totals),LinearLayout.LayoutParams(-1,dp(170)))
        val current=totals.sum();val previous=rows.filter {it.getString("date")>=today.minusDays(13).toString() && it.getString("date")<=today.minusDays(7).toString()}.sumOf {it.getInt("duration")}
        val insight=if(previous==0) "You've recorded $current minutes in the last 7 days. Keep logging to see your weekly trend." else "${kotlin.math.abs(current-previous)} minutes ${if(current>=previous) "more" else "less"} than the previous 7 days ($previous min)."
        parent.addView(label(insight,15f))
        parent.addView(label("All-time activity breakdown",21f,true))
        for(type in listOf("Running","Cycling","Weightlifting","Walking")) {
            val records=rows.filter {it.getString("type")==type};val minutes=records.sumOf {it.getInt("duration")}
            parent.addView(label("$type · ${records.size} sessions",18f,true))
            val sum=records.sumOf {it.optDouble("distance",0.0)}
            val stats=if(type=="Weightlifting") {
                val volume=records.sumOf { (it.workout() as WeightliftingWorkout).volume() }
                "${String.format(Locale.US,"%.0f",volume)} kg total training volume"
            } else {
                val extra=if(type=="Cycling") "${String.format(Locale.US,"%.1f",if(minutes>0)sum*60/minutes else 0.0)} km/h average" else "${String.format(Locale.US,"%.1f",if(sum>0)minutes/sum else 0.0)} min/km average"
                "${String.format(Locale.US,"%.1f",sum)} km · $extra"
            }
            parent.addView(label("$minutes min · $stats",14f))
        }
        parent.addView(label("Statistics include workouts pending sync on this device. Distance and weight are entered by you; attached GPS coordinates mark a location, not a tracked route.",12f))
    }
    private fun newGoal() {
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),0)}
        content.addView(label("Activity",14f,true))
        val type=Spinner(this).apply {adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Running","Cycling","Weightlifting","Walking"))}
        content.addView(type,LinearLayout.LayoutParams(-1,dp(52)))
        val target=field(content,"Target minutes",InputType.TYPE_CLASS_NUMBER)
        var end=LocalDate.now().plusDays(6)
        val dateButton=button("Until $end") {}
        dateButton.setOnClickListener {DatePickerDialog(this,{_,y,m,d->end=LocalDate.of(y,m+1,d);dateButton.text="Until $end"},end.year,end.monthValue-1,end.dayOfMonth).show()}
        content.addView(dateButton);content.addView(label("Starts today. Progress uses workouts matching the activity and date range.",13f))
        val dialog=MaterialAlertDialogBuilder(this).setTitle("Set a fitness goal").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Save goal",null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val minutes=target.text.toString().toIntOrNull()
            if(minutes==null || minutes !in 1..100000){target.error="Enter 1 to 100000 minutes";return@setOnClickListener}
            if(end<LocalDate.now()){message("End date must be today or later");return@setOnClickListener}
            dialog.getButton(-1).isEnabled=false
            api.call("save_goal",JSONObject().put("type",type.selectedItem.toString()).put("target_minutes",minutes).put("start_date",LocalDate.now().toString()).put("end_date",end.toString()),{
                dialog.dismiss();sync()
            },{dialog.getButton(-1).isEnabled=true;apiFailure(it)})
        }};dialog.show()
    }
}
```

## app/src/main/java/com/ncc/fitnesstracker/WeeklyChart.kt

```kotlin
package com.ncc.fitnesstracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A native chart backed by the same recorded minutes used in the accessible description. */
class WeeklyChart @JvmOverloads constructor(context:Context,private val values:List<Int> = List(7){0}):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {contentDescription="Daily minutes, oldest to newest: ${values.joinToString()}. Total ${values.sum()} minutes.";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);val density=resources.displayMetrics.density;val bottom=height-28*density
        val step=width/7f;val max=(values.maxOrNull()?:1).coerceAtLeast(1)
        values.forEachIndexed {i,value->
            val h=(bottom-30*density)*value/max;val x=step*i+step*.2f
            paint.color=if(i==6)Color.rgb(23,107,81) else Color.rgb(188,220,165)
            canvas.drawRoundRect(x,bottom-h, x+step*.6f,bottom,5*density,5*density,paint)
            paint.color=Color.rgb(24,46,39);paint.textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,11f,resources.displayMetrics);paint.textAlign=Paint.Align.CENTER
            canvas.drawText(value.toString(),x+step*.3f,bottom-h-6*density,paint)
            val day=LocalDate.now().minusDays((6-i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.getDefault())
            canvas.drawText(day,x+step*.3f,height-6*density,paint)
        }
    }
}
```

## app/src/main/java/com/ncc/fitnesstracker/WorkoutAdapter.kt

```kotlin
package com.ncc.fitnesstracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/** Framework inheritance supplies recycling; polymorphic models supply activity statistics. */
class WorkoutAdapter(private val rows: List<JSONObject>, private val clicked: (JSONObject) -> Unit) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {
    class WorkoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.tvType)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val stats: TextView = view.findViewById(R.id.tvStats)
        val date: TextView = view.findViewById(R.id.tvDate)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = WorkoutViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_workout,parent,false))
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val row=rows[position];val model: Workout=row.workout()
        holder.type.text=model.type;holder.duration.text="${model.duration} min"
        holder.stats.text=model.statistics()
        holder.date.text="${model.date}  ·  ${if(row.optLong("id")==0L) "Pending sync" else "Saved"}${if(!row.isNull("latitude")) "  ·  Location attached" else ""}"
        holder.itemView.setOnClickListener {clicked(row)}
    }
}
```

## app/src/main/java/com/ncc/fitnesstracker/WorkoutModels.kt

```kotlin
package com.ncc.fitnesstracker

import java.util.Locale

/** Common identity and timing are inherited; each activity defines its own statistics. */
abstract class Workout(val id: Long, val duration: Int, val date: String) {
    abstract val type: String
    abstract fun statistics(): String
    protected fun fmt(value: Double) = String.format(Locale.US, "%.1f", value)
}
class RunningWorkout(id: Long, duration: Int, date: String, val distance: Double) : Workout(id, duration, date) {
    override val type = "Running"
    fun pace(): Double = if (distance > 0) duration / distance else 0.0
    override fun statistics() = "${fmt(distance)} km  /  ${fmt(pace())} min/km"
}
class CyclingWorkout(id: Long, duration: Int, date: String, val distance: Double) : Workout(id, duration, date) {
    override val type = "Cycling"
    fun speed(): Double = if (duration > 0) distance * 60 / duration else 0.0
    override fun statistics() = "${fmt(distance)} km  /  ${fmt(speed())} km/h"
}
class WalkingWorkout(id: Long, duration: Int, date: String, val distance: Double) : Workout(id, duration, date) {
    override val type = "Walking"
    override fun statistics() = "${fmt(distance)} km  /  ${fmt(if (distance > 0) duration / distance else 0.0)} min/km"
}
class WeightliftingWorkout(id: Long, duration: Int, date: String, val weight: Double, val sets: Int, val reps: Int) : Workout(id, duration, date) {
    override val type = "Weightlifting"
    fun volume(): Double = weight * sets * reps
    override fun statistics() = "$sets x $reps at ${fmt(weight)} kg  /  ${fmt(volume())} kg volume"
}
```

## app/src/main/res/drawable/ic_add.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#182E27" android:pathData="M11,5h2v6h6v2h-6v6h-2v-6h-6v-2h6z" /></vector>
```

## app/src/main/res/drawable/ic_fitness.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#176B51" android:pathData="M0,0h48v48h-48z" />
    <path android:strokeColor="#D5F5AD" android:strokeWidth="3" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="@android:color/transparent" android:pathData="M8,25h8l5,-12l7,23l5,-11h7" />
</vector>
```

## app/src/main/res/layout/activity_add_workout.xml

```xml
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true">
    <LinearLayout android:id="@+id/workoutForm" android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="24dp" android:orientation="vertical" />
</ScrollView>
```

## app/src/main/res/layout/activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:paddingHorizontal="20dp">
    <LinearLayout android:id="@+id/header" android:orientation="vertical" android:layout_width="0dp" android:layout_height="wrap_content" app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical">
            <TextView android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="@string/brand" android:textColor="@color/green" android:letterSpacing="0.12" android:textStyle="bold" android:textSize="12sp" />
            <com.google.android.material.button.MaterialButton android:id="@+id/signOut" style="@style/Widget.MaterialComponents.Button.TextButton" android:layout_width="wrap_content" android:layout_height="48dp" android:text="@string/sign_out" android:textAllCaps="false" />
        </LinearLayout>
        <TextView android:id="@+id/greeting" android:layout_width="match_parent" android:layout_height="wrap_content" android:textSize="28sp" android:textColor="@color/ink" android:textStyle="bold" android:text="@string/your_activity" />
        <TextView android:id="@+id/status" android:layout_width="match_parent" android:layout_height="wrap_content" android:paddingVertical="8dp" android:text="@string/ready_status" android:textColor="@color/muted" android:textSize="13sp" android:accessibilityLiveRegion="polite" />
        <com.google.android.material.card.MaterialCardView android:layout_width="match_parent" android:layout_height="wrap_content" app:cardBackgroundColor="@color/green" app:cardCornerRadius="24dp" app:cardElevation="0dp" android:layout_marginVertical="8dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="22dp" android:orientation="vertical">
                <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/last_seven_days" android:textColor="#D5F5AD" android:textSize="12sp" android:letterSpacing="0.08" />
                <TextView android:id="@+id/weeklyMinutes" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/zero_minutes" android:textSize="38sp" android:textStyle="bold" android:textColor="#FFFFFF" />
                <TextView android:id="@+id/weeklyDetail" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/start_today" android:textSize="14sp" android:textColor="#ECF6EE" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
        <com.google.android.material.tabs.TabLayout android:id="@+id/tabs" android:layout_width="match_parent" android:layout_height="52dp" app:tabTextColor="@color/muted" app:tabSelectedTextColor="@color/green" app:tabIndicatorColor="@color/green" app:tabMode="fixed" />
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical">
            <TextView android:id="@+id/sectionTitle" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="@string/recent_workouts" android:textSize="20sp" android:textStyle="bold" android:textColor="@color/ink" />
            <com.google.android.material.button.MaterialButton android:id="@+id/refresh" style="@style/Widget.MaterialComponents.Button.TextButton" android:layout_width="wrap_content" android:layout_height="48dp" android:text="@string/sync" android:textAllCaps="false" />
        </LinearLayout>
    </LinearLayout>
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerView" android:layout_width="0dp" android:layout_height="0dp" android:clipToPadding="false" android:paddingBottom="88dp" app:layout_constraintTop_toBottomOf="@id/header" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" />
    <ScrollView android:id="@+id/detailsScroll" android:layout_width="0dp" android:layout_height="0dp" android:visibility="gone" android:fillViewport="true" app:layout_constraintTop_toBottomOf="@id/header" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent">
        <LinearLayout android:id="@+id/details" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:paddingBottom="88dp" />
    </ScrollView>
    <TextView android:id="@+id/empty" android:layout_width="0dp" android:layout_height="wrap_content" android:gravity="center" android:padding="24dp" android:text="@string/empty_history" android:textSize="17sp" android:textColor="@color/muted" app:layout_constraintTop_toBottomOf="@id/header" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" />
    <com.google.android.material.floatingactionbutton.FloatingActionButton android:id="@+id/fabAdd" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_margin="16dp" android:contentDescription="@string/add_workout" app:srcCompat="@drawable/ic_add" app:backgroundTint="#BCE983" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintEnd_toEndOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

## app/src/main/res/layout/item_workout.xml

```xml
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="12dp" app:cardCornerRadius="18dp" app:cardElevation="0dp" app:strokeColor="#DFE6DD" app:strokeWidth="1dp" android:clickable="true" android:focusable="true">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="18dp" android:orientation="vertical">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content">
            <TextView android:id="@+id/tvType" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:textSize="18sp" android:textStyle="bold" android:textColor="@color/ink" />
            <TextView android:id="@+id/tvDuration" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="16sp" android:textStyle="bold" android:textColor="@color/green" />
        </LinearLayout>
        <TextView android:id="@+id/tvStats" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:textSize="14sp" android:textColor="@color/muted" />
        <TextView android:id="@+id/tvDate" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:textSize="12sp" android:textColor="@color/muted" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

## app/src/main/res/values-v27/styles.xml

```xml
<resources>
    <style name="Theme.FitnessTracker" parent="Theme.FitnessTracker.Base">
        <item name="android:windowLightNavigationBar">true</item>
    </style>
</resources>
```

## app/src/main/res/values/ids.xml

```xml
<resources>
    <item name="auth_name" type="id" />
    <item name="auth_email" type="id" />
    <item name="auth_password" type="id" />
    <item name="workout_activity" type="id" />
    <item name="workout_duration" type="id" />
    <item name="workout_distance" type="id" />
    <item name="workout_weight" type="id" />
    <item name="workout_sets" type="id" />
    <item name="workout_reps" type="id" />
    <item name="workout_notes" type="id" />
</resources>
```

## app/src/main/res/values/strings.xml

```xml
<resources>
    <string name="app_name">Fitness Tracker</string>
    <string name="brand">FITNESS TRACKER</string>
    <string name="sign_out">Sign out</string>
    <string name="your_activity">Your activity</string>
    <string name="ready_status">Ready for your next session</string>
    <string name="last_seven_days">YOUR LAST 7 DAYS</string>
    <string name="zero_minutes">0 min</string>
    <string name="start_today">Start with one workout today.</string>
    <string name="recent_workouts">Recent workouts</string>
    <string name="sync">Sync</string>
    <string name="empty_history">Your story starts here.\nTap + to record your first workout.</string>
    <string name="add_workout">Add workout</string>
</resources>
```

## app/src/main/res/values/styles.xml

```xml
<resources>
    <color name="green">#176B51</color><color name="ink">#182E27</color>
    <color name="paper">#F6F8F3</color><color name="muted">#53675F</color>
    <style name="Theme.FitnessTracker.Base" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/green</item><item name="colorPrimaryVariant">#104D3A</item>
        <item name="colorSecondary">#BCE983</item><item name="colorOnSecondary">@color/ink</item>
        <item name="android:fontFamily">sans</item><item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">@color/paper</item><item name="android:navigationBarColor">@color/paper</item>
        <item name="android:windowBackground">@color/paper</item><item name="android:colorAccent">@color/green</item>
    </style>
    <style name="Theme.FitnessTracker" parent="Theme.FitnessTracker.Base" />
</resources>
```

## app/src/main/res/xml/data_extraction_rules.xml

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
        <exclude domain="root" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
        <exclude domain="root" path="." />
    </device-transfer>
</data-extraction-rules>
```

## app/src/main/res/xml/network_security_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config><base-config cleartextTrafficPermitted="false" /></network-security-config>
```

## app/src/test/java/com/ncc/fitnesstracker/WorkoutTest.kt

```kotlin
package com.ncc.fitnesstracker

import org.junit.Assert.*
import org.junit.Test

class WorkoutTest {
    @Test fun runningPaceUsesMinutesPerKilometre(){assertEquals(6.0,RunningWorkout(1,30,"2026-10-25",5.0).pace(),0.001)}
    @Test fun cyclingSpeedConvertsMinutesToHours(){assertEquals(24.0,CyclingWorkout(2,30,"2026-10-25",12.0).speed(),0.001)}
    @Test fun liftingVolumeIncludesSetsAndRepetitions(){assertEquals(1200.0,WeightliftingWorkout(3,40,"2026-10-25",40.0,3,10).volume(),0.001)}
    @Test fun emptyDistanceCannotDivideByZero(){assertEquals(0.0,RunningWorkout(1,10,"2026-10-25",0.0).pace(),0.0)}
    @Test fun polymorphicStatisticsMatchEachActivity(){
        val records:List<Workout> = listOf(RunningWorkout(1,30,"2026-10-25",5.0),CyclingWorkout(2,30,"2026-10-25",12.0),WeightliftingWorkout(3,40,"2026-10-25",40.0,3,10))
        assertTrue(records[0].statistics().contains("6.0 min/km"));assertTrue(records[1].statistics().contains("24.0 km/h"));assertTrue(records[2].statistics().contains("1200.0 kg volume"))
    }
}
```

## backend/bootstrap.php

```php
<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
function respond(array $data, int $status = 200): never {
    http_response_code($status);
    echo json_encode($data, JSON_THROW_ON_ERROR);
    exit;
}
function fail(string $message, int $status = 422): never { respond(['error' => $message], $status); }
set_exception_handler(function(Throwable $e): void {
    error_log((string)$e);
    fail('The server could not complete this request. Please try again.', 500);
});
function db(): PDO {
    static $pdo;
    if (!$pdo) {
        $file = __DIR__ . '/config.local.php';
        if (!is_file($file)) fail('Server database configuration is missing.', 503);
        $c = require $file;
        $pdo = new PDO("mysql:host={$c['host']};port={$c['port']};dbname={$c['database']};charset=utf8mb4",
            $c['user'], $c['password'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
        $pdo->exec("SET time_zone = '+00:00'");
    }
    return $pdo;
}
function input(): array {
    if ((int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 16384) fail('Request too large.', 413);
    try { $data = json_decode(file_get_contents('php://input'), true, 32, JSON_THROW_ON_ERROR); }
    catch(JsonException $e) { fail('Send a valid JSON object.'); }
    if (!is_array($data)) fail('Send a JSON object.');
    return $data;
}
function textValue(array $d, string $key, int $max, bool $required=true): string {
    if (isset($d[$key]) && !is_string($d[$key])) fail("Invalid $key.");
    $v = trim($d[$key] ?? '');
    if (($required && $v === '') || strlen($v) > $max) fail("Please enter a valid $key (up to $max characters).");
    return $v;
}
function numberValue(array $d, string $key, float $min, float $max, bool $integer=false): float|int {
    $v = $d[$key] ?? null;
    if (!is_numeric($v) || !is_finite((float)$v) || $v < $min || $v > $max || ($integer && floor((float)$v) != $v))
        fail("$key must be " . ($integer ? 'a whole number ' : '') . "between $min and $max.");
    return $integer ? (int)$v : (float)$v;
}
function dateValue(array $d, string $key): string {
    $v=textValue($d,$key,10);
    $date=DateTimeImmutable::createFromFormat('!Y-m-d',$v);
    if (!$date || $date->format('Y-m-d') !== $v || $v < '2000-01-01' || $v > '2100-12-31') fail("Invalid $key.");
    return $v;
}
function activityType(array $d): string {
    $v = textValue($d,'type',20);
    if (!in_array($v,['Running','Cycling','Weightlifting','Walking'],true)) fail('Choose a supported activity.');
    return $v;
}
function authenticatedUser(): int {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer ([a-f0-9]{64})$/D', $header, $m)) fail('Please sign in again.',401);
    $q=db()->prepare('SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>UTC_TIMESTAMP()');
    $q->execute([hash('sha256',$m[1])]);
    $id=$q->fetchColumn();
    if (!$id) fail('Your session has expired. Please sign in again.',401);
    return (int)$id;
}
function issueSession(int $id, string $name): never {
    $token=bin2hex(random_bytes(32));
    db()->prepare('DELETE FROM sessions WHERE expires_at<UTC_TIMESTAMP()')->execute();
    db()->prepare('INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 DAY))')
        ->execute([hash('sha256',$token),$id]);
    respond(['token'=>$token,'user'=>['id'=>$id,'name'=>$name]]);
}
```

## backend/config.example.php

```php
<?php
// Copy to config.local.php beside this file. Keep credentials out of source control.
return [
    'host' => '127.0.0.1',
    'port' => 3306,
    'database' => 'fitness_db',
    'user' => 'fitness_app',
    'password' => 'REPLACE_WITH_YOUR_DATABASE_PASSWORD'
];
```

## backend/fitness_db.sql

```sql
CREATE DATABASE IF NOT EXISTS fitness_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fitness_db;
CREATE TABLE IF NOT EXISTS users (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sessions (
 token_hash CHAR(64) PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 expires_at DATETIME NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX session_expiry (expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS workouts (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 client_id CHAR(36) NOT NULL,
 type ENUM('Running','Cycling','Weightlifting','Walking') NOT NULL,
 duration INT NOT NULL,
 distance DECIMAL(8,2) NULL,
 weight DECIMAL(8,2) NULL,
 sets INT NULL,
 reps INT NULL,
 date DATE NOT NULL,
 latitude DECIMAL(10,7) NULL,
 longitude DECIMAL(10,7) NULL,
 notes VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 UNIQUE KEY sync_idempotency (user_id,client_id),
 INDEX history_lookup (user_id,date),
 CONSTRAINT duration_positive CHECK (duration BETWEEN 1 AND 1440),
 CONSTRAINT distance_positive CHECK (distance IS NULL OR distance > 0),
 CONSTRAINT weight_positive CHECK (weight IS NULL OR weight > 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS goals (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 type ENUM('Running','Cycling','Weightlifting','Walking') NOT NULL,
 target_minutes INT NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX goal_lookup (user_id,end_date),
 CONSTRAINT target_positive CHECK (target_minutes BETWEEN 1 AND 100000),
 CONSTRAINT date_order CHECK (end_date >= start_date)
) ENGINE=InnoDB;
```

## backend/public/index.php

```php
<?php
declare(strict_types=1);
require dirname(__DIR__).'/bootstrap.php';
$action=$_GET['action'] ?? '';
$method=$_SERVER['REQUEST_METHOD'];
$allowed=['health'=>'GET','register'=>'POST','login'=>'POST','logout'=>'POST','workouts'=>'GET',
    'save_workout'=>'POST','delete_workout'=>'POST','goals'=>'GET','save_goal'=>'POST','delete_goal'=>'POST'];
if (!isset($allowed[$action])) fail('Endpoint not found.',404);
if ($allowed[$action] !== $method) fail('Method not allowed.',405);
if ($action==='health') { db()->query('SELECT 1'); respond(['status'=>'ok','service'=>'Fitness Tracker']); }
if ($action==='register' || $action==='login') {
    $d=input();
    $email=strtolower(textValue($d,'email',190));
    if (!filter_var($email,FILTER_VALIDATE_EMAIL)) fail('Enter a valid email address.');
    $password=$d['password'] ?? '';
    if (!is_string($password) || strlen($password)<8 || strlen($password)>72) fail('Use a password of 8 to 72 bytes.');
    if ($action==='register') {
        $name=textValue($d,'name',100);
        try { db()->prepare('INSERT INTO users(name,email,password_hash) VALUES(?,?,?)')
            ->execute([$name,$email,password_hash($password,PASSWORD_DEFAULT)]); }
        catch(PDOException $e) { if ($e->getCode()==='23000') fail('This email is already registered.',409); throw $e; }
        issueSession((int)db()->lastInsertId(),$name);
    }
    $q=db()->prepare('SELECT id,name,password_hash FROM users WHERE email=?');$q->execute([$email]);$u=$q->fetch();
    // Always run password verification to reduce account-enumeration timing differences.
    $hash=$u['password_hash'] ?? '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
    if (!password_verify($password,$hash) || !$u) { usleep(200000); fail('Email or password is incorrect.',401); }
    issueSession((int)$u['id'],$u['name']);
}
$uid=authenticatedUser();
if ($action==='logout') {
    $token=substr($_SERVER['HTTP_AUTHORIZATION'],7);
    db()->prepare('DELETE FROM sessions WHERE token_hash=?')->execute([hash('sha256',$token)]);
    respond(['ok'=>true]);
}
if ($action==='workouts') {
    $q=db()->prepare('SELECT id,client_id,type,duration,distance,weight,sets,reps,date,latitude,longitude,notes FROM workouts WHERE user_id=? ORDER BY date DESC,id DESC');
    $q->execute([$uid]);respond(['workouts'=>$q->fetchAll()]);
}
if ($action==='save_workout') {
    $d=input();$type=activityType($d);$duration=numberValue($d,'duration',1,1440,true);$date=dateValue($d,'date');
    if ($date>gmdate('Y-m-d',time()+14*3600)) fail('A completed workout cannot be in the future.');
    $client=textValue($d,'client_id',36);
    if (!preg_match('/^[a-f0-9-]{36}$/D',$client)) fail('Invalid client identifier.');
    $strength=$type==='Weightlifting';
    $distance=$strength?null:numberValue($d,'distance',0.01,99999);
    $weight=$strength?numberValue($d,'weight',0.01,99999):null;
    $sets=$strength?numberValue($d,'sets',1,1000,true):null;
    $reps=$strength?numberValue($d,'reps',1,1000,true):null;
    $lat=$d['latitude'] ?? null;$lon=$d['longitude'] ?? null;
    if (($lat===null)!==($lon===null)) fail('Both location coordinates are required.');
    if ($lat!==null) {$lat=numberValue($d,'latitude',-90,90);$lon=numberValue($d,'longitude',-180,180);}
    $notes=textValue($d,'notes',500,false);
    $id=isset($d['id'])?numberValue($d,'id',0,PHP_INT_MAX,true):0;
    if ($id>0) {
        $q=db()->prepare('SELECT id FROM workouts WHERE id=? AND user_id=?');$q->execute([$id,$uid]);
        if (!$q->fetch()) fail('Workout not found.',404);
        db()->prepare('UPDATE workouts SET type=?,duration=?,distance=?,weight=?,sets=?,reps=?,date=?,latitude=?,longitude=?,notes=? WHERE id=? AND user_id=?')
            ->execute([$type,$duration,$distance,$weight,$sets,$reps,$date,$lat,$lon,$notes,$id,$uid]);
    } else {
        // A stable UUID makes retrying a pending offline workout safe.
        $q=db()->prepare('INSERT INTO workouts(user_id,client_id,type,duration,distance,weight,sets,reps,date,latitude,longitude,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)');
        $q->execute([$uid,$client,$type,$duration,$distance,$weight,$sets,$reps,$date,$lat,$lon,$notes]);
        $id=(int)db()->lastInsertId();
    }
    respond(['ok'=>true,'id'=>(int)$id]);
}
if ($action==='delete_workout' || $action==='delete_goal') {
    $d=input();$id=numberValue($d,'id',1,PHP_INT_MAX,true);
    $table=$action==='delete_workout'?'workouts':'goals';
    $q=db()->prepare("DELETE FROM $table WHERE id=? AND user_id=?");$q->execute([$id,$uid]);
    if (!$q->rowCount()) fail('Record not found.',404);
    respond(['ok'=>true]);
}
if ($action==='goals') {
    $q=db()->prepare('SELECT g.id,g.type,g.target_minutes,g.start_date,g.end_date,COALESCE(SUM(w.duration),0) AS completed_minutes FROM goals g LEFT JOIN workouts w ON w.user_id=g.user_id AND w.type=g.type AND w.date BETWEEN g.start_date AND g.end_date WHERE g.user_id=? GROUP BY g.id ORDER BY g.end_date DESC,g.id DESC');
    $q->execute([$uid]);respond(['goals'=>$q->fetchAll()]);
}
if ($action==='save_goal') {
    $d=input();$type=activityType($d);$target=numberValue($d,'target_minutes',1,100000,true);
    $start=dateValue($d,'start_date');$end=dateValue($d,'end_date');
    if ($end<$start) fail('End date must be on or after start date.');
    db()->prepare('INSERT INTO goals(user_id,type,target_minutes,start_date,end_date) VALUES(?,?,?,?,?)')->execute([$uid,$type,$target,$start,$end]);
    respond(['ok'=>true,'id'=>(int)db()->lastInsertId()]);
}
```

## build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
```

## settings.gradle.kts

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "FitnessTracker"
include(":app")
```
