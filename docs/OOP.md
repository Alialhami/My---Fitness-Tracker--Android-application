# Three implemented inheritance examples

## 1. Shared activity behaviour

`BaseActivity : AppCompatActivity` holds session checking, error feedback, inset handling, shared labels, buttons and form fields. `AuthActivity`, `MainActivity` and `AddWorkoutActivity` inherit this behaviour. A change to a common field style or session check can be made centrally.

Source: `app/src/main/java/com/ncc/fitnesstracker/BaseActivity.kt` and the three activity files.

## 2. Activity model hierarchy and polymorphism

The abstract `Workout` class holds identity, duration and date and declares `type` and `statistics()`. `RunningWorkout`, `CyclingWorkout`, `WalkingWorkout` and `WeightliftingWorkout` inherit those common properties and implement activity-specific statistics. `WorkoutAdapter` calls `model.statistics()` through the `Workout` type, so the subclass supplies the correct pace, speed or volume without the adapter duplicating those formulas.

- Running pace = duration in minutes / distance in kilometres.
- Cycling speed = distance in kilometres / duration in hours.
- Weightlifting volume = weight per repetition × sets × repetitions.

Source: `WorkoutModels.kt`, `Data.kt` and `WorkoutAdapter.kt` in the Kotlin package.

## 3. RecyclerView framework inheritance

`WorkoutAdapter` extends `RecyclerView.Adapter<WorkoutViewHolder>` and overrides `onCreateViewHolder`, `onBindViewHolder` and `getItemCount`. Its nested `WorkoutViewHolder` extends `RecyclerView.ViewHolder` and retains references to row controls. The framework supplies view recycling and lifecycle integration while the app supplies workout binding and tap behaviour.

Source: `WorkoutAdapter.kt` and `app/src/main/res/layout/item_workout.xml`.

## Encapsulation

`Store` keeps its SharedPreferences instance private and provides specific operations for session state, queued workouts and cached records. `Api` hides the Volley request queue. Workout model properties are read-only `val` values. Passwords are sent to the PHP service for verification and are never stored in the Android preferences. PHP stores password hashes and hashed session tokens.
