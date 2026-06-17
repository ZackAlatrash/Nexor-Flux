package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updatedAt DESC")
    abstract fun observeAllWithExercises(): Flow<List<WorkoutWithExercisesDb>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun getWithExercises(id: Long): WorkoutWithExercisesDb?

    @Insert
    abstract suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    abstract suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun deleteWorkoutById(id: Long)

    @Insert
    abstract suspend fun insertWorkoutExercise(line: WorkoutExerciseEntity): Long

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun deleteExercisesByWorkoutId(workoutId: Long)

    @Transaction
    open suspend fun replaceExercises(workoutId: Long, lines: List<WorkoutExerciseEntity>) {
        deleteExercisesByWorkoutId(workoutId)
        lines.forEachIndexed { index, line ->
            insertWorkoutExercise(line.copy(workoutId = workoutId, sortOrder = index, id = 0))
        }
    }
}
