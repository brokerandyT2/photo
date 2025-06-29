// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/PagedList.kt
package com.x3squaredcircles.pixmap.shared.application.common.models

/**
 * Represents a paginated list of items
 */
data class PagedList<T>(
    val items: List<T>,
    val totalCount: Int,
    val pageNumber: Int,
    val pageSize: Int
) {
    val totalPages: Int = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize
    val hasNextPage: Boolean = pageNumber < totalPages
    val hasPreviousPage: Boolean = pageNumber > 1
    val isFirstPage: Boolean = pageNumber == 1
    val isLastPage: Boolean = pageNumber == totalPages

    companion object {
        fun <T> createOptimized(
            items: List<T>,
            totalCount: Int,
            pageNumber: Int,
            pageSize: Int
        ): PagedList<T> {
            return PagedList(items, totalCount, pageNumber, pageSize)
        }

        fun <T> empty(): PagedList<T> {
            return PagedList(emptyList(), 0, 1, 10)
        }
    }

    fun <R> map(transform: (T) -> R): PagedList<R> {
        return PagedList(
            items = items.map(transform),
            totalCount = totalCount,
            pageNumber = pageNumber,
            pageSize = pageSize
        )
    }
}