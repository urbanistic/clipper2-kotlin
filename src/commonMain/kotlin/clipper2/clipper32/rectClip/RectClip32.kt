package clipper2.clipper32.rectClip

import Clipper.getBounds
import clipper2.clipper32.core.Path32
import clipper2.clipper32.core.Paths32
import clipper2.clipper32.core.Point32
import clipper2.clipper32.core.Rect32
import clipper2.clipper32.core.InternalClipper32.crossProduct
import clipper2.clipper32.core.InternalClipper32.getIntersectPt
import clipper2.clipper32.core.InternalClipper32.pointInPolygon
import clipper2.clipper32.core.InternalClipper32.segsIntersect
import clipper2.engine.PointInPolygonResult
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import tangible.OutObject
import tangible.RefObject

/**
 * ExecuteRectClip intersects subject polygons with the specified rectangular clipping
 * region. Polygons may be simple or complex (self-intersecting).
 *
 *
 * This function is extremely fast when compared to the Library's general
 * purpose Intersect clipper. Where Intersect has roughly O(n³) performance,
 * ExecuteRectClip has O(n) performance.
 *
 * @since 1.0.6
 */
@JsExport
open class RectClip32(rect: Rect32) {
    protected class OutPt2(var pt: Point32) {
        var next: OutPt2? = null

        var prev: OutPt2? = null
        var ownerIdx = 0

        var edge: MutableList<OutPt2?>? = null
    }

    enum class Location {
        LEFT, TOP, RIGHT, BOTTOM, INSIDE
    }

    protected val rect: Rect32
    protected val mp: Point32
    protected val rectPath: Path32
    protected var pathBounds: Rect32? = null
    protected var results: MutableList<OutPt2?>
    protected var edges: Array<MutableList<OutPt2?>>
    protected var currIdx = -1

    init {
        currIdx = -1
        this.rect = rect
        mp = rect.midPoint()
        rectPath = this.rect.asPath()
        results = mutableListOf<OutPt2?>()
        edges = Array<MutableList<OutPt2?>>(8) { mutableListOf() }
    }

    protected fun add(pt: Point32, startingNewPath: Boolean = false): OutPt2? {
        // this method is only called by InternalExecute.
        // Later splitting and rejoining won't create additional op's,
        // though they will change the (non-storage) fResults count.
        var currIdx = results.size
        val result: OutPt2
        if (currIdx == 0 || startingNewPath) {
            result = OutPt2(pt)
            results.add(result)
            result.ownerIdx = currIdx
            result.prev = result
            result.next = result
        } else {
            currIdx--
            val prevOp: OutPt2? = results[currIdx]
            if (prevOp!!.pt === pt) {
                return prevOp
            }
            result = OutPt2(pt)
            result.ownerIdx = currIdx
            result.next = prevOp!!.next
            prevOp.next!!.prev = result
            prevOp.next = result
            result.prev = prevOp
            results[currIdx] = result
        }
        return result
    }

    private fun addCorner(prev: Location?, curr: Location?) {
        if (headingClockwise(prev, curr)) {
            add(rectPath[prev!!.ordinal])
        } else {
            add(rectPath[curr!!.ordinal])
        }
    }

    private fun addCorner(loc: RefObject<Location>, isClockwise: Boolean) {
        if (isClockwise) {
            add(rectPath[loc.argValue!!.ordinal])
            loc.argValue = getAdjacentLocation(loc.argValue, true)
        } else {
            loc.argValue = getAdjacentLocation(loc.argValue, false)
            add(rectPath[loc.argValue!!.ordinal])
        }
    }

