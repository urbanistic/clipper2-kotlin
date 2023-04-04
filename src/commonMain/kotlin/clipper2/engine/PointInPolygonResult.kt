package clipper2.engine

import kotlin.js.JsExport

@JsExport
enum class PointInPolygonResult {
    IsOn, IsInside, IsOutside
}
