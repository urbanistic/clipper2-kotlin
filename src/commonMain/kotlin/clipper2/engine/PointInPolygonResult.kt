package clipper2.engine

import kotlin.js.JsExport

@JsExport
public enum class PointInPolygonResult {
    IsOn, IsInside, IsOutside
}
