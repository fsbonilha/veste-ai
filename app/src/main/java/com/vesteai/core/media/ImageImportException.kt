package com.vesteai.core.media

sealed class ImageImportException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Dimensions are after EXIF rotation. */
    class TooSmall(val width: Int, val height: Int) : ImageImportException(
        "Image is ${width}x$height px; minimum is " +
            "${ImageNormalizer.MIN_WIDTH}x${ImageNormalizer.MIN_HEIGHT} px",
    )

    class Undecodable : ImageImportException("Source is not a decodable image")

    class Unreadable(cause: Throwable) :
        ImageImportException("Image could not be read or written", cause)
}
