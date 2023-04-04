@file:Suppress("unused")

package clipper2.core

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The PointD structure is used to represent a single floating point coordinate.
 * A series of these coordinates forms a PathD structure.
 */
@JsExport
class PointD(var x: Double = 0.0, var y: Double = 0.0) {

    @JsName("zero")
    constructor() : this(0.0, 0.0)

    @JsName("ofPointD")
    constructor(pt: PointD) : this(pt.x, pt.y)

    @JsName("ofPoint64")
    constructor(pt: Point64) : this(pt.x.toDouble(), pt.y.toDouble())

    @JsName("ofPointDScale")
    constructor(pt: PointD, scale: Double) : this(pt.x * scale, pt.y * scale)

    @JsName("ofPoint64Scale")
    constructor(pt: Point64, scale: Double) : this(pt.x * scale, pt.y * scale)

    @JsName("ofLong")
    constructor(x: Long, y: Long) : this(x.toDouble(), y.toDouble())

    fun negate() {
        x = -x
        y = -y
    }

    override fun toString(): String {
        return "($x,$y) "
    }

    override fun equals(other: Any?): Boolean {
        if (other is PointD) {
            return opEquals(this, other)
        }
        return false
    }

    override fun hashCode(): Int {
        val d: Double = (x * 31 + y)
        return d.hashCode()
    }

    fun clone(): PointD {
        return PointD(x, y)
    }

    companion object {
        fun opEquals(lhs: PointD, rhs: PointD): Boolean {
            return InternalClipper.isAlmostZero(lhs.x - rhs.x) && InternalClipper.isAlmostZero(lhs.y - rhs.y)
        }

        fun opNotEquals(lhs: PointD, rhs: PointD): Boolean {
            return !InternalClipper.isAlmostZero(lhs.x - rhs.x) || !InternalClipper.isAlmostZero(lhs.y - rhs.y)
        }
    }
}
