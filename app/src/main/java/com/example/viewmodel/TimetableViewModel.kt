package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PeriodEntity
import com.example.data.ScheduleRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

import com.example.data.GeminiService
import android.graphics.Bitmap

class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduleRepository
    private val geminiService = GeminiService()
    private val sharedPrefs = application.getSharedPreferences("class_connect_prefs", Context.MODE_PRIVATE)
    
    private val _extraOwners = MutableStateFlow<Set<String>>(emptySet())
    val extraOwners: StateFlow<Set<String>> = _extraOwners.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ScheduleRepository(database.periodDao())
        
        // Load extra owners from SharedPreferences
        val saved = sharedPrefs.getStringSet("extra_owners", emptySet()) ?: emptySet()
        _extraOwners.value = saved

        viewModelScope.launch {
            repository.initializeDatabaseIfEmpty()
            // Force seed the new AIML-F schedule exactly once on update
            val isAimlFSeeded = sharedPrefs.getBoolean("is_aiml_f_seeded_v3", false)
            if (!isAimlFSeeded) {
                repository.resetAllToDefaults()
                _extraOwners.value = emptySet()
                sharedPrefs.edit()
                    .remove("extra_owners")
                    .putBoolean("is_aiml_f_seeded_v3", true)
                    .apply()
            }
        }
    }

    // AI Timetable Parsing States
    private val _isParsingImage = MutableStateFlow(false)
    val isParsingImage: StateFlow<Boolean> = _isParsingImage.asStateFlow()

    private val _parseStatusMessage = MutableStateFlow<String?>(null)
    val parseStatusMessage: StateFlow<String?> = _parseStatusMessage.asStateFlow()

    fun parseAndSaveTimetable(bitmap: Bitmap, targetOwner: String) {
        viewModelScope.launch {
            _isParsingImage.value = true
            _parseStatusMessage.value = "Analyzing timetable image with Gemini AI..."
            try {
                val periods = geminiService.parseTimetableFromImage(bitmap)
                if (periods.isEmpty()) {
                    _parseStatusMessage.value = "Failed: No classes identified in the image. Please upload a clearer schedule."
                } else {
                    // Update SharedPreferences extra owners
                    if (targetOwner.startsWith("Friend: ")) {
                        val newSet = _extraOwners.value.toMutableSet().apply { add(targetOwner) }
                        _extraOwners.value = newSet
                        sharedPrefs.edit().putStringSet("extra_owners", newSet).apply()
                    }

                    // Replace existing periods for that owner name
                    repository.deletePeriodsByOwner(targetOwner)
                    val mappedPeriods = periods.map {
                        it.copy(scheduleOwnerName = targetOwner)
                    }
                    repository.insertPeriods(mappedPeriods)
                    _selectedOwnerName.value = targetOwner
                    _parseStatusMessage.value = "Success! Imported ${periods.size} periods into '$targetOwner'."
                }
            } catch (e: Exception) {
                _parseStatusMessage.value = "AI Parsing Failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isParsingImage.value = false
            }
        }
    }

    fun parseAndSaveTimetableFromText(text: String, targetOwner: String) {
        viewModelScope.launch {
            _isParsingImage.value = true
            _parseStatusMessage.value = "Analyzing text timetable with Gemini AI..."
            try {
                val periods = geminiService.parseTimetableFromText(text)
                if (periods.isEmpty()) {
                    _parseStatusMessage.value = "Failed: No classes parsed from text. Ensure text has school schedules, days, or time indicators."
                } else {
                    // Update SharedPreferences extra owners
                    if (targetOwner.startsWith("Friend: ")) {
                        val newSet = _extraOwners.value.toMutableSet().apply { add(targetOwner) }
                        _extraOwners.value = newSet
                        sharedPrefs.edit().putStringSet("extra_owners", newSet).apply()
                    }

                    repository.deletePeriodsByOwner(targetOwner)
                    val mappedPeriods = periods.map {
                        it.copy(scheduleOwnerName = targetOwner)
                    }
                    repository.insertPeriods(mappedPeriods)
                    _selectedOwnerName.value = targetOwner
                    _parseStatusMessage.value = "Success! Imported ${periods.size} periods into '$targetOwner'."
                }
            } catch (e: Exception) {
                _parseStatusMessage.value = "AI Text Parsing Failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isParsingImage.value = false
            }
        }
    }

    fun clearParseStatus() {
        _parseStatusMessage.value = null
    }

    // Settings & Navigation States
    private val _selectedOwnerName = MutableStateFlow("My Schedule")
    val selectedOwnerName: StateFlow<String> = _selectedOwnerName.asStateFlow()

    private val _selectedDay = MutableStateFlow(getCurrentDayOfWeekString())
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    // Speech preferences: Text or Voice
    private val _speechPreferenceEnabled = MutableStateFlow(true)
    val speechPreferenceEnabled: StateFlow<Boolean> = _speechPreferenceEnabled.asStateFlow()

    // Distinct Owners List Flow - combining Database owners and custom extra (empty) owners
    val distinctOwners: StateFlow<List<String>> = combine(
        repository.distinctOwners,
        _extraOwners
    ) { dbOwners, extraOwners ->
        val mergedList = mutableListOf<String>()
        mergedList.add("My Schedule")
        mergedList.addAll(dbOwners)
        mergedList.addAll(extraOwners)
        mergedList.distinct().sortedWith { a, b ->
            when {
                a == "My Schedule" -> -1
                b == "My Schedule" -> 1
                else -> a.compareTo(b)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("My Schedule")
    )

    // Current schedule flow based on selected owner
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPeriods: StateFlow<List<PeriodEntity>> = _selectedOwnerName
        .flatMapLatest { ownerName ->
            repository.getPeriodsForOwner(ownerName)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Parse device current time to determine classes
    fun getScheduleOverviewAtTime(
        periods: List<PeriodEntity>,
        dayOfWeek: String,
        minutesSinceMidnight: Int
    ): ActiveScheduleOverview {
        val dayPeriods = periods.filter { it.dayOfWeek.uppercase() == dayOfWeek.uppercase() }
            .sortedBy { it.startMinutes }

        var current: PeriodEntity? = null
        var next: PeriodEntity? = null
        var previous: PeriodEntity? = null

        // 1. Find current
        current = dayPeriods.firstOrNull { minutesSinceMidnight >= it.startMinutes && minutesSinceMidnight < it.endMinutes }

        // 2. Find next
        next = dayPeriods.filter { it.startMinutes > minutesSinceMidnight }
            .minByOrNull { it.startMinutes }

        // 3. Find previous
        previous = dayPeriods.filter { it.endMinutes <= minutesSinceMidnight }
            .maxByOrNull { it.endMinutes }

        return ActiveScheduleOverview(previous, current, next)
    }

    fun selectOwner(ownerName: String) {
        _selectedOwnerName.value = ownerName
    }

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun toggleSpeechPreference() {
        _speechPreferenceEnabled.value = !_speechPreferenceEnabled.value
    }

    fun addNewFriendTimetable(friendName: String, periodsList: List<PeriodEntity>) {
        viewModelScope.launch {
            val ownerName = "Friend: $friendName"
            val newSet = _extraOwners.value.toMutableSet().apply { add(ownerName) }
            _extraOwners.value = newSet
            sharedPrefs.edit().putStringSet("extra_owners", newSet).apply()

            if (periodsList.isNotEmpty()) {
                val mappedPeriods = periodsList.map {
                    it.copy(id = 0, scheduleOwnerName = ownerName)
                }
                repository.insertPeriods(mappedPeriods)
            }
            _selectedOwnerName.value = ownerName
        }
    }

    fun deleteOwnerSchedule(ownerName: String) {
        viewModelScope.launch {
            repository.deletePeriodsByOwner(ownerName)
            val newSet = _extraOwners.value.toMutableSet().apply { remove(ownerName) }
            _extraOwners.value = newSet
            sharedPrefs.edit().putStringSet("extra_owners", newSet).apply()

            if (_selectedOwnerName.value == ownerName) {
                _selectedOwnerName.value = "My Schedule"
            }
        }
    }

    fun insertSinglePeriod(period: PeriodEntity) {
        viewModelScope.launch {
            repository.insertPeriod(period)
        }
    }

    fun deletePeriod(id: Long) {
        viewModelScope.launch {
            repository.deletePeriodById(id)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetAllToDefaults()
            _extraOwners.value = emptySet()
            sharedPrefs.edit().remove("extra_owners").apply()
            _selectedOwnerName.value = "My Schedule"
            _selectedDay.value = getCurrentDayOfWeekString()
        }
    }

    fun exportAllSchedulesToJson(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Fetch all data from database
                val allData = repository.getPeriodsForOwner("My Schedule").first() // wait, how about all owners?
                // Let's implement an export of the entire database so sync works fully!
                val database = AppDatabase.getDatabase(getApplication())
                val allPeriods = database.periodDao().getAllPeriods().first()
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, PeriodEntity::class.java)
                val jsonAdapter = moshi.adapter<List<PeriodEntity>>(listType)
                onComplete(jsonAdapter.toJson(allPeriods))
            } catch (e: Exception) {
                onComplete("Error formatting data: ${e.message}")
            }
        }
    }

    fun importSchedulesFromJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, PeriodEntity::class.java)
                val jsonAdapter = moshi.adapter<List<PeriodEntity>>(listType)
                val importedList = jsonAdapter.fromJson(jsonString)
                if (importedList != null) {
                    repository.clearAll()
                    // Strip the old database generated primary keys (set to 0) to re-insert cleanly
                    val cleanedList = importedList.map { it.copy(id = 0) }
                    repository.insertPeriods(cleanedList)

                    // Re-calculate extra owners from the imported database's owners
                    val importedOwners = cleanedList.map { it.scheduleOwnerName }
                        .filter { it.startsWith("Friend:") }
                        .toSet()
                    _extraOwners.value = importedOwners
                    sharedPrefs.edit().putStringSet("extra_owners", importedOwners).apply()

                    _selectedOwnerName.value = "My Schedule"
                    onResult(true, "Successfully synced ${cleanedList.size} periods across your devices!")
                } else {
                    onResult(false, "Invalid backup format: data was empty.")
                }
            } catch (e: Exception) {
                onResult(false, "Failed to import schedule: ${e.localizedMessage}")
            }
        }
    }

    fun handleVoiceCommand(voiceInput: String, onSpeak: (String) -> Unit) {
        val inputNormalized = voiceInput.lowercase().trim()
        val periods = currentPeriods.value
        val today = getCurrentDayOfWeekString()
        val minutes = getCurrentTimeMinutes()

        val scheduleInfo = getScheduleOverviewAtTime(periods, today, minutes)

        when {
            inputNormalized.contains("reset") || inputNormalized.contains("default") -> {
                resetToDefaults()
                onSpeak("I have reset all timetables back to your original class schedule.")
            }
            inputNormalized.contains("current") || inputNormalized.contains("now") || inputNormalized.contains("present") -> {
                val current = scheduleInfo.current
                if (current != null) {
                    val response = "Right now you have ${current.subjectName} by ${current.facultyName} in classroom ${current.roomNumber}. It ends at ${current.endTime}."
                    onSpeak(response)
                } else {
                    val freeResponse = "You have no scheduled period right now on $today. You are free."
                    onSpeak(freeResponse)
                }
            }
            inputNormalized.contains("next") || inputNormalized.contains("upcoming") -> {
                val next = scheduleInfo.next
                if (next != null) {
                    val response = "Your upcoming class is ${next.subjectName} by ${next.facultyName} in classroom ${next.roomNumber}, starting at ${next.startTime}."
                    onSpeak(response)
                } else {
                    onSpeak("You have no more classes scheduled for the rest of today.")
                }
            }
            inputNormalized.contains("previous") || inputNormalized.contains("past") -> {
                val previous = scheduleInfo.previous
                if (previous != null) {
                    val response = "Your previous class was ${previous.subjectName} in room ${previous.roomNumber}, which ended at ${previous.endTime}."
                    onSpeak(response)
                } else {
                    onSpeak("There was no previous class earlier today.")
                }
            }
            inputNormalized.contains("today") || inputNormalized.contains("periods today") || inputNormalized.contains("classes today") || inputNormalized.contains("all periods") -> {
                val todayPeriods = periods.filter { it.dayOfWeek.uppercase() == today.uppercase() }
                    .sortedBy { it.startMinutes }
                if (todayPeriods.isEmpty()) {
                    onSpeak("You have no classes scheduled for $today.")
                } else {
                    val count = todayPeriods.size
                    var text = "Today you have $count periods. "
                    todayPeriods.forEachIndexed { index, period ->
                        text += "Period ${index + 1} is ${period.subjectName} at ${period.startTime} in classroom ${period.roomNumber}. "
                    }
                    onSpeak(text)
                }
            }
            // Friends selection commands
            inputNormalized.contains("friend") || inputNormalized.contains("schedule of") || inputNormalized.contains("timetable") -> {
                val owners = distinctOwners.value
                var matchedOwner: String? = null
                for (owner in owners) {
                    val shortName = owner.replace("Friend: ", "").lowercase()
                    if (inputNormalized.contains(shortName)) {
                        matchedOwner = owner
                        break
                    }
                }
                if (matchedOwner != null) {
                    _selectedOwnerName.value = matchedOwner
                    onSpeak("Switched to $matchedOwner.")
                } else {
                    onSpeak("I couldn't find a friend's timetable matching your spoken word. You can add one from the sync menu.")
                }
            }
            // Switch current view day
            inputNormalized.contains("monday") -> {
                _selectedDay.value = "MON"
                onSpeak("Switched view to Monday.")
            }
            inputNormalized.contains("tuesday") -> {
                _selectedDay.value = "TUE"
                onSpeak("Switched view to Tuesday.")
            }
            inputNormalized.contains("wednesday") -> {
                _selectedDay.value = "WED"
                onSpeak("Switched view to Wednesday.")
            }
            inputNormalized.contains("thursday") -> {
                _selectedDay.value = "THU"
                onSpeak("Switched view to Thursday.")
            }
            inputNormalized.contains("friday") -> {
                _selectedDay.value = "FRI"
                onSpeak("Switched view to Friday.")
            }
            inputNormalized.contains("saturday") -> {
                _selectedDay.value = "SAT"
                onSpeak("Switched view to Saturday.")
            }
            else -> {
                onSpeak("Understood, but I didn't recognize that command. Try asking for current, next, or today's classes.")
            }
        }
    }

    // Helper time calculations
    fun getCurrentDayOfWeekString(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            else -> "MON"
        }
    }

    fun getCurrentTimeMinutes(): Int {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour * 60 + minute
    }
}

// Simple Holder representing Present, Next, Previous
data class ActiveScheduleOverview(
    val previous: PeriodEntity?,
    val current: PeriodEntity?,
    val next: PeriodEntity?
)