    protected fun getNextLocation(path: Path32, loc: RefObject<Location>, i: RefObject<Int>, highI: Int) {
        when (loc.argValue) {
            Location.LEFT -> {
                while (i.argValue!! <= highI && path[i.argValue!!].x <= rect.left) {
                    i.argValue = i.argValue!! + 1
                }
                if (i.argValue!! > highI) {
                    return
                }
                if (path[i.argValue!!].x >= rect.right) {
                    loc.argValue = Location.RIGHT
                } else if (path[i.argValue!!].y <= rect.top) {
                    loc.argValue = Location.TOP
                } else if (path[i.argValue!!].y >= rect.bottom) {
                    loc.argValue = Location.BOTTOM
                } else {
                    loc.argValue = Location.INSIDE
                }
            }

            Location.TOP -> {
                while (i.argValue!! <= highI && path[i.argValue!!].y <= rect.top) {
                    i.argValue = i.argValue!! + 1
                }
                if (i.argValue!! > highI) {
                    return
                }
                if (path[i.argValue!!].y >= rect.bottom) {
                    loc.argValue = Location.BOTTOM
                } else if (path[i.argValue!!].x <= rect.left) {
                    loc.argValue = Location.LEFT
                } else if (path[i.argValue!!].x >= rect.right) {
                    loc.argValue = Location.RIGHT
                } else {
                    loc.argValue = Location.INSIDE
                }
            }

            Location.RIGHT -> {
                while (i.argValue!! <= highI && path[i.argValue!!].x >= rect.right) {
                    i.argValue = i.argValue!! + 1
                }
                if (i.argValue!! > highI) {
                    return
                }
                if (path[i.argValue!!].x <= rect.left) {
                    loc.argValue = Location.LEFT
                } else if (path[i.argValue!!].y <= rect.top) {
                    loc.argValue = Location.TOP
                } else if (path[i.argValue!!].y >= rect.bottom) {
                    loc.argValue = Location.BOTTOM
                } else {
                    loc.argValue = Location.INSIDE
                }
            }

            Location.BOTTOM -> {
                while (i.argValue!! <= highI && path[i.argValue!!].y >= rect.bottom) {
                    i.argValue = i.argValue!! + 1
                }
                if (i.argValue!! > highI) {
                    return
                }
                if (path[i.argValue!!].y <= rect.top) {
                    loc.argValue = Location.TOP
                } else if (path[i.argValue!!].x <= rect.left) {
                    loc.argValue = Location.LEFT
                } else if (path[i.argValue!!].x >= rect.right) {
                    loc.argValue = Location.RIGHT
                } else {
                    loc.argValue = Location.INSIDE
                }
            }

            Location.INSIDE -> {
                while (i.argValue!! <= highI) {
                    if (path[i.argValue!!].x < rect.left) {
                        loc.argValue = Location.LEFT
                    } else if (path[i.argValue!!].x > rect.right) {
                        loc.argValue = Location.RIGHT
                    } else if (path[i.argValue!!].y > rect.bottom) {
                        loc.argValue = Location.BOTTOM
                    } else if (path[i.argValue!!].y < rect.top) {
                        loc.argValue = Location.TOP
                    } else {
                        add(path[i.argValue!!])
                        i.argValue = i.argValue!! + 1
                        continue
                    }
                    break
                }
            }

            else -> {}
        }
    }

