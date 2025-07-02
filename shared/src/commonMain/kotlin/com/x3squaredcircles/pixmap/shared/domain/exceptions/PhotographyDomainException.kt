package com.x3squaredcircles.pixmap.shared.domain.exceptions

import com.x3squaredcircles.pixmap.shared.domain.exceptions.DomainException

class PhotographyDomainException(
    code: String,
    message: String,
    cause: Throwable? = null
) : DomainException(code, message, cause) {

    override fun getUserFriendlyMessage(): String {
        return when (code) {
            "INVALID_CAMERA_NAME" -> "Camera name cannot be empty."
            "INVALID_LENS_NAME" -> "Lens name cannot be empty."
            "INVALID_FOCAL_LENGTH" -> "Focal length must be a positive number."
            "INVALID_APERTURE" -> "Aperture value must be positive."
            "CAMERA_NOT_FOUND" -> "The requested camera could not be found."
            "LENS_NOT_FOUND" -> "The requested lens could not be found."
            "INCOMPATIBLE_MOUNT" -> "This lens is not compatible with the selected camera mount."
            "DATABASE_ERROR" -> "There was a problem saving the photography equipment. Please try again."
            else -> "An error occurred while managing photography equipment: $message"
        }
    }
}