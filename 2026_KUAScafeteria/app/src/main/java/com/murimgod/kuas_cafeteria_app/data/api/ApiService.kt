package com.murimgod.kuas_cafeteria_app.data.api

import com.murimgod.kuas_cafeteria_app.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("campuses")
    suspend fun getCampuses(@Query("lang") lang: String = "en"): List<Campus>

    @GET("campuses/{campus}/today")
    suspend fun getTodayMenu(
        @Path("campus") campus: String,
        @Query("lang") lang: String = "en",
        @Query("excludeAllergens") excludeAllergens: String? = null
    ): DailyMenu

    @GET("campuses/{campus}/menu")
    suspend fun getMenuByDate(
        @Path("campus") campus: String,
        @Query("date") date: String,
        @Query("lang") lang: String = "en",
        @Query("excludeAllergens") excludeAllergens: String? = null
    ): DailyMenu

    @GET("campuses/{campus}/menu")
    suspend fun getMenuByWeek(
        @Path("campus") campus: String,
        @Query("week") week: String,
        @Query("lang") lang: String = "en",
        @Query("excludeAllergens") excludeAllergens: String? = null
    ): WeekMenuResponse

    @GET("campuses/{campus}/weeks")
    suspend fun getWeeks(
        @Path("campus") campus: String,
        @Query("limit") limit: Int = 12
    ): WeeksListResponse

    @GET("allergens")
    suspend fun getAllergens(@Query("lang") lang: String = "en"): List<AllergenInfo>

    @GET("items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("lang") lang: String = "en"
    ): MenuItem

    @GET("health")
    suspend fun getHealth(): HealthResponse

    // First-party telemetry (self-hosted, no Firebase). Sent once after the
    // first launch / onboarding, and from the crash handler.
    @POST("events/onboarding")
    suspend fun postOnboarding(@Body body: OnboardingEvent): Response<ResponseBody>

    @POST("events/crash")
    suspend fun postCrash(@Body body: CrashEvent): Response<ResponseBody>
}
