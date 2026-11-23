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
