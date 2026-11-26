@file:Suppress(
    "MagicNumber",
    "LongParameterList",
)

package app.openhealth.android.activity

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.openhealth.core.activity.ActivityPoint
import app.openhealth.ui.theme.OhTheme
import kotlin.math.max
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

/**
 * The route on a map (`pr.md` §6.11). With a PMTiles pack installed, MapLibre draws the
 * OpenStreetMap basemap and the route; without one, the same route is drawn on the
 * neutral grid, so an activity is never blocked on a map download. The style uses the
 * theme's existing surface ramp and accent, never a new colour.
 */
@Composable
fun ActivityRouteMap(
    points: List<ActivityPoint>,
    modifier: Modifier = Modifier,
    packPath: String? = null,
    height: Int = 220,
) {
    if (packPath == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(OhTheme.colors.surface2),
        ) {
            RouteCanvas(points = points, modifier = Modifier.fillMaxSize())
        }
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val background = OhTheme.colors.bg
    val mapView = remember {
        runCatching {
            MapLibre.getInstance(context)
            MapView(
                context,
                MapLibreMapOptions.createFromAttributes(context).textureMode(true),
            ).apply {
                onCreate(null)
                setBackgroundColor(background.toArgb())
            }
        }.getOrNull()
    }
    if (mapView == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(OhTheme.colors.surface2),
        ) {
            RouteCanvas(points = points, modifier = Modifier.fillMaxSize())
        }
        return
    }
    val land = OhTheme.colors.surface2
    val water = OhTheme.colors.surface3
    val road = OhTheme.colors.sep
    val boundary = OhTheme.colors.sepOp
    val routeInk = OhTheme.colors.acc
    var projected by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val routeStroke = OhTheme.dims.chartLine
    val routeDot = OhTheme.space.s1
    val startInk = OhTheme.colors.greenInk
    val endInk = OhTheme.colors.label
    val style = remember(packPath, background, land, water, road, boundary) {
        mapStyle(
            packPath = packPath,
            background = background,
            land = land,
            water = water,
            road = road,
            boundary = boundary,
        )
    }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
        }
    }
    LaunchedEffect(mapView, style) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(style)) { loaded ->
                Log.d(TAG, "style loaded; points=${points.size}")
                runCatching {
                    addRoute(loaded, points, routeInk)
                    fitRoute(map, points)
                    fun project() {
                        projected = runCatching {
                            points.map { point ->
                                val screen = map.projection.toScreenLocation(
                                    LatLng(point.latitude, point.longitude),
                                )
                                Offset(screen.x, screen.y)
                            }
                        }.getOrDefault(emptyList())
                    }
                    map.addOnCameraIdleListener { project() }
                    mapView.post { project() }
                    Log.d(
                        TAG,
                        "route layer=${loaded.getLayer("route-line") != null} " +
                            "zoom=${map.cameraPosition.zoom} " +
                            "target=${map.cameraPosition.target}",
                    )
                }.onFailure { Log.e(TAG, "route layer failed", it) }
            }
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.matchParentSize(),
        )
        if (projected.size >= 2) {
            Canvas(Modifier.matchParentSize()) {
                val path = Path()
                projected.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, routeInk, style = Stroke(width = routeStroke.toPx()))
                drawCircle(startInk, routeDot.toPx(), projected.first())
                drawCircle(endInk, routeDot.toPx(), projected.last())
            }
        }
    }
}

private const val TAG = "ActivityRouteMap"

private fun addRoute(style: Style, points: List<ActivityPoint>, color: Color) {
    if (points.size < 2) return
    val line = LineString.fromLngLats(
        points.map {
            org.maplibre.geojson.Point.fromLngLat(it.longitude, it.latitude)
        },
    )
    val source =
        GeoJsonSource(
            "route-source",
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line))),
        )
    style.addSource(source)
    val layer = LineLayer("route-line", "route-source").withProperties(
        PropertyFactory.lineColor(colourHex(color)),
        PropertyFactory.lineWidth(3.5f),
        PropertyFactory.lineJoin("round"),
        PropertyFactory.lineCap("round"),
    )
    style.addLayer(layer)
}

private fun fitRoute(map: org.maplibre.android.maps.MapLibreMap, points: List<ActivityPoint>) {
    if (points.isEmpty()) return
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 48))
    }.onFailure {
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(points.first().latitude, points.first().longitude))
            .zoom(12.0)
            .build()
    }
}

@Composable
private fun RouteCanvas(points: List<ActivityPoint>, modifier: Modifier) {
    val ink = OhTheme.colors.acc
    val grid = OhTheme.colors.sepOp
    val start = OhTheme.colors.greenInk
    val end = OhTheme.colors.label
    val stroke = if (OhTheme.dims.chartLine > OhTheme.dims.bodyMapStroke) {
        OhTheme.dims.chartLine
    } else {
        OhTheme.dims.bodyMapStroke
    }
    val dot = OhTheme.space.s1
    Canvas(modifier = modifier) {
        val cols = 6
        val rows = 4
        for (index in 1 until cols) {
            val x = size.width * index / cols
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        for (index in 1 until rows) {
            val y = size.height * index / rows
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = max(maxLat - minLat, 0.0005)
        val lonSpan = max(maxLon - minLon, 0.0005)
        val padding = size.minDimension * 0.08f
        fun pointOf(point: ActivityPoint): Offset {
            val x = ((point.longitude - minLon) / lonSpan).toFloat()
            val y = 1f - ((point.latitude - minLat) / latSpan).toFloat()
            return Offset(
                padding + x * (size.width - padding * 2),
                padding + y * (size.height - padding * 2),
            )
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val offset = pointOf(point)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, ink, style = Stroke(width = stroke.toPx()))
        drawCircle(start, radius = dot.toPx(), center = pointOf(points.first()))
        drawCircle(end, radius = dot.toPx(), center = pointOf(points.last()))
    }
}

private fun mapStyle(
    packPath: String,
    background: Color,
    land: Color,
    water: Color,
    road: Color,
    boundary: Color,
): String {
    val url = "pmtiles://" + Uri.fromFile(java.io.File(packPath))
    return """
        {
          "version": 8,
          "name": "openHealth",
          "sources": {
            "basemap": { "type": "vector", "url": "$url" }
          },
          "layers": [
            { "id": "background", "type": "background",
              "paint": { "background-color": "${colourHex(background)}" } },
            { "id": "landuse", "type": "fill", "source": "basemap",
              "source-layer": "landuse",
              "paint": { "fill-color": "${colourHex(land)}" } },
            { "id": "water", "type": "fill", "source": "basemap",
              "source-layer": "water",
              "paint": { "fill-color": "${colourHex(water)}" } },
            { "id": "boundary", "type": "line", "source": "basemap",
              "source-layer": "boundary",
              "paint": { "line-color": "${colourHex(boundary)}", "line-width": 0.6 } },
            { "id": "road", "type": "line", "source": "basemap",
              "source-layer": "transportation",
              "paint": { "line-color": "${colourHex(road)}",
                "line-width": ["interpolate", ["linear"], ["zoom"], 6, 0.5, 14, 2.2] } }
          ]
        }
    """.trimIndent()
}

private fun colourHex(colour: Color): String {
    // Compose packs ARGB in the low 32 bits of Color.value; the upper bits carry the
    // colour space. Shift the RGB bytes, not the colour-space header.
    val argb = colour.value.toLong() and 0xFFFFFFFFL
    val red = ((argb shr 16) and 0xFF).toInt()
    val green = ((argb shr 8) and 0xFF).toInt()
    val blue = (argb and 0xFF).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}
