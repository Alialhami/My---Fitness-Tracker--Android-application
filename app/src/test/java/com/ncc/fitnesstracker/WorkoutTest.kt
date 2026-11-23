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