    private fun executeInternal(path: Path32) {
        if (path.size < 3 || rect.isEmpty()) {
            return
        }
        val startLocs: MutableList<Location?> = mutableListOf<Location?>()
        var firstCross = Location.INSIDE
        val crossingLoc = RefObject(firstCross)
        val prev = RefObject(firstCross)
        val i = RefObject(0)
        val highI = path.size - 1
        val loc = RefObject<Location>(null)
        if (!getLocation(rect, path[highI], loc)) {
            i.argValue = highI - 1
            while (i.argValue!! >= 0 && !getLocation(rect, path[i.argValue!!], prev)) {
                i.argValue = i.argValue!! - 1
            }
            if (i.argValue!! < 0) {
                for (pt in path) {
                    add(pt)
                }
                return
            }
            if (prev.argValue == Location.INSIDE) {
                loc.argValue = Location.INSIDE
            }
        }
        val startingLoc = loc.argValue

        ///////////////////////////////////////////////////
        i.argValue = 0
        while (i.argValue!! <= highI) {
            prev.argValue = loc.argValue
            val prevCrossLoc = crossingLoc.argValue
            getNextLocation(path, loc, i, highI)
            if (i.argValue!! > highI) {
                break
            }
            val prevPt = if (i.argValue == 0) path[highI] else path[i.argValue!! - 1]
            crossingLoc.argValue = loc.argValue
            val ip = Point32()
            if (!getIntersection(rectPath, path[i.argValue!!], prevPt, crossingLoc, ip)) {
                // ie remaining outside
                if (prevCrossLoc == Location.INSIDE) {
                    val isClockw = isClockwise(
                        prev.argValue, loc.argValue, prevPt,
                        path[i.argValue!!], mp
                    )
                    do {
                        startLocs.add(prev.argValue)
                        prev.argValue = getAdjacentLocation(prev.argValue, isClockw)
                    } while (prev.argValue != loc.argValue)
                    crossingLoc.argValue = prevCrossLoc // still not crossed
                } else if (prev.argValue != Location.INSIDE && prev.argValue != loc.argValue) {
                    val isClockw = isClockwise(
                        prev.argValue, loc.argValue, prevPt,
                        path[i.argValue!!], mp
                    )
                    do {
                        addCorner(prev, isClockw)
                    } while (prev.argValue != loc.argValue)
                }
                i.argValue = i.argValue!! + 1
                continue
            }

            ////////////////////////////////////////////////////
            // we must be crossing the rect boundary to get here
            ////////////////////////////////////////////////////
            if (loc.argValue == Location.INSIDE) // path must be entering rect
            {
                if (firstCross == Location.INSIDE) {
                    firstCross = crossingLoc.argValue!!
                    startLocs.add(prev.argValue)
                } else if (prev.argValue != crossingLoc.argValue) {
                    val isClockw = isClockwise(
                        prev.argValue, crossingLoc.argValue, prevPt,
                        path[i.argValue!!], mp
                    )
                    do {
                        addCorner(prev, isClockw)
                    } while (prev.argValue != crossingLoc.argValue)
                }
            } else if (prev.argValue != Location.INSIDE) {
                // passing right through rect. 'ip' here will be the second
                // intersect pt but we'll also need the first intersect pt (ip2)
                loc.argValue = prev.argValue
                val ip2 = Point32()
                getIntersection(rectPath, prevPt, path[i.argValue!!], loc, ip2)
                if (prevCrossLoc != Location.INSIDE) {
                    addCorner(prevCrossLoc, loc.argValue)
                }
                if (firstCross == Location.INSIDE) {
                    firstCross = loc.argValue!!
                    startLocs.add(prev.argValue)
                }
                loc.argValue = crossingLoc.argValue
                add(ip2)
                if (ip.opEquals(ip2)) {
                    // it's very likely that path[i] is on rect
                    getLocation(rect, path[i.argValue!!], loc)
                    addCorner(crossingLoc.argValue, loc.argValue)
                    crossingLoc.argValue = loc.argValue
                    continue
                }
            } else  // path must be exiting rect
            {
                loc.argValue = crossingLoc.argValue
                if (firstCross == Location.INSIDE) {
                    firstCross = crossingLoc.argValue!!
                }
            }
            add(ip)
        } // while i <= highI
        ///////////////////////////////////////////////////
        if (firstCross == Location.INSIDE) {
            // path never intersects
            if (startingLoc != Location.INSIDE) {
                if (pathBounds!!.contains(rect) && path1ContainsPath2(path, rectPath)) {
                    for (j in 0..3) {
                        add(rectPath[j])
                        addToEdge(edges[j * 2], results[0])
                    }
                }
            }
        } else if (loc.argValue != Location.INSIDE && (loc.argValue != firstCross || startLocs.size > 2)) {
            if (startLocs.size > 0) {
                prev.argValue = loc.argValue
                for (loc2 in startLocs) {
                    if (prev.argValue == loc2) {
                        continue
                    }
                    addCorner(prev, headingClockwise(prev.argValue, loc2))
                    prev.argValue = loc2
                }
                loc.argValue = prev.argValue
            }
            if (loc.argValue != firstCross) {
                addCorner(loc, headingClockwise(loc.argValue, firstCross))
            }
        }
    }

