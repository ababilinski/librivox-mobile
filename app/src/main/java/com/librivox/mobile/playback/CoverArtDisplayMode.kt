package com.librivox.mobile.playback

enum class CoverArtDisplayMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val frameAspectRatio: Float,
    val cropsImage: Boolean,
) {
    Uncropped(
        preferenceValue = "uncropped",
        label = "Uncropped",
        description = "Show the full cover image without trimming the edges.",
        frameAspectRatio = 2f / 3f,
        cropsImage = false,
    ),
    CropSquare(
        preferenceValue = "crop_square",
        label = "Square crop",
        description = "Crop covers into the current square artwork frame.",
        frameAspectRatio = 1f,
        cropsImage = true,
    ),
    CropFourFive(
        preferenceValue = "crop_4_5",
        label = "4:5 crop",
        description = "Crop covers into a tall 4:5 artwork frame.",
        frameAspectRatio = 4f / 5f,
        cropsImage = true,
    ),
    CropTwoThree(
        preferenceValue = "crop_2_3",
        label = "2:3 crop",
        description = "Crop covers into a classic 2:3 book-cover frame.",
        frameAspectRatio = 2f / 3f,
        cropsImage = true,
    );

    companion object {
        val Default: CoverArtDisplayMode = CropSquare

        fun fromPreference(value: String?): CoverArtDisplayMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}
