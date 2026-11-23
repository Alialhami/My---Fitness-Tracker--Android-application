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
