package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity

/**
 * Tip category entity
 */
class TipType : Entity {

    private var _name: String = ""
    private val _tips = mutableListOf<Tip>()

    var name: String
        get() = _name
        private set(value) {
            require(value.isNotBlank()) { "Name cannot be empty" }
            _name = value
        }

    var i8n: String = "en-US"
        private set

    val tips: List<Tip> get() = _tips.toList()

    // For ORM
    constructor()

    constructor(name: String) {
        this.name = name
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