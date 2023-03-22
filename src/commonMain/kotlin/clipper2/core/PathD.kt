package clipper2.core

import kotlin.js.JsExport

/**
 * This structure contains a sequence of PointD vertices defining a single
 * contour (see also terminology). Paths may be open and represent a series of
 * line segments defined by 2 or more vertices, or they may be closed and
 * represent polygons. Whether or not a path is open depends on its context.
 * Closed paths may be 'outer' contours, or they may be 'hole' contours, and
 * this usually depends on their orientation (whether arranged roughly
 * clockwise, or arranged counter-clockwise).
 */
@JsExport
public class PathD : MutableList<PointD> by mutableListOf() {

    public override fun toString(): String {
        val bld: StringBuilder = StringBuilder()
        for (pt in this) {
            bld.append(pt.toString())
        }
        return bld.toString()
    }

    public fun asArray(): Array<PointD>{
        return this.toTypedArray()
    }

    companion object{
        public fun of(vararg elements: PointD): PathD{
            val path = PathD()
            path.addAll(elements)
            return path
            // return mutableListOf<PointD>(*elements) as PathD
        }

        public fun ofPath64(path: Path64): PathD{
            val result = PathD() //path.size
            for (pt in path) {
                result.add(PointD(pt))
            }
            return result
        }
    }
}