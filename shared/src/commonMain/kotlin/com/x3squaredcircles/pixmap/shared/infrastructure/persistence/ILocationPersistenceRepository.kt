// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/ILocationPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

interface ILocationPersistenceRepository {
    suspend fun getByIdAsync(id: Int): Location?
    suspend fun getAllAsync(): List<Location>
    suspend fun getActiveAsync(): List<Location>
    suspend fun addAsync(location: Location): Location
    suspend fun updateAsync(location: Location)
    suspend fun deleteAsync(location: Location)
    suspend fun getByTitleAsync(title: String): Location?
    suspend fun getNearbyAsync(latitude: Double, longitude: Double, distanceKm: Double): List<Location>
    suspend fun getPagedAsync(pageNumber: Int, pageSize: Int, searchTerm: String?, includeDeleted: Boolean): PagedList<Location>
    suspend fun getPagedProjectedAsync(pageNumber: Int, pageSize: Int, selectColumns: String, whereClause: String?, parameters: Map<String, Any>?, orderBy: String?): PagedList<Map<String, Any?>>
    suspend fun getActiveProjectedAsync(selectColumns: String, additionalWhere: String?, parameters: Map<String, Any>?): List<Map<String, Any?>>
    suspend fun getNearbyProjectedAsync(latitude: Double, longitude: Double, distanceKm: Double, selectColumns: String): List<Map<String, Any?>>
    suspend fun getByIdProjectedAsync(id: Int, selectColumns: String): Map<String, Any?>?
    suspend fun getBySpecificationAsync(specification: ISqliteSpecification<Location>): List<Location>
    suspend fun getPagedBySpecificationAsync(specification: ISqliteSpecification<Location>, pageNumber: Int, pageSize: Int, selectColumns: String): PagedList<Map<String, Any?>>
    suspend fun createBulkAsync(locations: List<Location>): List<Location>
    suspend fun updateBulkAsync(locations: List<Location>): Int
    suspend fun countAsync(whereClause: String?, parameters: Map<String, Any>?): Int
    suspend fun existsAsync(whereClause: String, parameters: Map<String, Any>): Boolean
    suspend fun existsByIdAsync(id: Int): Boolean
    suspend fun executeQueryAsync(sql: String, parameters: Map<String, Any>?): List<Map<String, Any?>>
    suspend fun executeCommandAsync(sql: String, parameters: Map<String, Any>?): Int
}

interface IWeatherPersistenceRepository {
    suspend fun getByIdAsync(id: Int): com.x3squaredcircles.pixmap.shared.domain.entities.Weather?
    suspend fun getByLocationIdAsync(locationId: Int): com.x3squaredcircles.pixmap.shared.domain.entities.Weather?
    suspend fun addAsync(weather: com.x3squaredcircles.pixmap.shared.domain.entities.Weather): com.x3squaredcircles.pixmap.shared.domain.entities.Weather
    suspend fun updateAsync(weather: com.x3squaredcircles.pixmap.shared.domain.entities.Weather)
    suspend fun deleteAsync(weather: com.x3squaredcircles.pixmap.shared.domain.entities.Weather)
    suspend fun getRecentAsync(count: Int): List<com.x3squaredcircles.pixmap.shared.domain.entities.Weather>
    suspend fun getExpiredAsync(maxAge: kotlin.time.Duration): List<com.x3squaredcircles.pixmap.shared.domain.entities.Weather>
    suspend fun createBulkAsync(weatherRecords: List<com.x3squaredcircles.pixmap.shared.domain.entities.Weather>): List<com.x3squaredcircles.pixmap.shared.domain.entities.Weather>
    suspend fun deleteExpiredAsync(maxAge: kotlin.time.Duration): Int
}