    fun execute(paths: Paths32, convexOnly: Boolean): Paths32 {
        val result = Paths32()
        if (rect.isEmpty()) {
            return result
        }
        for (path in paths) {
            if (path.size < 3) {
                continue
            }
            pathBounds = getBounds(path)
            if (!rect.intersects(pathBounds!!)) {
                continue  // the path must be completely outside fRect
            } else if (rect.contains(pathBounds!!)) {
                // the path must be completely inside rect_
                result.add(path)
                continue
            }
            executeInternal(path)
            if (!convexOnly) {
                checkEdges()
                for (i in 0..3) {
                    tidyEdgePair(i, edges[i * 2], edges[i * 2 + 1])
                }
            }
            for (op in results) {
                val tmp = getPath(op)
                if (tmp.size > 0) {
                    result.add(tmp)
                }
            }

            // clean up after every loop
            results.clear()
            for (i in 0..7) {
                edges[i].clear()
            }
        }
        return result
    }

    private fun checkEdges() {
        for (i in results.indices) {
            var op: OutPt2? = results[i]
            var op2: OutPt2? = op
            if (op == null) {
                continue
            }
            do {
                if (crossProduct(op2!!.prev!!.pt, op2.pt, op2.next!!.pt) == 0.0) {
                    if (op2 === op) {
                        op2 = unlinkOpBack(op2)
                        if (op2 == null) {
                            break
                        }
                        op = op2.prev
                    } else {
                        op2 = unlinkOpBack(op2)
                        if (op2 == null) {
                            break
                        }
                    }
                } else {
                    op2 = op2.next
                }
            } while (op2 !== op)
            if (op2 == null) {
                results[i] = null
                continue
            }
            results[i] = op2 // safety first

            /* unsigned */
            var edgeSet1 = getEdgesForPt(op!!.prev!!.pt, rect)
            op2 = op
            do {
                /* unsigned */
                val edgeSet2 = getEdgesForPt(op2!!.pt, rect)
                if (edgeSet2 != 0 && op2.edge == null) {
                    /* unsigned */
                    val combinedSet = edgeSet1 and edgeSet2
                    for (j in 0..3) {
                        if (combinedSet and (1 shl j) != 0) {
                            if (isHeadingClockwise(op2.prev!!.pt, op2.pt, j)) {
                                addToEdge(edges[j * 2], op2)
                            } else {
                                addToEdge(edges[j * 2 + 1], op2)
                            }
                        }
                    }
                }
                edgeSet1 = edgeSet2
                op2 = op2.next
            } while (op2 !== op)
        }
    }

