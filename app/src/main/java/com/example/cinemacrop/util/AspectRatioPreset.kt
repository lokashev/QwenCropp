package com.example.cinemacrop.util

/**
 * Доступные кинематографические соотношения сторон.
 */
enum class AspectRatioPreset(
    val title: String,
    val ratio: Float,          // ширина / высота
    val description: String
) {
    ORIGINAL("Оригинал", 0f, "Исходное соотношение"),
    IMAX("IMAX", 1.43f, "1.43:1 — IMAX"),
    FOUR_THREE("4:3", 4f / 3f, "4:3 — классика ТВ"),
    ONE_ONE("1:1", 1f, "1:1 — квадрат"),
    FULL_VISION("1.66:1", 1.66f, "1.66:1 — Full Vision"),
    SD_TV("16:9", 16f / 9f, "16:9 — HDTV"),
    UNIVISIUM("1.85:1", 1.85f, "1.85:1 — Widescreen"),
    SCOPE("2.39:1", 2.39f, "2.39:1 — Anamorphic Scope"),
    CINERAMA("2.59:1", 2.59f, "2.59:1 — Cinerama"),
    ULTRAWIDE("21:9", 21f / 9f, "21:9 — Ultrawide");
}
