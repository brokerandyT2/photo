package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity

/**
 * Photography tip entity
 */
class Tip : Entity {

    private var _title: String = ""
    private var _content: String = ""
    private var _fstop: String = ""
    private var _shutterSpeed: String = ""
    private var _iso: String = ""

    var tipTypeId: Int = 0
        private set

    var title: String
        get() = _title
        private set(value) {
            require(value.isNotBlank()) { "Title cannot be empty" }
            _title = value
        }

    var content: String
        get() = _content
        private set(value) {
            _content = value
        }

    var fstop: String
        get() = _fstop
        private set(value) {
            _fstop = value
        }

    var shutterSpeed: String
        get() = _shutterSpeed
        private set(value) {
            _shutterSpeed = value
        }

    var iso: String
        get() = _iso
        private set(value) {
            _iso = value
        }

    var i8n: String = "en-US"
        private set

    // For ORM
    constructor()

    constructor(tipTypeId: Int, title: String, content: String) {
        this.tipTypeId = tipTypeId
        this.title = title
        this.content = content
    }

    fun updatePhotographySettings(fstop: String, shutterSpeed: String, iso: String) {
        this.fstop = fstop
        this.shutterSpeed = shutterSpeed
        this.iso = iso
    }

    fun updateContent(title: String, content: String) {
        this.title = title
        this.content = content
    }

    fun setLocalization(i8n: String?) {
        this.i8n = i8n ?: "en-US"
    }
}