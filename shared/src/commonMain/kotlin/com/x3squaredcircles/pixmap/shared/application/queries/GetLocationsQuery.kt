// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetLocationsQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.dto.LocationListDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest

/**
 * Query to get a paginated list of locations
 */
data class GetLocationsQuery(
    val pageNumber: Int = 1,
    val pageSize: Int = 10,
    val searchTerm: String? = null,
    val includeDeleted: Boolean = false
) : IRequest<PagedList<LocationListDto>>