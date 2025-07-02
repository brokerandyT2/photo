//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/common/models/PagedList.kt

package com.x3squaredcircles.pixmap.shared.application.common.models

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/**
 * Represents a paginated list of items with optimized construction
 */
@Serializable
data class PagedList<T>(
    val items: List<T>,
    val totalCount: Int,
    val pageNumber: Int,
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
    val isLastPage: Boolean = pageNumber == totalPages

    /**
     * Gets the start index for the current page (0-based)
     */
    val startIndex: Int = (pageNumber - 1) * pageSize

    /**
     * Gets the end index for the current page (0-based, exclusive)
     */
    val endIndex: Int = minOf(startIndex + pageSize, totalCount)

    /**
     * Gets the number of items on the current page
     */
    val currentPageCount: Int = items.size

    /**
     * Checks if the list is empty
     */
    val isEmpty: Boolean = items.isEmpty()

    /**
     * Checks if the list has items
     */
    val isNotEmpty: Boolean = items.isNotEmpty()

    companion object {
        /**
         * Creates an optimized paged list when you already have the paged items and total count
         * This is the preferred method for repository-level paging
         */
        fun <T> createOptimized(
            items: List<T>,
            totalCount: Int,
            pageNumber: Int,
            pageSize: Int
        ): PagedList<T> {
            require(pageNumber > 0) { "Page number must be greater than 0" }
            require(pageSize > 0) { "Page size must be greater than 0" }
            require(totalCount >= 0) { "Total count cannot be negative" }

            return PagedList(items, totalCount, pageNumber, pageSize)
        }

        /**
         * Creates an empty paged list
         */
        fun <T> empty(pageSize: Int = 10): PagedList<T> {
            return PagedList(emptyList(), 0, 1, pageSize)
        }

        /**
         * Creates a single page list from all items
         */
        fun <T> singlePage(items: List<T>): PagedList<T> {
            return PagedList(items, items.size, 1, items.size.coerceAtLeast(1))
        }
    }

    /**
     * Transforms the items in this paged list using the provided transform function
     */
    fun <R> map(transform: (T) -> R): PagedList<R> {
        return PagedList(
            items = items.map(transform),
            totalCount = totalCount,
            pageNumber = pageNumber,
            pageSize = pageSize
        )
    }

    /**
     * Filters the items in this paged list (note: this doesn't change pagination metadata)
     */
    fun filter(predicate: (T) -> Boolean): PagedList<T> {
        return copy(items = items.filter(predicate))
    }

    /**
     * Gets a specific item by index within the current page
     */
    fun getItem(index: Int): T? {
        return items.getOrNull(index)
    }

    /**
     * Gets the first item in the current page, or null if empty
     */
    fun firstOrNull(): T? = items.firstOrNull()

    /**
     * Gets the last item in the current page, or null if empty
     */
    fun lastOrNull(): T? = items.lastOrNull()

    /**
     * Gets a display string for the current page range
     */
    fun getPageRangeDisplay(): String {
        if (isEmpty) return "0 of 0"

        val start = startIndex + 1
        val end = endIndex
        return "$start-$end of $totalCount"
    }

    /**
     * Gets pagination info as a string
     */
    fun getPaginationInfo(): String {
        return "Page $pageNumber of $totalPages (${getPageRangeDisplay()})"
    }

    /**
     * Creates a copy with different items but same pagination metadata
     */
    fun <R> withItems(newItems: List<R>): PagedList<R> {
        return PagedList(newItems, totalCount, pageNumber, pageSize)
    }

    /**
     * Creates a copy with a different page number
     */
    fun withPageNumber(newPageNumber: Int): PagedList<T> {
        require(newPageNumber > 0) { "Page number must be greater than 0" }
        return copy(pageNumber = newPageNumber)
    }

    /**
     * Creates a copy with a different page size
     */
    fun withPageSize(newPageSize: Int): PagedList<T> {
        require(newPageSize > 0) { "Page size must be greater than 0" }
        return copy(pageSize = newPageSize)
    }
}