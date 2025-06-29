// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetSettingByKeyQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Query to get a setting by key
 */
data class GetSettingByKeyQuery(
    val key: String
) : IRequest<Setting?>