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
