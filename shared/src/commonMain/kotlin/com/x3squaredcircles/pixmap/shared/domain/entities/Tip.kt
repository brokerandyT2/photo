// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Tip.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity

/**
 * Photography tip entity
 */
class Tip private constructor() : Entity() {

    private var _title: String = ""
    private var _content: String = ""
    private var _fstop: String = ""
    private var _shutterSpeed: String = ""
    private var _iso: String = ""

    val tipTypeId: Int
        get() = _tipTypeId

    private var _tipTypeId: Int = 0

    val title: String
        get() = _title

    val content: String
        get() = _content

    val fstop: String
        get() = _fstop

    val shutterSpeed: String
        get() = _shutterSpeed

    val iso: String
        get() = _iso

    var i8n: String = "en-US"
        private set

    constructor(tipTypeId: Int, title: String, content: String) : this() {
        require(title.isNotBlank()) { "Title cannot be empty" }
        _tipTypeId = tipTypeId
        _title = title
        _content = content
    }

    fun updatePhotographySettings(fstop: String?, shutterSpeed: String?, iso: String?) {
        _fstop = fstop ?: ""
        _shutterSpeed = shutterSpeed ?: ""
        _iso = iso ?: ""
    }

    fun updateContent(title: String, content: String) {
        require(title.isNotBlank()) { "Title cannot be empty" }
        _title = title
        _content = content
    }

    fun setLocalization(i8n: String?) {
        this.i8n = i8n ?: "en-US"
    }
}