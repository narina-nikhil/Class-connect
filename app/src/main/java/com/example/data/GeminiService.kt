package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini Request / Response DTOs for Moshi ---

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String // Bas64 string
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPartResponse(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContentResponse
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

// Simple DTO to parse fields from AI and reconstruct into PeriodEntity on-device
@JsonClass(generateAdapter = true)
data class ParsedPeriod(
    val dayOfWeek: String,       // "MON", etc.
    val subjectCode: String? = null,
    val subjectName: String,
    val facultyName: String? = null,
    val roomNumber: String? = null,
    val startTime: String,
    val endTime: String,
    val isLab: Boolean? = null
)

// --- Retrofit Interface ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiService {

    // Helper to convert Bitmap to Base64 JPEG
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Helper to parse time string into minutes since midnight
    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val cleaned = timeStr.trim().uppercase()
            val isPm = cleaned.contains("PM")
            val isAm = cleaned.contains("AM")
            val pureTime = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = pureTime.split(":")
            var hour = parts[0].toInt()
            val minute = if (parts.size > 1) parts[1].toInt() else 0
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            hour * 60 + minute
        } catch (e: Exception) {
            0
        }
    }

    // Dynamic model-switching fallback executor to ensure compatibility with various API Key model tiers
    private suspend fun generateContentWithFallback(request: GeminiRequest, apiKey: String): GeminiResponse {
        val modelsToTry = listOf("gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null

        for (model in modelsToTry) {
            try {
                return GeminiClient.service.generateContent(model, apiKey, request)
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("All configured Gemini models failed to generate content.")
    }

    // Extremely robust helper to locate and extract valid JSON array boundaries out of standard LLM responses
    private fun extractJsonArray(text: String): String {
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        return if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            text.substring(firstBracket, lastBracket + 1)
        } else {
            text
        }
    }

    suspend fun parseTimetableFromImage(bitmap: Bitmap): List<PeriodEntity> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is not configured. Please enter your GEMINI_API_KEY in the Secrets panel of Google AI Studio.")
        }

        val prompt = """
            Examine this timetable schedule image exceptionally closely. Identify different academic days (MON, TUE, WED, THU, FRI, SAT, SUN), subject codes (e.g. CS201, EMA2216), subject titles, instructor/professor names, classroom/labs (e.g. Rm 201, Lab 4-C) and start/end times.
            
            Extract them and translate the timetable into a structured list. Populate it strictly inside a JSON array of objects conforming to this schema parameters.
            Do not include any extra text or conversational filler, and do NOT wrap the output in markdown block ticks. Return raw JSON string only.
        """.trimIndent()

        val systemInstruction = """
            You are a professional academic timetable parser. Parse the contents of the image and return a JSON array where each object has these exact fields:
            - "dayOfWeek": short name string ("MON", "TUE", "WED", "THU", "FRI", "SAT" or "SUN").
            - "subjectCode": string code or empty string.
            - "subjectName": string title of course.
            - "facultyName": string professor name or empty string.
            - "roomNumber": room or laboratory identification string.
            - "startTime": standard time with AM/PM (e.g. "09:00 AM", "01:20 PM", "10:15 AM").
            - "endTime": standard time with AM/PM (e.g. "09:55 AM", "04:05 PM", "11:45 AM").
            - "isLab": boolean (true if practical session, laboratory, workshop, seminar, else false).
            
            Do NOT include code block tags or backticks (```json). Return raw valid JSON.
        """.trimIndent()

        val base64Image = bitmap.toBase64()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val response = generateContentWithFallback(request, apiKey)
        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini returned an empty response.")

        // Parse extracted list using Moshi
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val listType = Types.newParameterizedType(List::class.java, ParsedPeriod::class.java)
        val adapter = moshi.adapter<List<ParsedPeriod>>(listType)
        
        val cleanedJson = extractJsonArray(responseText)
        val parsedList = try {
            adapter.fromJson(cleanedJson) ?: emptyList()
        } catch (e: Exception) {
            // Secondary fallback attempt: strip markdown tags manually if bounds extraction failed
            val manualClean = responseText.replace("```json", "").replace("```", "").trim()
            adapter.fromJson(manualClean) ?: emptyList()
        }

        // Reconstruct as database entities
        parsedList.map { parsed ->
            val sMinutes = parseTimeToMinutes(parsed.startTime)
            val eMinutes = parseTimeToMinutes(parsed.endTime)
            PeriodEntity(
                id = 0,
                scheduleOwnerName = "My Schedule", // placeholder, will be overridden in VM load call if friend name is supplied
                dayOfWeek = parsed.dayOfWeek.uppercase().trim().take(3),
                subjectCode = parsed.subjectCode ?: "N/A",
                subjectName = parsed.subjectName,
                facultyName = parsed.facultyName ?: "N/A",
                roomNumber = parsed.roomNumber ?: "N/A",
                startTime = parsed.startTime,
                endTime = parsed.endTime,
                startMinutes = sMinutes,
                endMinutes = eMinutes,
                isLab = parsed.isLab ?: false
            )
        }
    }

    suspend fun parseTimetableFromText(text: String): List<PeriodEntity> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is not configured. Please enter your GEMINI_API_KEY in the Secrets panel of Google AI Studio.")
        }

        val prompt = """
            Analyze this unstructured timetable schedule plain text extremely closely. It may contain days, class slots, course codes, names, instructors, and hours.
            
            Unstructured timetable text content:
            $text
            
            Extract them and translate into a structured representation list. Populate it strictly inside a JSON array of objects conforming to the schema parameters.
            Do not include any extra text, and do NOT wrap the output in markdown block ticks. Return raw JSON string only.
        """.trimIndent()

        val systemInstruction = """
            You are a professional academic timetable parser. Parse the contents of the text and return a JSON array where each object has these exact fields:
            - "dayOfWeek": short name string ("MON", "TUE", "WED", "THU", "FRI", "SAT" or "SUN").
            - "subjectCode": string code or empty string.
            - "subjectName": string title of course.
            - "facultyName": string professor name or empty string.
            - "roomNumber": room or laboratory identification string.
            - "startTime": standard time with AM/PM (e.g. "09:00 AM", "01:20 PM", "10:15 AM").
            - "endTime": standard time with AM/PM (e.g. "09:55 AM", "04:05 PM", "11:45 AM").
            - "isLab": boolean (true if practical session, laboratory, workshop, seminar, else false).
            
            Do NOT include code block tags or backticks (```json). Return raw valid JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val response = generateContentWithFallback(request, apiKey)
        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini returned an empty response.")

        // Parse extracted list using Moshi
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val listType = Types.newParameterizedType(List::class.java, ParsedPeriod::class.java)
        val adapter = moshi.adapter<List<ParsedPeriod>>(listType)
        
        val cleanedJson = extractJsonArray(responseText)
        val parsedList = try {
            adapter.fromJson(cleanedJson) ?: emptyList()
        } catch (e: Exception) {
            val manualClean = responseText.replace("```json", "").replace("```", "").trim()
            adapter.fromJson(manualClean) ?: emptyList()
        }

        // Reconstruct as database entities
        parsedList.map { parsed ->
            val sMinutes = parseTimeToMinutes(parsed.startTime)
            val eMinutes = parseTimeToMinutes(parsed.endTime)
            PeriodEntity(
                id = 0,
                scheduleOwnerName = "My Schedule",
                dayOfWeek = parsed.dayOfWeek.uppercase().trim().take(3),
                subjectCode = parsed.subjectCode ?: "N/A",
                subjectName = parsed.subjectName,
                facultyName = parsed.facultyName ?: "N/A",
                roomNumber = parsed.roomNumber ?: "N/A",
                startTime = parsed.startTime,
                endTime = parsed.endTime,
                startMinutes = sMinutes,
                endMinutes = eMinutes,
                isLab = parsed.isLab ?: false
            )
        }
    }
}
