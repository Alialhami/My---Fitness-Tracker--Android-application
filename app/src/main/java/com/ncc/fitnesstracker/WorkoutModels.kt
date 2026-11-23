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
