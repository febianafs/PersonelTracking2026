package com.example.personeltracking2026.core.map

object MapTypeManager {

    enum class MapType(val label: String) {
        STANDARD("Standard"),
        SATELLITE("Satellite"),
        TERRAIN("Terrain"),
        HYBRID("Hybrid")
    }

    // =========================
    // Pakai MAPLIBRE
    // =========================
    fun getStyleUrl(type: MapType): String {
        return when (type) {
            MapType.STANDARD ->
                "https://api.maptiler.com/maps/base-v4/style.json?key=LJfykuOhpb8qUA8Unzsr"

            MapType.SATELLITE ->
                "https://api.maptiler.com/maps/satellite-v4/style.json?key=LJfykuOhpb8qUA8Unzsr"

            MapType.TERRAIN ->
                "https://api.maptiler.com/maps/outdoor-v4/style.json?key=LJfykuOhpb8qUA8Unzsr"

            MapType.HYBRID ->
                "https://api.maptiler.com/maps/hybrid-v4/style.json?key=LJfykuOhpb8qUA8Unzsr"
        }
    }
}