interface ISettingPersistenceRepository {
    suspend fun getByIdAsync(id: Int): com.x3squaredcircles.pixmap.shared.domain.entities.Setting?
    suspend fun getByKeyAsync(key: String): com.x3squaredcircles.pixmap.shared.domain.entities.Setting?
    suspend fun getAllAsync(): List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>
    suspend fun getByKeysAsync(keys: List<String>): List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>
    suspend fun addAsync(setting: com.x3squaredcircles.pixmap.shared.domain.entities.Setting): com.x3squaredcircles.pixmap.shared.domain.entities.Setting
    suspend fun updateAsync(setting: com.x3squaredcircles.pixmap.shared.domain.entities.Setting)
    suspend fun deleteAsync(setting: com.x3squaredcircles.pixmap.shared.domain.entities.Setting)
    suspend fun upsertAsync(key: String, value: String, description: String?): com.x3squaredcircles.pixmap.shared.domain.entities.Setting
    suspend fun getAllAsDictionaryAsync(): Map<String, String>
    suspend fun getByPrefixAsync(keyPrefix: String): List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>
    suspend fun getRecentlyModifiedAsync(count: Int): List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>
    suspend fun existsAsync(key: String): Boolean
    suspend fun createBulkAsync(settings: List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>): List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>
    suspend fun updateBulkAsync(settings: List<com.x3squaredcircles.pixmap.shared.domain.entities.Setting>): Int
    suspend fun deleteBulkAsync(keys: List<String>): Int
    suspend fun upsertBulkAsync(keyValuePairs: Map<String, String>): Map<String, String>
}

interface ITipPersistenceRepository {
    suspend fun getByIdAsync(id: Int): com.x3squaredcircles.pixmap.shared.domain.entities.Tip?
    suspend fun getAllAsync(): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun getByTipTypeIdAsync(tipTypeId: Int): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun addAsync(tip: com.x3squaredcircles.pixmap.shared.domain.entities.Tip): com.x3squaredcircles.pixmap.shared.domain.entities.Tip
    suspend fun updateAsync(tip: com.x3squaredcircles.pixmap.shared.domain.entities.Tip)
    suspend fun deleteAsync(tip: com.x3squaredcircles.pixmap.shared.domain.entities.Tip)
    suspend fun getByTitleAsync(title: String): com.x3squaredcircles.pixmap.shared.domain.entities.Tip?
    suspend fun getRandomByTypeAsync(tipTypeId: Int): com.x3squaredcircles.pixmap.shared.domain.entities.Tip?
    suspend fun searchAsync(searchTerm: String): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun getByTipTypeIdWithPaginationAsync(tipTypeId: Int, pageNumber: Int, pageSize: Int): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun getCountByTipTypeIdAsync(tipTypeId: Int): Int
    suspend fun getRecentAsync(count: Int): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun createBulkAsync(tips: List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>): List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>
    suspend fun updateBulkAsync(tips: List<com.x3squaredcircles.pixmap.shared.domain.entities.Tip>): Int
    suspend fun deleteBulkAsync(tipIds: List<Int>): Int
    suspend fun deleteByTipTypeIdAsync(tipTypeId: Int): Int
    suspend fun getProjectedAsync(selectColumns: String, whereClause: String?, parameters: Map<String, Any>?, orderBy: String?, limit: Int?): List<Map<String, Any?>>
    suspend fun getTipCountsByTypeAsync(): Map<Int, Int>
    suspend fun getPopularCameraSettingsAsync(limit: Int): List<String>
}

interface ITipTypePersistenceRepository {
    suspend fun getByIdAsync(id: Int): com.x3squaredcircles.pixmap.shared.domain.entities.TipType?
    suspend fun getAllAsync(): List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>
    suspend fun addAsync(tipType: com.x3squaredcircles.pixmap.shared.domain.entities.TipType): com.x3squaredcircles.pixmap.shared.domain.entities.TipType
    suspend fun updateAsync(tipType: com.x3squaredcircles.pixmap.shared.domain.entities.TipType)
    suspend fun deleteAsync(tipType: com.x3squaredcircles.pixmap.shared.domain.entities.TipType)
    suspend fun getByNameAsync(name: String): com.x3squaredcircles.pixmap.shared.domain.entities.TipType?
    suspend fun getWithTipsAsync(id: Int): com.x3squaredcircles.pixmap.shared.domain.entities.TipType?
    suspend fun getAllWithTipsAsync(): List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>
    suspend fun getActiveWithTipCountsAsync(): List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>
    suspend fun getTipCountsByTipTypeAsync(): Map<Int, Int>
    suspend fun createBulkAsync(tipTypes: List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>): List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>
    suspend fun updateBulkAsync(tipTypes: List<com.x3squaredcircles.pixmap.shared.domain.entities.TipType>): Int
    suspend fun deleteBulkAsync(tipTypeIds: List<Int>): Int
}

interface ISqliteSpecification<T> {
    val whereClause: String
    val parameters: Map<String, Any>
    val orderBy: String?
    val take: Int?
    val skip: Int?
    val joins: String?
}