//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetTipsByTypeQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Query to get tips by type
 */
data class GetTipsByTypeQuery(
    val tipTypeId: Int
) : IRequest<List<Tip>>