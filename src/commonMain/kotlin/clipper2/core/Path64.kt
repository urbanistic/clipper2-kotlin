@file:Suppress("unused")

package clipper2.core

import kotlin.js.JsExport

/**
 * This structure contains a sequence of Point64 vertices defining a single
 * contour (see also terminology). Paths may be open and represent a series of
 * line segments defined by 2 or more vertices, or they may be closed and
 * represent polygons. Whether or not a path is open depends on its context.
 * Closed paths may be 'outer' contours, or they may be 'hole' contours, and
 * this usually depends on their orientation (whether arranged roughly
 * clockwise, or arranged counter-clockwise).
 */

@JsExport
class Path64 : MutableList<Point64> by mutableListOf() {

    override fun toString(): String {
        val bld = StringBuilder()
        for (pt in this) {
            bld.append(pt.toString())
        }
        return bld.toString()
    }

    fun asArray(): Array<Point64> {
        return this.toTypedArray()
    }

    companion object {
        fun of(vararg elements: Point64): Path64 {
            val path = Path64()
            path.addAll(elements)
            return path
            // return mutableListOf<Point64>(*elements) as Path64
        }
        fun ofPathD(path: PathD): Path64 {
            val result = Path64() // path.size
            for (pt in path) {
                result.add(Point64(pt))
            }
            return result
        }
    }
}
