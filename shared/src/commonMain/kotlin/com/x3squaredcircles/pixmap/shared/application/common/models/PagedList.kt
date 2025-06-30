// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/PagedList.kt
package com.x3squaredcircles.pixmap.shared.application.common.models

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/**
 * Represents a paged list of items with optimized construction
 */
@Serializable
data class PagedList<T>(
    /**
     * The items in the current page
     */
    val items: List<T>,

    /**
     * Total number of items across all pages
     */
    val totalCount: Int,

    /**
     * Current page number (1-based)
     */
    val pageNumber: Int,

    /**
     * Number of items per page
     */
    val pageSize: Int
) {
    /**
     * Total number of pages
     */
    val totalPages: Int = if (totalCount == 0) 0 else ceil(totalCount.toDouble() / pageSize).toInt()

    /**
     * Indicates whether there is a previous page
     */
    val hasPreviousPage: Boolean = pageNumber > 1

    /**
     * Indicates whether there is a next page
     */
    val hasNextPage: Boolean = pageNumber < totalPages

    /**
     * Indicates if this is the first page
     */
    val isFirstPage: Boolean = pageNumber == 1

    /**
     * Indicates if this is the last page
     */
    val isLastPage: Boolean = pageNumber >= totalPages

    companion object {
        /**
         * Creates an optimized paged list when you already have the paged items and total count
         * This is the preferred method for repository-level paging
         */
        fun <T> createOptimized(
            pagedItems: List<T>,
            totalCount: Int,
            pageNumber: Int,
            pageSize: Int
        ): PagedList<T> {
            return PagedList(pagedItems, totalCount, pageNumber, pageSize)
        }

        /**
         * Creates an empty paged list
         */
        fun <T> empty(): PagedList<T> {
            return PagedList(emptyList(), 0, 1, 10)
        }

        /**
         * Creates a paged list from a list source - AVOID IN PRODUCTION
         * This materializes the entire list in memory before paging
         */
        @Deprecated("Use repository-level paging for better performance")
        fun <T> create(source: List<T>, pageNumber: Int, pageSize: Int): PagedList<T> {
            val count = source.size
            val items = source
                .drop((pageNumber - 1) * pageSize)
                .take(pageSize)

            return PagedList(items, count, pageNumber, pageSize)
        }
    }

    /**
     * Maps the items in this paged list to a new type
     */
    fun <R> map(transform: (T) -> R): PagedList<R> {
        return PagedList(
            items = items.map(transform),
            totalCount = totalCount,
            pageNumber = pageNumber,
            pageSize = pageSize
        )
    }
}