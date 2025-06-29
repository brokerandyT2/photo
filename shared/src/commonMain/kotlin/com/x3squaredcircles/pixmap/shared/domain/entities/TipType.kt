// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/TipType.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity

/**
 * Tip category entity
 */
class TipType private constructor() : Entity() {

    private var _name: String = ""
    private val _tips = mutableListOf<Tip>()

    val name: String
        get() = _name

    var i8n: String = "en-US"
        private set

    val tips: List<Tip> = _tips

    constructor(name: String) : this() {
        require(name.isNotBlank()) { "Name cannot be empty" }
        _name = name
    }

    fun setLocalization(i8n: String?) {
        this.i8n = i8n ?: "en-US"
    }

    fun addTip(tip: Tip) {
        if (tip.tipTypeId != id && id > 0) {
            throw IllegalStateException("Tip type ID mismatch")
        }
        _tips.add(tip)
    }

    fun removeTip(tip: Tip) {
        _tips.remove(tip)
    }
}