    private fun tidyEdgePair(idx: Int, cw: MutableList<OutPt2?>, ccw: MutableList<OutPt2?>) {
        if (ccw.isEmpty()) {
            return
        }
        val isHorz = idx == 1 || idx == 3
        val cwIsTowardLarger = idx == 1 || idx == 2
        var i = 0
        var j = 0
        var p1: OutPt2?
        var p2: OutPt2?
        var p1a: OutPt2?
        var p2a: OutPt2?
        var op: OutPt2?
        var op2: OutPt2?
        while (i < cw.size) {
            p1 = cw[i]
            if (p1 != null) {
                if (p1.next === p1.prev) {
                    cw[i++]!!.edge = null
                    j = 0
                    continue
                }
            }
            val jLim = ccw.size
            while (j < jLim && (ccw[j] == null || ccw[j]!!.next === ccw[j]!!.prev)) {
                ++j
            }
            if (j == jLim) {
                ++i
                j = 0
                continue
            }
            if (cwIsTowardLarger) {
                // p1 >>>> p1a;
                // p2 <<<< p2a;
                p1 = cw[i]!!.prev
                p1a = cw[i]
                p2 = ccw[j]
                p2a = ccw[j]!!.prev
            } else {
                // p1 <<<< p1a;
                // p2 >>>> p2a;
                p1 = cw[i]
                p1a = cw[i]!!.prev
                p2 = ccw[j]!!.prev
                p2a = ccw[j]
            }
            if (isHorz && !hasHorzOverlap(p1!!.pt, p1a!!.pt, p2!!.pt, p2a!!.pt) || !isHorz && !hasVertOverlap(
                    p1!!.pt, p1a!!.pt, p2!!.pt, p2a!!.pt
                )
            ) {
                ++j
                continue
            }

            // to get here we're either splitting or rejoining
            val isRejoining = cw[i]!!.ownerIdx != ccw[j]!!.ownerIdx
            if (isRejoining) {
                results[p2!!.ownerIdx] = null
                setNewOwner(p2, p1!!.ownerIdx)
            }

            // do the split or re-join
            if (cwIsTowardLarger) {
                // p1 >> | >> p1a;
                // p2 << | << p2a;
                p1!!.next = p2
                p2!!.prev = p1
                p1a!!.prev = p2a
                p2a!!.next = p1a
            } else {
                // p1 << | << p1a;
                // p2 >> | >> p2a;
                p1!!.prev = p2
                p2!!.next = p1
                p1a!!.next = p2a
                p2a!!.prev = p1a
            }
            if (!isRejoining) {
                val new_idx = results.size
                results.add(p1a)
                setNewOwner(p1a, new_idx)
            }
            if (cwIsTowardLarger) {
                op = p2
                op2 = p1a
            } else {
                op = p1
                op2 = p2a
            }
            results[op.ownerIdx] = op
            results[op2.ownerIdx] = op2

            // and now lots of work to get ready for the next loop
            var opIsLarger: Boolean
            var op2IsLarger: Boolean
            if (isHorz) // X
            {
                opIsLarger = op.pt.x > op.prev!!.pt.x
                op2IsLarger = op2.pt.x > op2.prev!!.pt.x
            } else  // Y
            {
                opIsLarger = op.pt.y > op.prev!!.pt.y
                op2IsLarger = op2.pt.y > op2.prev!!.pt.y
            }
            if (op.next === op.prev || op.pt === op.prev!!.pt) {
                if (op2IsLarger == cwIsTowardLarger) {
                    cw[i] = op2
                    ccw[j++] = null
                } else {
                    ccw[j] = op2
                    cw.set(i++, null)
                }
            } else if (op2.next === op2.prev || op2.pt === op2.prev!!.pt) {
                if (opIsLarger == cwIsTowardLarger) {
                    cw[i] = op
                    ccw[j++] = null
                } else {
                    ccw[j] = op
                    cw.set(i++, null)
                }
            } else if (opIsLarger == op2IsLarger) {
                if (opIsLarger == cwIsTowardLarger) {
                    cw[i] = op
                    uncoupleEdge(op2)
                    addToEdge(cw, op2)
                    ccw[j++] = null
                } else {
                    cw.set(i++, null)
                    ccw[j] = op2
                    uncoupleEdge(op)
                    addToEdge(ccw, op)
                    j = 0
                }
            } else {
                if (opIsLarger == cwIsTowardLarger) {
                    cw[i] = op
                } else {
                    ccw[j] = op
                }
                if (op2IsLarger == cwIsTowardLarger) {
                    cw[i] = op2
                } else {
                    ccw[j] = op2
                }
            }
        }
    }

    private fun getPath(op: OutPt2?): Path32 {
        var op = op
        val result = Path32()
        if (op == null || op.prev === op.next) {
            return result
        }
        var op2: OutPt2? = op.next
        while (op2 != null && op2 !== op) {
            if (crossProduct(op2.prev!!.pt, op2.pt, op2.next!!.pt) == 0.0) {
                op = op2.prev
                op2 = unlinkOp(op2)
            } else {
                op2 = op2.next
            }
        }
        if (op2 == null) {
            return Path32()
        }
        result.add(op!!.pt)
        op2 = op.next
        while (op2 !== op) {
            result.add(op2!!.pt)
            op2 = op2.next
        }
        return result
    }

