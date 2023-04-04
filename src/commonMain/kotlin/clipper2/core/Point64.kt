package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.roundToLong

/**
 * The Point64 structure is used to represent a single vertex (or coordinate) in
 * a series that together make a path or contour (see Path64). Closed paths are
 * usually referred to as polygons, and open paths are referred to as lines or
 * polylines.
 *
 *
 * All coordinates are represented internally using integers as this is the only
 * way to ensure numerical robustness. While the library also accepts floating
 * point coordinates (see PointD), these will be converted into integers
 * internally (using user specified scaling).
 */
@JsExport
class Point64(var x: Long, var y: Long) {

    @JsName("zero")
    constructor() : this(0, 0)

    @JsName("ofPoint64")
    constructor(pt: Point64) : this(pt.x, pt.y)

    @JsName("ofDouble")
    constructor(x: Double, y: Double) : this((x).roundToLong(), (y).roundToLong())

    @JsName("ofPointD")
    constructor(pt: PointD) : this((pt.x).roundToLong(), (pt.y).roundToLong())

    @JsName("ofPoint64Scaled")
    constructor(pt: Point64, scale: Double) : this((pt.x * scale).roundToLong(), (pt.y * scale).roundToLong())

    @JsName("ofPointDScaled")
    constructor(pt: PointD, scale: Double) : this((pt.x * scale).roundToLong(), (pt.y * scale).roundToLong())

    fun opEquals(o: Point64): Boolean {
        return x == o.x && y == o.y
    }

    fun opNotEquals(o: Point64): Boolean {
        return x != o.x || y != o.y
    }

    override fun toString(): String {
        return "($x,$y) " // nb: trailing space
    }

    override fun equals(other: Any?): Boolean {
        if (other is Point64) {
            return opEquals(this, other)
        }
        return false
    }

    override fun hashCode(): Int {
        val l: Long = x * 31 + y
        return l.hashCode()
    }

    fun clone(): Point64 {
        return Point64(x, y)
    }

    companion object {
        fun opEquals(lhs: Point64, rhs: Point64): Boolean {
            return lhs.x == rhs.x && lhs.y == rhs.y
        }

        fun opNotEquals(lhs: Point64, rhs: Point64): Boolean {
            return lhs.x != rhs.x || lhs.y != rhs.y
        }

        fun opAdd(lhs: Point64, rhs: Point64): Point64 {
            return Point64(lhs.x + rhs.x, lhs.y + rhs.y)
        }

        fun opSubtract(lhs: Point64, rhs: Point64): Point64 {
            return Point64(lhs.x - rhs.x, lhs.y - rhs.y)
        }
    }
}
