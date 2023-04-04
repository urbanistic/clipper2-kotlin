package clipper2.clipper32.core

import clipper2.core.PathD
import kotlin.js.JsExport

/**
 * This structure contains a sequence of Point32 vertices defining a single
 * contour (see also terminology). Paths may be open and represent a series of
 * line segments defined by 2 or more vertices, or they may be closed and
 * represent polygons. Whether or not a path is open depends on its context.
 * Closed paths may be 'outer' contours, or they may be 'hole' contours, and
 * this usually depends on their orientation (whether arranged roughly
 * clockwise, or arranged counter-clockwise).
 */

@JsExport
public class Path32 : MutableList<Point32> by mutableListOf() {

    public override fun toString(): String {
        val bld = StringBuilder()
        for (pt in this) {
            bld.append(pt.toString())
        }
        return bld.toString()
    }

    public fun asArray(): Array<Point32>{
        return this.toTypedArray()
    }

    companion object{
        public fun of(vararg elements: Point32): Path32 {
            val path = Path32()
            path.addAll(elements)
            return path
            // return mutableListOf<Point32>(*elements) as Path32
        }
        public fun ofPathD(path: PathD): Path32 {
            val result = Path32() //path.size
            for (pt in path) {
                result.add(Point32(pt))
            }
            return result
        }
    }
}
