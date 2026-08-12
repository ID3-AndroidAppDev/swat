package com.murimgod.kuas_cafeteria_app.ui.tray

import androidx.lifecycle.ViewModel
import com.murimgod.kuas_cafeteria_app.data.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrayEntry(
    val item: MenuItem,
    val isSet: Boolean,
    var qty: Int = 1
) {
    val effectivePrice: Int get() {
        val price = if (isSet) item.setPrice ?: item.basePrice ?: 0
                    else item.basePrice ?: 0
        return price * qty
    }
    val totalKcal: Double get() = (item.nutrition?.kcal ?: 0.0) * qty
    val totalProtein: Double get() = (item.nutrition?.protein ?: 0.0) * qty
    val totalFat: Double get() = (item.nutrition?.fat ?: 0.0) * qty
    val totalSalt: Double get() = (item.nutrition?.salt ?: 0.0) * qty
}

class TrayViewModel : ViewModel() {

    private val _entries = MutableStateFlow<List<TrayEntry>>(emptyList())
    val entries: StateFlow<List<TrayEntry>> = _entries.asStateFlow()

    val totalKcal: Double get() = _entries.value.sumOf { it.totalKcal }
    val totalPrice: Int get() = _entries.value.sumOf { it.effectivePrice }
    val totalProtein: Double get() = _entries.value.sumOf { it.totalProtein }
    val totalFat: Double get() = _entries.value.sumOf { it.totalFat }

    fun addItem(item: MenuItem, isSet: Boolean) {
        val current = _entries.value.toMutableList()
        val existing = current.indexOfFirst { it.item.id == item.id && it.isSet == isSet }
        if (existing >= 0) {
            current[existing] = current[existing].copy(qty = current[existing].qty + 1)
        } else {
            current.add(TrayEntry(item, isSet))
        }
        _entries.value = current
    }

    fun increaseQty(entry: TrayEntry) {
        val current = _entries.value.toMutableList()
        val idx = current.indexOf(entry)
        if (idx >= 0) current[idx] = entry.copy(qty = entry.qty + 1)
        _entries.value = current
    }

    fun decreaseQty(entry: TrayEntry) {
        val current = _entries.value.toMutableList()
        val idx = current.indexOf(entry)
        if (idx >= 0) {
            if (entry.qty <= 1) current.removeAt(idx)
            else current[idx] = entry.copy(qty = entry.qty - 1)
        }
        _entries.value = current
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun summaryText(): String {
        if (_entries.value.isEmpty()) return "Σ 0 kcal · ¥0"
        val kcal = totalKcal.toInt()
        val price = totalPrice
        return "Σ $kcal kcal · ¥$price"
    }
}
