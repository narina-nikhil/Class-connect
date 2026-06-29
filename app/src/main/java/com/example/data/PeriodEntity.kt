package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "periods")
@JsonClass(generateAdapter = true)
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleOwnerName: String, // "My Schedule", or Friend's Name (e.g. "Rahul")
    val dayOfWeek: String,       // "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"
    val subjectCode: String,     // e.g. "EMA2216"
    val subjectName: String,     // e.g. "Java Programming"
    val facultyName: String,     // e.g. "Mr. P Shiva Shankar"
    val roomNumber: String,      // e.g. "I-201", "I-311", "Seminar Hall", etc.
    val startTime: String,       // e.g. "09:00 AM" or "1:20 PM"
    val endTime: String,         // e.g. "09:55 AM" or "4:05 PM"
    val startMinutes: Int,       // Minutes from midnight, e.g., 9:00 AM -> 540. Used for calculations.
    val endMinutes: Int,         // Minutes from midnight, e.g., 9:55 AM -> 595. Used for calculations.
    val isLab: Boolean = false
)
