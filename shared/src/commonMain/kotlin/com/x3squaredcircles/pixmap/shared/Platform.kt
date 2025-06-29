package com.x3squaredcircles.pixmap.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform