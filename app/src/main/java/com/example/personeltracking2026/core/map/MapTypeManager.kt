package com.example.personeltracking2026.core.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

object MapTypeManager {

    enum class MapType(val label: String) {
        STANDARD("Standard"),
        SATELLITE("Satellite"),
        TERRAIN("Terrain"),
        HYBRID("Hybrid")
    }

    fun getTileSource(type: MapType): ITileSource {
        return when (type) {
            MapType.STANDARD -> TileSourceFactory.MAPNIK

            MapType.SATELLITE -> XYTileSource(
                "EsriSatellite",
                0, 20, 256, ".jpg",
                arrayOf(
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
                )
            )

            MapType.TERRAIN -> XYTileSource(
                "OpenTopoMap",
                0, 17, 256, "",
                arrayOf(
                    "https://tile.opentopomap.org/"
                )
            )

            MapType.HYBRID -> XYTileSource(
                "EsriHybrid",
                0, 20, 256, ".png",
                arrayOf(
                    "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Reference_Overlay/MapServer/tile/"
                )
            )
        }
    }
}