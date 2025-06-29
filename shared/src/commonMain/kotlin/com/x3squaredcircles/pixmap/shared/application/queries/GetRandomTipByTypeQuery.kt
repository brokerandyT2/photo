// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetRandomTipByTypeQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Query to get a random tip by tip type
 */
data class GetRandomTipByTypeQuery(
    val tipTypeId: Int
) : IRequest<Tip?>