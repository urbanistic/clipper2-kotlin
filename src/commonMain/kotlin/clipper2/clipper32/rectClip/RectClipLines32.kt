@file:Suppress("unused")

package clipper2.clipper32.rectClip

import Clipper.getBounds
import clipper2.clipper32.core.Path32
import clipper2.clipper32.core.Paths32
import clipper2.clipper32.core.Point32
import clipper2.clipper32.core.Rect32
import tangible.RefObject

/**
 * ExecuteRectClipLines intersects subject open paths (polylines) with the specified
 * rectangular clipping region.
 *
 *
 * This function is extremely fast when compared to the Library's general
 * purpose Intersect clipper. Where Intersect has roughly O(n³) performance,
 * ExecuteRectClipLines has O(n) performance.
 *
 * @since 1.0.6
 */
class RectClipLines32(rect: Rect32?) : RectClip32(rect!!) {
    fun execute(paths: Paths32): Paths32 {
        val result = Paths32()
        if (rect.isEmpty()) {
            return result
        }
        for (path in paths) {
            if (path.size < 2) {
                continue
            }
            pathBounds = getBounds(path)
            if (!rect.intersects(pathBounds!!)) {
                continue // the path must be completely outside fRect
            }
            // Apart from that, we can't be sure whether the path
            // is completely outside or completed inside or intersects
            // fRect, simply by comparing path bounds with fRect.
            executeInternal(path)
            for (op in results) {
                val tmp = getPath(op!!)
                if (!tmp.isEmpty()) {
                    result.add(tmp)
                }
            }

            // clean up after every loop
            results.clear()
            for (i in 0..7) {
                edges.get(i).clear()
            }
        }
        return result
    }

    private fun getPath(op: OutPt2?): Path32 {
        var op: OutPt2? = op
        val result = Path32()
        if (op == null || op == op.next) {
            return result
        }
        op = op.next // starting at path beginning
        result.add(op!!.pt)
        var op2 = op.next
        while (op2 != op) {
            result.add(op2!!.pt)
            op2 = op2.next
        }
        return result
    }

    private fun executeInternal(path: Path32) {
        results.clear()
        if (path.size < 2 || rect.isEmpty()) {
            return
        }
        val prev = RefObject(Location.INSIDE)
        val i = RefObject(1)
        val highI = path.size - 1
        val loc = RefObject<Location>(null)
        if (!getLocation(rect, path[0], loc)) {
            while (i.argValue!! <= highI && !getLocation(rect, path[i.argValue!!], prev)) {
                i.argValue = i.argValue!! + 1
            }
            if (i.argValue!! > highI) {
                for (pt in path) {
                    add(pt)
                }
            }
            if (prev.argValue === Location.INSIDE) {
                loc.argValue = Location.INSIDE
            }
            i.argValue = 1
        }
        if (loc.argValue === Location.INSIDE) {
            add(path[0])
        }

        // /////////////////////////////////////////////////
        while (i.argValue!! <= highI) {
            prev.argValue = loc.argValue
            getNextLocation(path, loc, i, highI)
            if (i.argValue!! > highI) {
                break
            }
            val prevPt = path[i.argValue!! - 1]
            val crossingLoc = RefObject(loc.argValue)
            val ip = Point32()
            if (!getIntersection(rectPath, path[i.argValue!!], prevPt, crossingLoc, ip)) {
                // ie remaining outside (& crossingLoc still == loc)
                i.argValue = i.argValue!! + 1
                continue
            }

            // //////////////////////////////////////////////////
            // we must be crossing the rect boundary to get here
            // //////////////////////////////////////////////////
            if (loc.argValue === Location.INSIDE) // path must be entering rect
                {
                    add(ip)
                } else if (prev.argValue !== Location.INSIDE) {
                // passing right through rect. 'ip' here will be the second
                // intersect pt but we'll also need the first intersect pt (ip2)
                crossingLoc.argValue = prev.argValue
                val ip2 = Point32()
                getIntersection(rectPath, prevPt, path[i.argValue!!], crossingLoc, ip2)
                add(ip2)
                add(ip)
            } else // path must be exiting rect
                {
                    add(ip)
                }
        }
    }
}
