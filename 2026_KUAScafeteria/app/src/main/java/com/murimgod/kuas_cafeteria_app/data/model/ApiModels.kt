package com.murimgod.kuas_cafeteria_app.data.model

import com.google.gson.annotations.SerializedName

data class Campus(
    val id: String,
    @SerializedName("nameEn") val nameEn: String?,
    @SerializedName("nameJa") val nameJa: String?,
    val location: String?,
    val hours: Hours?,
    val openDays: List<String>?
) {
    val displayName: String get() = nameEn ?: id.replaceFirstChar { it.uppercase() }
}

data class Hours(
    val open: String?,
    val close: String?,
    val ticketsFrom: String?
) {
    fun format(): String {
        val parts = mutableListOf<String>()
        if (ticketsFrom != null) parts.add("Tickets from $ticketsFrom")
        if (open != null && close != null) parts.add("Open $open–$close")
        return parts.joinToString(" · ")
    }
}

data class DailyMenu(
    val campus: String,
    val date: String,
    val weekId: String?,
    val isOpen: Boolean,
    val reason: String?,
    val hours: Hours?,
    val sections: List<Section>,
    val disclaimer: String?,
    val discount: Discount?,
    val source: Source?
)

data class WeekMenuResponse(
    val days: List<DailyMenu>
)

data class Section(
    val kind: String,
    val slot: String?,
    val title: String,
    val items: List<MenuItem>
)

data class MenuItem(
    val id: String,
    val name: String,
    @SerializedName("nameJa") val nameJa: String?,
    val prices: List<Price>,
    val nutrition: Nutrition?,
    val allergens: List<String>,
    val tags: List<String>,
    val available: Boolean,
    val filteredOut: Boolean
) {
    val basePrice: Int? get() = prices.firstOrNull { it.tier == "base" || it.tier == "single" }
        ?.let { it.discountedYen ?: it.yen }
    val setPrice: Int? get() = prices.firstOrNull { it.tier == "set" }
        ?.let { it.discountedYen ?: it.yen }

    fun priceLabel(): String {
        val base = basePrice
        val set = setPrice
        return when {
            base != null && set != null -> "¥$base · set ¥$set"
            base != null -> "¥$base"
            set != null -> "set ¥$set"
            else -> ""
        }
    }
}

data class Price(
    val tier: String,
    val yen: Int,
    val discount: Int?,
    val discountedYen: Int?
)

data class Nutrition(
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val salt: Double?
)

data class Discount(
    val active: Boolean,
    val campaign: String?,
    val lunchOff: Int?,
    val curryNoodleOff: Int?,
    val periods: List<DiscountPeriod>?
)

data class DiscountPeriod(
    val start: String,
    val end: String
)

data class Source(
    val pdfUrl: String?,
    val parsedAt: String?,
    val confidence: Double?
)

data class AllergenInfo(
    val id: String,
    @SerializedName("nameEn") val nameEn: String,
    @SerializedName("nameJa") val nameJa: String?
)

data class WeekSummary(
    val weekId: String,
    val isoWeek: String,
    val coverage: List<String>?
)

data class WeekApiItem(
    val weekId: String,
    val isoWeek: String,
    val start: String?,
    val end: String?,
    val days: List<String>?
)

data class WeeksListResponse(
    val campus: String,
    val weeks: List<WeekApiItem>
)

data class OnboardingEvent(
    val campus: String,
    val lang: String,
    val skipped: Boolean,
    val platform: String = "android",
    val appVersion: String?,
    val deviceId: String?
)

data class CrashEvent(
    val message: String,
    val stacktrace: String,
    val platform: String = "android",
    val appVersion: String?,
    val deviceId: String?
)

data class HealthResponse(
    val status: String,
    val version: String?,
    val db: String?,
    val redis: String?
)
