// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/persistence/ISqliteSpecification.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.persistence

/**
 * Specification interface for SQLite queries
 */
interface ISqliteSpecification<T> {
    val whereClause: String
    val parameters: Map<String, Any>
    val orderBy: String?
    val take: Int?
    val skip: Int?
    val joins: String?
}