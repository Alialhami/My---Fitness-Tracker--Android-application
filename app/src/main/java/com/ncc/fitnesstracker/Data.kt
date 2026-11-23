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
