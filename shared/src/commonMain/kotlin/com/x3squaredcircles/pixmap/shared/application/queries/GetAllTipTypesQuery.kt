// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetAllTipTypesQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Query to get all tip types
 */
class GetAllTipTypesQuery : IRequest<List<TipType>>