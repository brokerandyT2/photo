// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/mappers/LocationMapperProfile.kt
package com.x3squaredcircles.pixmap.shared.application.mappers

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.dto.LocationListDto
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Mapper functions for Location entity transformations
 */
object LocationMapper {

    /**
     * Maps Location entity to LocationDto
     */
    fun Location.toDto(): LocationDto {
        return LocationDto(
            id = this.id,
            title = this.title,
            description = this.description,
            latitude = this.coordinate.latitude,
            longitude = this.coordinate.longitude,
            city = this.address.city,
            state = this.address.state,
            photoPath = this.photoPath,
            timestamp = this.timestamp,
            isDeleted = this.isDeleted
        )
    }

    /**
     * Maps Location entity to LocationListDto for list display
     */
    fun Location.toListDto(): LocationListDto {
        return LocationListDto(
            id = this.id,
            title = this.title,
            city = this.address.city,
            state = this.address.state,
            latitude = this.coordinate.latitude,
            longitude = this.coordinate.longitude,
            photoPath = this.photoPath,
            timestamp = this.timestamp,
            isDeleted = this.isDeleted
        )
    }

    /**
     * Maps LocationDto to Location entity
     */
    fun LocationDto.toEntity(): Location {
        return Location(
            title = this.title,
            description = this.description,
            coordinate = Coordinate(this.latitude, this.longitude),
            address = Address(this.city, this.state)
        ).apply {
            // Set other properties
            if (this@toEntity.photoPath != null) {
                attachPhoto(this@toEntity.photoPath)
            }
        }
    }

    /**
     * Maps a list of Location entities to LocationDto list
     */
    fun List<Location>.toLocationDtoList(): List<LocationDto> {
        return this.map { it.toDto() }
    }

    /**
     * Maps a list of Location entities to LocationListDto list
     */
    fun List<Location>.toLocationListDtoList(): List<LocationListDto> {
        return this.map { it.toListDto() }
    }

    /**
     * Maps a PagedList of Location entities to PagedList of LocationDto
     */
    fun PagedList<Location>.toLocationDtoPagedList(): PagedList<LocationDto> {
        return this.map { it.toDto() }
    }

    /**
     * Maps a PagedList of Location entities to PagedList of LocationListDto
     */
    fun PagedList<Location>.toLocationListDtoPagedList(): PagedList<LocationListDto> {
        return this.map { it.toListDto() }
    }

    /**
     * Bulk mapping for collections with null safety
     */
    fun List<Location>?.toLocationDtoListSafe(): List<LocationDto> {
        return this?.map { it.toDto() } ?: emptyList()
    }

    /**
     * Bulk mapping for collections with null safety
     */
    fun List<Location>?.toLocationListDtoListSafe(): List<LocationListDto> {
        return this?.map { it.toListDto() } ?: emptyList()
    }
}

/**
 * Extension functions for easier usage
 */
fun Location.toDto(): LocationDto = LocationMapper.run { toDto() }
fun Location.toListDto(): LocationListDto = LocationMapper.run { toListDto() }
fun LocationDto.toEntity(): Location = LocationMapper.run { toEntity() }
fun List<Location>.toLocationDtoList(): List<LocationDto> = LocationMapper.run { toLocationDtoList() }
fun List<Location>.toLocationListDtoList(): List<LocationListDto> = LocationMapper.run { toLocationListDtoList() }
fun PagedList<Location>.toLocationDtoPagedList(): PagedList<LocationDto> = LocationMapper.run { toLocationDtoPagedList() }
fun PagedList<Location>.toLocationListDtoPagedList(): PagedList<LocationListDto> = LocationMapper.run { toLocationListDtoPagedList() }