package clipper2.clipper32.core

import clipper2.core.PointD
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.roundToInt

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
public class Point32(var x: Int, var y: Int) {

    @JsName("zero")
    constructor() : this(0, 0)

    @JsName("ofPoint64")
    constructor(pt: Point32) : this(pt.x, pt.y)

    @JsName("ofDouble")
    constructor(x: Double, y: Double) : this((x).roundToInt(), (y).roundToInt())

    @JsName("ofPointD")
    constructor(pt: PointD) : this((pt.x).roundToInt(), (pt.y).roundToInt())

    @JsName("ofPoint32Scaled")
    constructor(pt: Point32, scale: Double) : this((pt.x * scale).roundToInt(), (pt.y * scale).roundToInt())

    @JsName("ofPointDScaled")
    constructor(pt: PointD, scale: Double) : this((pt.x * scale).roundToInt(), (pt.y * scale).roundToInt())

    fun opEquals(o: Point32): Boolean {
        return x == o.x && y == o.y
    }

    fun opNotEquals(o: Point32): Boolean {
        return x != o.x || y != o.y
    }

    override fun toString(): String {
        return "($x,$y) " // nb: trailing space
    }

    override fun equals(other: Any?): Boolean {
        if (other is Point32) {
            return opEquals(this, other)
        }
        return false
    }

    override fun hashCode(): Int {
        val l: Int = x * 31 + y
        return l.hashCode()
    }

    fun clone(): Point32 {
        return Point32(x, y)
    }

    companion object {
        fun opEquals(lhs: Point32, rhs: Point32): Boolean {
            return lhs.x == rhs.x && lhs.y == rhs.y
        }

        fun opNotEquals(lhs: Point32, rhs: Point32): Boolean {
            return lhs.x != rhs.x || lhs.y != rhs.y
        }

        fun opAdd(lhs: Point32, rhs: Point32): Point32 {
            return Point32(lhs.x + rhs.x, lhs.y + rhs.y)
        }

        fun opSubtract(lhs: Point32, rhs: Point32): Point32 {
            return Point32(lhs.x - rhs.x, lhs.y - rhs.y)
        }
    }
}
