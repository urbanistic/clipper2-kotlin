package clipper2.offset

import kotlin.js.JsExport

/**
 * The EndType enumerator is only needed when offsetting (inflating/shrinking).
 * It isn't needed for polygon clipping.
 *
 *
 * EndType has 5 values:
 *
 *  * **Polygon**: the path is treated as a polygon
 *  * **Join**: ends are joined and the path treated as a polyline
 *  * **Square**: ends extend the offset amount while being squared off
 *  * **Round**: ends extend the offset amount while being rounded off
 *  * **Butt**: ends are squared off without any extension
 *
 * With both EndType.Polygon and EndType.Join, path closure will occur
 * regardless of whether or not the first and last vertices in the path match.
 */
@JsExport
enum class EndType {
    Polygon, Joined, Butt, Square, Round
}