    companion object {
        private fun path1ContainsPath2(path1: Path32, path2: Path32): Boolean {
            // nb: occasionally, due to rounding, path1 may
            // appear (momentarily) inside or outside path2.
            var ioCount = 0
            for (pt in path2) {
                val pip = pointInPolygon(pt, path1)
                when (pip) {
                    PointInPolygonResult.IsInside -> ioCount--
                    PointInPolygonResult.IsOutside -> ioCount++
                    else -> {}
                }
                if (abs(ioCount) > 1) {
                    break
                }
            }
            return ioCount <= 0
        }

        private fun isClockwise(
                prev: Location?,
                curr: Location?,
                prevPt: Point32,
                currPt: Point32,
                rectMidPoint: Point32
        ): Boolean {
            return if (areOpposites(prev, curr)) {
                crossProduct(prevPt, rectMidPoint, currPt) < 0
            } else {
                headingClockwise(prev, curr)
            }
        }

        private fun areOpposites(prev: Location?, curr: Location?): Boolean {
            return abs(prev!!.ordinal - curr!!.ordinal) == 2
        }

        private fun headingClockwise(prev: Location?, curr: Location?): Boolean {
            return (prev!!.ordinal + 1) % 4 == curr!!.ordinal
        }

        private fun getAdjacentLocation(loc: Location?, isClockwise: Boolean): Location {
            val delta = if (isClockwise) 1 else 3
            return Location.values()[(loc!!.ordinal + delta) % 4]
        }

        private fun unlinkOp(op: OutPt2): OutPt2? {
            if (op.next === op) {
                return null
            }
            op.prev!!.next = op.next
            op.next!!.prev = op.prev
            return op.next
        }

        private fun unlinkOpBack(op: OutPt2): OutPt2? {
            if (op.next === op) {
                return null
            }
            op.prev!!.next = op.next
            op.next!!.prev = op.prev
            return op.prev
        }

        private fun getEdgesForPt(pt: Point32, rec: Rect32): Int {
            var result = 0 // unsigned
            if (pt.x == rec.left) {
                result = 1
            } else if (pt.x == rec.right) {
                result = 4
            }
            if (pt.y == rec.top) {
                result += 2
            } else if (pt.y == rec.bottom) {
                result += 8
            }
            return result
        }

        private fun isHeadingClockwise(pt1: Point32, pt2: Point32, edgeIdx: Int): Boolean {
            return when (edgeIdx) {
                0 -> pt2.y < pt1.y
                1 -> pt2.x > pt1.x
                2 -> pt2.y > pt1.y
                else -> pt2.x < pt1.x
            }
        }

        private fun hasHorzOverlap(left1: Point32, right1: Point32, left2: Point32, right2: Point32): Boolean {
            return left1.x < right2.x && right1.x > left2.x
        }

        private fun hasVertOverlap(top1: Point32, bottom1: Point32, top2: Point32, bottom2: Point32): Boolean {
            return top1.y < bottom2.y && bottom1.y > top2.y
        }

        private fun addToEdge(edge: MutableList<OutPt2?>, op: OutPt2?) {
            if (op!!.edge != null) {
                return
            }
            op.edge = edge
            edge.add(op)
        }

        private fun uncoupleEdge(op: OutPt2) {
            if (op.edge == null) {
                return
            }
            for (i in op.edge!!.indices) {
                val op2: OutPt2? = op.edge!![i]
                if (op2 === op) {
                    op.edge!![i] = null
                    break
                }
            }
            op.edge = null
        }

        private fun setNewOwner(op: OutPt2, newIdx: Int) {
            op.ownerIdx = newIdx
            var op2 = op.next
            while (op2 !== op) {
                op2!!.ownerIdx = newIdx
                op2 = op2.next
            }
        }

        @JvmStatic
        protected fun getLocation(rec: Rect32, pt: Point32, loc: OutObject<Location>): Boolean {
            if (pt.x == rec.left && pt.y >= rec.top && pt.y <= rec.bottom) {
                loc.argValue = Location.LEFT
                return false // pt on rec
            }
            if (pt.x == rec.right && pt.y >= rec.top && pt.y <= rec.bottom) {
                loc.argValue = Location.RIGHT
                return false // pt on rec
            }
            if (pt.y == rec.top && pt.x >= rec.left && pt.x <= rec.right) {
                loc.argValue = Location.TOP
                return false // pt on rec
            }
            if (pt.y == rec.bottom && pt.x >= rec.left && pt.x <= rec.right) {
                loc.argValue = Location.BOTTOM
                return false // pt on rec
            }
            if (pt.x < rec.left) {
                loc.argValue = Location.LEFT
            } else if (pt.x > rec.right) {
                loc.argValue = Location.RIGHT
            } else if (pt.y < rec.top) {
                loc.argValue = Location.TOP
            } else if (pt.y > rec.bottom) {
                loc.argValue = Location.BOTTOM
            } else {
                loc.argValue = Location.INSIDE
            }
            return true
        }

        @JvmStatic
        protected fun getIntersection(
                rectPath: Path32, p: Point32, p2: Point32?, loc: RefObject<Location>,  /* out */
            ip: Point32?
        ): Boolean {
            /*
		 * Gets the pt of intersection between rectPath and segment(p, p2) that's
		 * closest to 'p'. When result == false, loc will remain unchanged.
		 */
            when (loc.argValue) {
                Location.LEFT -> if (segsIntersect(
                        p,
                        p2!!, rectPath[0], rectPath[3], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[3], ip!!)
                } else if (p.y < rectPath[0].y && segsIntersect(
                        p,
                        p2, rectPath[0], rectPath[1], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[1], ip!!)
                    loc.argValue = Location.TOP
                } else if (segsIntersect(p, p2, rectPath[2], rectPath[3], true)) {
                    getIntersectPt(p, p2, rectPath[2], rectPath[3], ip!!)
                    loc.argValue = Location.BOTTOM
                } else {
                    return false
                }

                Location.RIGHT -> if (segsIntersect(
                        p,
                        p2!!, rectPath[1], rectPath[2], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[1], rectPath[2], ip!!)
                } else if (p.y < rectPath[0].y && segsIntersect(
                        p,
                        p2, rectPath[0], rectPath[1], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[1], ip!!)
                    loc.argValue = Location.TOP
                } else if (segsIntersect(p, p2, rectPath[2], rectPath[3], true)) {
                    getIntersectPt(p, p2, rectPath[2], rectPath[3], ip!!)
                    loc.argValue = Location.BOTTOM
                } else {
                    return false
                }

                Location.TOP -> if (segsIntersect(
                        p,
                        p2!!, rectPath[0], rectPath[1], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[1], ip!!)
                } else if (p.x < rectPath[0].x && segsIntersect(
                        p,
                        p2, rectPath[0], rectPath[3], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[3], ip!!)
                    loc.argValue = Location.LEFT
                } else if (p.x > rectPath[1].x && segsIntersect(
                        p,
                        p2, rectPath[1], rectPath[2], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[1], rectPath[2], ip!!)
                    loc.argValue = Location.RIGHT
                } else {
                    return false
                }

                Location.BOTTOM -> if (segsIntersect(
                        p,
                        p2!!, rectPath[2], rectPath[3], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[2], rectPath[3], ip!!)
                } else if (p.x < rectPath[3].x && segsIntersect(
                        p,
                        p2, rectPath[0], rectPath[3], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[3], ip!!)
                    loc.argValue = Location.LEFT
                } else if (p.x > rectPath[2].x && segsIntersect(
                        p,
                        p2, rectPath[1], rectPath[2], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[1], rectPath[2], ip!!)
                    loc.argValue = Location.RIGHT
                } else {
                    return false
                }

                Location.INSIDE -> if (segsIntersect(
                        p,
                        p2!!, rectPath[0], rectPath[3], true
                    )
                ) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[3], ip!!)
                    loc.argValue = Location.LEFT
                } else if (segsIntersect(p, p2, rectPath[0], rectPath[1], true)) {
                    getIntersectPt(p, p2, rectPath[0], rectPath[1], ip!!)
                    loc.argValue = Location.TOP
                } else if (segsIntersect(p, p2, rectPath[1], rectPath[2], true)) {
                    getIntersectPt(p, p2, rectPath[1], rectPath[2], ip!!)
                    loc.argValue = Location.RIGHT
                } else if (segsIntersect(p, p2, rectPath[2], rectPath[3], true)) {
                    getIntersectPt(p, p2, rectPath[2], rectPath[3], ip!!)
                    loc.argValue = Location.BOTTOM
                } else {
                    return false
                }

                else -> {}
            }
            return true
        }
    }
}