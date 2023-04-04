@file:Suppress("unused")

package clipper2.engine

import Clipper
import Clipper.perpendicDistFromLineSqrd
import clipper2.core.ClipType
import clipper2.core.FillRule
import clipper2.core.InternalClipper.crossProduct
import clipper2.core.InternalClipper.dotProduct
import clipper2.core.InternalClipper.getClosestPtOnSegment
import clipper2.core.InternalClipper.getIntersectPoint
import clipper2.core.InternalClipper.getIntersectPt
import clipper2.core.InternalClipper.segsIntersect
import clipper2.core.Path64
import clipper2.core.PathType
import clipper2.core.Paths64
import clipper2.core.Point64
import clipper2.core.PointD
import clipper2.core.Rect64
import tangible.OutObject
import tangible.RefObject
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Subject and Clip paths are passed to a Clipper object via AddSubject,
 * AddOpenSubject and AddClip methods. Clipping operations are then initiated by
 * calling Execute. And Execute can be called multiple times (ie with different
 * ClipTypes & FillRules) without having to reload these paths.
 */
abstract class ClipperBase protected constructor() {
    private var cliptype = ClipType.None
    private var fillrule = FillRule.EvenOdd
    private var actives: Active? = null
    private var sel: Active? = null
    private val minimaList: MutableList<LocalMinima>
    private val intersectList: MutableList<IntersectNode>
    private val vertexList: MutableList<Vertex?>
    private val outrecList: MutableList<OutRec>
    private val scanlineSet: MutableList<Long>
    private val horzSegList: MutableList<HorzSegment>
    private val _horzJoinList: MutableList<HorzJoin>
    private var currentLocMin = 0
    private var currentBotY: Long = 0
    private var isSortedMinimaList = false
    private var hasOpenPaths = false
    var usingPolytree = false
    var succeeded = false

    /**
     * When adjacent edges are collinear in closed path solutions, the common vertex
     * can safely be removed to simplify the solution without altering path shape.
     * However, because some users prefer to retain these common vertices, this
     * feature is optional. Nevertheless, when adjacent edges in solutions are
     * collinear and also create a 'spike' by overlapping, the vertex creating the
     * spike will be removed irrespective of the PreserveCollinear setting. This
     * property is enabled by default.
     */
    var preserveCollinear = false
    var reverseSolution = false

    /**
     * Path data structure for clipping solutions.
     */
    class OutRec {
        var idx = 0

        var owner: OutRec? = null

        var frontEdge: Active? = null

        var backEdge: Active? = null

        var pts: OutPt? = null

        var polypath: PolyPathBase? = null
        var bounds = Rect64()
        var path = Path64()
        var isOpen = false

        var splits: MutableList<Int>? = null
    }

    private class HorzSegSorter : Comparator<HorzSegment?> {
        override fun compare(a: HorzSegment?, b: HorzSegment?): Int {
            if (a == null || b == null) {
                return 0
            }
            return if (a.rightOp == null) {
                if (b.rightOp == null) 0 else 1
            } else if (b.rightOp == null) {
                -1
            } else {
                a.leftOp!!.pt.x.compareTo(b.leftOp!!.pt.x) // java.lang.Long.compare(hs1.leftOp!!.pt.x, hs2.leftOp!!.pt.x)
            }
        }
    }

    class HorzSegment(var leftOp: OutPt?) {
        var rightOp: OutPt? = null
        var leftToRight = true
    }

    private class HorzJoin(var op1: OutPt, var op2: OutPt)

    class Active {
        var bot: Point64? = null
        var top: Point64? = null
        var curX: Long = 0 // current (updated at every new scanline)
        var dx = 0.0
        var windDx = 0 // 1 or -1 depending on winding direction
        var windCount = 0
        var windCount2 = 0 // winding count of the opposite polytype

        var outrec: OutRec? = null

        // AEL: 'active edge list' (Vatti's AET - active edge table)
        // a linked list of all edges (from left to right) that are present
        // (or 'active') within the current scanbeam (a horizontal 'beam' that
        // sweeps from bottom to top over the paths in the clipping operation).
        var prevInAEL: Active? = null

        var nextInAEL: Active? = null

        // SEL: 'sorted edge list' (Vatti's ST - sorted table)
        // linked list used when sorting edges into their new positions at the
        // top of scanbeams, but also (re)used to process horizontals.
        var prevInSEL: Active? = null

        var nextInSEL: Active? = null

        var jump: Active? = null

        var vertexTop: Vertex? = null
        var localMin: LocalMinima = LocalMinima() // the bottom of an edge 'bound' (also Vatti)
        var isLeftBound = false
        var joinWith = JoinWith.None
    }

    /**
     * Vertex data structure for clipping solutions
     */
    class OutPt(pt: Point64?, outrec: OutRec?) {
        var pt: Point64

        var next: OutPt
        var prev: OutPt?
        var outrec: OutRec?

        var horz: HorzSegment?

        init {
            this.pt = pt!!
            this.outrec = outrec
            next = this
            prev = this
            horz = null
        }
    }

    enum class JoinWith {
        None, Left, Right
    }

    internal enum class HorzPosition {
        Bottom, Middle, Top
    }

    /**
     * A structure representing 2 intersecting edges. Intersections must be sorted
     * so they are processed from the largest y coordinates to the smallest while
     * keeping edges adjacent.
     */
    internal class IntersectNode(val pt: Point64, val edge1: Active?, val edge2: Active?)

    /**
     * Vertex: a pre-clipping data structure. It is used to separate polygons into
     * ascending and descending 'bounds' (or sides) that start at local minima and
     * ascend to a local maxima, before descending again.
     */
    inner class Vertex(pt: Point64, flags: Int, prev: Vertex?) {
        var pt = Point64()

        var next: Vertex?

        var prev: Vertex?
        var flags: Int

        init {
            this.pt = pt
            this.flags = flags
            next = null
            this.prev = prev
        }
    }

    internal object VertexFlags {
        const val None = 0
        const val OpenStart = 1
        const val OpenEnd = 2
        const val LocalMax = 4
        const val LocalMin = 8
    }

    init {
        minimaList = mutableListOf<LocalMinima>()
        intersectList = mutableListOf<IntersectNode>()
        vertexList = mutableListOf<Vertex?>()
        outrecList = mutableListOf<OutRec>()
        scanlineSet = mutableListOf()
        horzSegList = mutableListOf<HorzSegment>()
        _horzJoinList = mutableListOf<HorzJoin>()
        preserveCollinear = true
    }

    protected fun clearSolutionOnly() {
        while (actives != null) {
            deleteFromAEL(actives)
        }
        scanlineSet.clear()
        disposeIntersectNodes()
        outrecList.clear()
        horzSegList.clear()
        _horzJoinList.clear()
    }

    fun clear() {
        clearSolutionOnly()
        minimaList.clear()
        vertexList.clear()
        currentLocMin = 0
        isSortedMinimaList = false
        hasOpenPaths = false
    }

    protected fun reset() {
        if (!isSortedMinimaList) {
            minimaList.sortWith { locMin1: LocalMinima, locMin2: LocalMinima -> locMin2.vertex!!.pt.y.compareTo(locMin1.vertex!!.pt.y) }
            isSortedMinimaList = true
        }
        for (i in minimaList.indices.reversed()) {
            scanlineSet.addIfMissingAndSort(minimaList[i].vertex!!.pt.y)
        }
        currentBotY = 0
        currentLocMin = 0
        actives = null
        sel = null
        succeeded = true
    }

    @Suppress("unused")
    @Deprecated("Has been inlined in Java version since function is much simpler")
    private fun insertScanline(y: Long) {
        scanlineSet.addIfMissingAndSort(y)
    }

    @Suppress("unused")
    @Deprecated("Has been inlined in Java version since function is much simpler")
    private fun popScanline(): Long {
        return if (scanlineSet.isEmpty()) {
            Long.MAX_VALUE
        } else {
            scanlineSet.pollLast()
        }
    }

    private fun hasLocMinAtY(y: Long): Boolean {
        return currentLocMin < minimaList.size && minimaList[currentLocMin].vertex!!.pt.y == y
    }

    private fun popLocalMinima(): LocalMinima {
        return minimaList[currentLocMin++]
    }

    private fun addLocMin(vert: Vertex?, polytype: PathType, isOpen: Boolean) {
        // make sure the vertex is added only once ...
        if (vert!!.flags and VertexFlags.LocalMin != VertexFlags.None) {
            return
        }
        vert.flags = vert.flags or VertexFlags.LocalMin
        val lm = LocalMinima(vert, polytype, isOpen)
        minimaList.add(lm)
    }

    protected fun addPathsToVertexList(paths: Paths64, polytype: PathType, isOpen: Boolean) {
        for (path in paths) {
            var v0: Vertex? = null
            var prevV: Vertex? = null
            var currV: Vertex?
            for (pt in path) {
                if (v0 == null) {
                    v0 = Vertex(pt, VertexFlags.None, null)
                    vertexList.add(v0)
                    prevV = v0
                } else if (prevV!!.pt.opNotEquals(pt)) { // ie skips duplicates
                    currV = Vertex(pt, VertexFlags.None, prevV)
                    vertexList.add(currV)
                    prevV.next = currV
                    prevV = currV
                }
            }
            if (prevV?.prev == null) {
                continue
            }
            if (!isOpen && v0!!.pt.opEquals(prevV.pt)) {
                prevV = prevV.prev
            }
            prevV!!.next = v0
            v0!!.prev = prevV
            if (!isOpen && prevV === prevV.next) {
                continue
            }

            // OK, we have a valid path
            var goingup: Boolean
            var goingup0: Boolean
            if (isOpen) {
                currV = v0.next
                while (v0 !== currV && currV!!.pt.y == v0.pt.y) {
                    currV = currV.next
                }
                goingup = currV.pt.y <= v0.pt.y
                if (goingup) {
                    v0.flags = VertexFlags.OpenStart
                    addLocMin(v0, polytype, true)
                } else {
                    v0.flags = VertexFlags.OpenStart or VertexFlags.LocalMax
                }
            } else { // closed path
                prevV = v0.prev
                while (v0 != prevV && prevV!!.pt.y == v0.pt.y) {
                    prevV = prevV.prev
                }
                if (v0 == prevV) {
                    continue // only open paths can be completely flat
                }
                goingup = prevV.pt.y > v0.pt.y
            }
            goingup0 = goingup
            prevV = v0
            currV = v0.next
            while (v0 != currV) {
                if (currV!!.pt.y > prevV!!.pt.y && goingup) {
                    prevV.flags = prevV.flags or VertexFlags.LocalMax
                    goingup = false
                } else if (currV.pt.y < prevV.pt.y && !goingup) {
                    goingup = true
                    addLocMin(prevV, polytype, isOpen)
                }
                prevV = currV
                currV = currV.next
            }
            if (isOpen) {
                prevV!!.flags = prevV.flags or VertexFlags.OpenEnd
                if (goingup) {
                    prevV.flags = prevV.flags or VertexFlags.LocalMax
                } else {
                    addLocMin(prevV, polytype, isOpen)
                }
            } else if (goingup != goingup0) {
                if (goingup0) {
                    addLocMin(prevV, polytype, false)
                } else {
                    prevV!!.flags = prevV.flags or VertexFlags.LocalMax
                }
            }
        }
    }

    fun addSubject(path: Path64) {
        addPath(path, PathType.Subject)
    }

    /**
     * Adds one or more closed subject paths (polygons) to the Clipper object.
     */
    open fun addSubjects(paths: Paths64) {
        addPaths(paths, PathType.Subject)
    }

    /**
     * Adds one or more open subject paths (polylines) to the Clipper object.
     */
    fun addOpenSubject(path: Path64) {
        addPath(path, PathType.Subject, true)
    }

    open fun addOpenSubjects(paths: Paths64) {
        addPaths(paths, PathType.Subject, true)
    }

    /**
     * Adds one or more clip polygons to the Clipper object.
     */
    fun addClip(path: Path64) {
        addPath(path, PathType.Clip)
    }

    fun addClips(paths: Paths64) {
        addPaths(paths, PathType.Clip)
    }

    open fun addPath(path: Path64, polytype: PathType, isOpen: Boolean = false) {
        val tmp = Paths64()
        tmp.add(path)
        addPaths(tmp, polytype, isOpen)
    }

    open fun addPaths(paths: Paths64, polytype: PathType, isOpen: Boolean = false) {
        if (isOpen) {
            hasOpenPaths = true
        }
        isSortedMinimaList = false
        addPathsToVertexList(paths, polytype, isOpen)
    }

    private fun isContributingClosed(ae: Active?): Boolean {
        when (fillrule) {
            FillRule.Positive -> if (ae!!.windCount != 1) {
                return false
            }

            FillRule.Negative -> if (ae!!.windCount != -1) {
                return false
            }

            FillRule.NonZero -> if (abs(ae!!.windCount) != 1) {
                return false
            }

            FillRule.EvenOdd -> {}
        }
        return when (cliptype) {
            ClipType.Intersection -> when (fillrule) {
                FillRule.Positive -> ae!!.windCount2 > 0
                FillRule.Negative -> ae!!.windCount2 < 0
                else -> ae!!.windCount2 != 0
            }

            ClipType.Union -> when (fillrule) {
                FillRule.Positive -> ae!!.windCount2 <= 0
                FillRule.Negative -> ae!!.windCount2 >= 0
                else -> ae!!.windCount2 == 0
            }

            ClipType.Difference -> {
                val result: Boolean
                result = when (fillrule) {
                    FillRule.Positive -> ae!!.windCount2 <= 0
                    FillRule.Negative -> ae!!.windCount2 >= 0
                    else -> ae!!.windCount2 == 0
                }
                getPolyType(ae) === PathType.Subject == result
            }

            ClipType.Xor -> true // XOr is always contributing unless open
            else -> false
        }
    }

    private fun isContributingOpen(ae: Active?): Boolean {
        val isInClip: Boolean
        val isInSubj: Boolean
        when (fillrule) {
            FillRule.Positive -> {
                isInSubj = ae!!.windCount > 0
                isInClip = ae.windCount2 > 0
            }

            FillRule.Negative -> {
                isInSubj = ae!!.windCount < 0
                isInClip = ae.windCount2 < 0
            }

            else -> {
                isInSubj = ae!!.windCount != 0
                isInClip = ae.windCount2 != 0
            }
        }
        return when (cliptype) {
            ClipType.Intersection -> isInClip
            ClipType.Union -> !isInSubj && !isInClip
            else -> !isInClip
        }
    }

    private fun setWindCountForClosedPathEdge(ae: Active?) {
        /*
		 * Wind counts refer to polygon regions not edges, so here an edge's WindCnt
		 * indicates the higher of the wind counts for the two regions touching the
		 * edge. (nb: Adjacent regions can only ever have their wind counts differ by
		 * one. Also, open paths have no meaningful wind directions or counts.)
		 */
        var ae2 = ae!!.prevInAEL
        // find the nearest closed path edge of the same PolyType in AEL (heading left)
        val pt = getPolyType(ae)
        while (ae2 != null && (getPolyType(ae2) !== pt || isOpen(ae2))) {
            ae2 = ae2.prevInAEL
        }
        if (ae2 == null) {
            ae.windCount = ae.windDx
            ae2 = actives
        } else if (fillrule === FillRule.EvenOdd) {
            ae.windCount = ae.windDx
            ae.windCount2 = ae2.windCount2
            ae2 = ae2.nextInAEL
        } else {
            // NonZero, positive, or negative filling here ...
            // when e2's WindCnt is in the SAME direction as its WindDx,
            // then polygon will fill on the right of 'e2' (and 'e' will be inside)
            // nb: neither e2.WindCnt nor e2.WindDx should ever be 0.
            if (ae2.windCount * ae2.windDx < 0) {
                // opposite directions so 'ae' is outside 'ae2' ...
                if (abs(ae2.windCount) > 1) {
                    // outside prev poly but still inside another.
                    if (ae2.windDx * ae.windDx < 0) {
                        // reversing direction so use the same WC
                        ae.windCount = ae2.windCount
                    } else {
                        // otherwise keep 'reducing' the WC by 1 (i.e. towards 0) ...
                        ae.windCount = ae2.windCount + ae.windDx
                    }
                } else {
                    // now outside all polys of same polytype so set own WC ...
                    ae.windCount = if (isOpen(ae)) 1 else ae.windDx
                }
            } else {
                // 'ae' must be inside 'ae2'
                if (ae2.windDx * ae.windDx < 0) {
                    // reversing direction so use the same WC
                    ae.windCount = ae2.windCount
                } else {
                    // otherwise keep 'increasing' the WC by 1 (i.e. away from 0) ...
                    ae.windCount = ae2.windCount + ae.windDx
                }
            }
            ae.windCount2 = ae2.windCount2
            ae2 = ae2.nextInAEL // i.e. get ready to calc WindCnt2
        }

        // update windCount2 ...
        if (fillrule === FillRule.EvenOdd) {
            while (ae2 != ae) {
                if (getPolyType(ae2!!) !== pt && !isOpen(ae2)) {
                    ae.windCount2 = if (ae.windCount2 == 0) 1 else 0
                }
                ae2 = ae2!!.nextInAEL
            }
        } else {
            while (ae2 != ae) {
                if (getPolyType(ae2!!) !== pt && !isOpen(ae2)) {
                    ae.windCount2 += ae2!!.windDx
                }
                ae2 = ae2!!.nextInAEL
            }
        }
    }

    private fun setWindCountForOpenPathEdge(ae: Active?) {
        var ae2 = actives
        if (fillrule === FillRule.EvenOdd) {
            var cnt1 = 0
            var cnt2 = 0
            while (ae2 != ae) {
                if (getPolyType(ae2!!) === PathType.Clip) {
                    cnt2++
                } else if (!isOpen(ae2)) {
                    cnt1++
                }
                ae2 = ae2!!.nextInAEL
            }
            ae!!.windCount = if (isOdd(cnt1)) 1 else 0
            ae.windCount2 = if (isOdd(cnt2)) 1 else 0
        } else {
            while (ae2 != ae) {
                if (getPolyType(ae2!!) === PathType.Clip) {
                    ae!!.windCount2 += ae2!!.windDx
                } else if (!isOpen(ae2)) {
                    ae!!.windCount += ae2!!.windDx
                }
                ae2 = ae2!!.nextInAEL
            }
        }
    }

    private fun insertLeftEdge(ae: Active) {
        var ae2: Active?
        if (actives == null) {
            ae.prevInAEL = null
            ae.nextInAEL = null
            actives = ae
        } else if (!isValidAelOrder(actives!!, ae)) {
            ae.prevInAEL = null
            ae.nextInAEL = actives
            actives!!.prevInAEL = ae
            actives = ae
        } else {
            ae2 = actives
            while (ae2!!.nextInAEL != null && isValidAelOrder(ae2.nextInAEL!!, ae)) {
                ae2 = ae2.nextInAEL
            }
            // don't separate joined edges
            if (ae2.joinWith == JoinWith.Right) {
                ae2 = ae2.nextInAEL
            }
            ae.nextInAEL = ae2!!.nextInAEL
            if (ae2.nextInAEL != null) {
                ae2.nextInAEL!!.prevInAEL = ae
            }
            ae.prevInAEL = ae2
            ae2.nextInAEL = ae
        }
    }

    private fun insertRightEdge(ae: Active?, ae2: Active) {
        ae2.nextInAEL = ae!!.nextInAEL
        if (ae.nextInAEL != null) {
            ae.nextInAEL!!.prevInAEL = ae2
        }
        ae2.prevInAEL = ae
        ae.nextInAEL = ae2
    }

    private fun insertLocalMinimaIntoAEL(botY: Long) {
        var localMinima: LocalMinima
        var leftBound: Active?
        var rightBound: Active?
        // Add any local minima (if any) at BotY ...
        // NB horizontal local minima edges should contain locMin.vertex.prev
        while (hasLocMinAtY(botY)) {
            localMinima = popLocalMinima()
            if (localMinima.vertex!!.flags and VertexFlags.OpenStart != VertexFlags.None) {
                leftBound = null
            } else {
                leftBound = Active()
                leftBound.bot = localMinima.vertex!!.pt
                leftBound.curX = localMinima.vertex!!.pt.x
                leftBound.windDx = -1
                leftBound.vertexTop = localMinima.vertex!!.prev
                leftBound.top = localMinima.vertex!!.prev!!.pt
                leftBound.outrec = null
                leftBound.localMin = localMinima
                setDx(leftBound)
            }

            if (localMinima.vertex!!.flags and VertexFlags.OpenEnd != VertexFlags.None) {
                rightBound = null
            } else {
                rightBound = Active()
                rightBound.bot = localMinima.vertex!!.pt
                rightBound.curX = localMinima.vertex!!.pt.x
                rightBound.windDx = 1
                rightBound.vertexTop = localMinima.vertex!!.next
                rightBound.top = localMinima.vertex!!.next!!.pt
                rightBound.outrec = null
                rightBound.localMin = localMinima
                setDx(rightBound)
            }

            // Currently LeftB is just the descending bound and RightB is the ascending.
            // Now if the LeftB isn't on the left of RightB then we need swap them.
            if (leftBound != null && rightBound != null) {
                if (isHorizontal(leftBound)) {
                    if (isHeadingRightHorz(leftBound)) {
                        val tempRefleftBound = RefObject<Active>(leftBound)
                        val tempRefrightBound = RefObject<Active>(rightBound)
                        swapActives(tempRefleftBound, tempRefrightBound)
                        rightBound = tempRefrightBound.argValue
                        leftBound = tempRefleftBound.argValue
                    }
                } else if (isHorizontal(rightBound)) {
                    if (isHeadingLeftHorz(rightBound)) {
                        val tempRefleftBound2 = RefObject<Active>(leftBound)
                        val tempRefrightBound2 = RefObject<Active>(rightBound)
                        swapActives(tempRefleftBound2, tempRefrightBound2)
                        rightBound = tempRefrightBound2.argValue
                        leftBound = tempRefleftBound2.argValue
                    }
                } else if (leftBound.dx < rightBound.dx) {
                    val tempRefleftBound3 = RefObject<Active>(leftBound)
                    val tempRefrightBound3 = RefObject<Active>(rightBound)
                    swapActives(tempRefleftBound3, tempRefrightBound3)
                    rightBound = tempRefrightBound3.argValue
                    leftBound = tempRefleftBound3.argValue
                }
                // so when leftBound has windDx == 1, the polygon will be oriented
                // counter-clockwise in Cartesian coords (clockwise with inverted y).
            } else if (leftBound == null) {
                leftBound = rightBound
                rightBound = null
            }

            var contributing: Boolean
            leftBound!!.isLeftBound = true
            insertLeftEdge(leftBound)

            contributing = if (isOpen(leftBound)) {
                setWindCountForOpenPathEdge(leftBound)
                isContributingOpen(leftBound)
            } else {
                setWindCountForClosedPathEdge(leftBound)
                isContributingClosed(leftBound)
            }

            if (rightBound != null) {
                rightBound.windCount = leftBound.windCount
                rightBound.windCount2 = leftBound.windCount2
                insertRightEdge(leftBound, rightBound) // /////

                if (contributing) {
                    addLocalMinPoly(leftBound, rightBound, leftBound.bot, true)
                    if (!isHorizontal(leftBound)) {
                        checkJoinLeft(leftBound, leftBound.bot)
                    }
                }

                while (rightBound.nextInAEL != null && isValidAelOrder(rightBound.nextInAEL!!, rightBound)) {
                    intersectEdges(rightBound, rightBound.nextInAEL!!, rightBound.bot!!)
                    swapPositionsInAEL(rightBound, rightBound.nextInAEL)
                }

                if (isHorizontal(rightBound)) {
                    pushHorz(rightBound)
                } else {
                    checkJoinRight(rightBound, rightBound.bot)
                    scanlineSet.addIfMissingAndSort(rightBound.top!!.y)
                }
            } else if (contributing) {
                startOpenPath(leftBound, leftBound.bot)
            }

            if (isHorizontal(leftBound)) {
                pushHorz(leftBound)
            } else {
                scanlineSet.addIfMissingAndSort(leftBound.top!!.y)
            }
        } // while (HasLocMinAtY())
    }

    private fun pushHorz(ae: Active?) {
        ae!!.nextInSEL = sel
        sel = ae
    }

    private fun popHorz(ae: OutObject<Active>): Boolean {
        ae.argValue = sel
        if (sel == null) {
            return false
        }
        sel = sel!!.nextInSEL
        return true
    }

    private fun addLocalMinPoly(ae1: Active?, ae2: Active?, pt: Point64?, isNew: Boolean = false): OutPt {
        val outrec = newOutRec()
        ae1!!.outrec = outrec
        ae2!!.outrec = outrec
        if (isOpen(ae1)) {
            outrec.owner = null
            outrec.isOpen = true
            if (ae1.windDx > 0) {
                setSides(outrec, ae1, ae2)
            } else {
                setSides(outrec, ae2, ae1)
            }
        } else {
            outrec.isOpen = false
            val prevHotEdge = getPrevHotEdge(ae1)
            // e.windDx is the winding direction of the **input** paths
            // and unrelated to the winding direction of output polygons.
            // Output orientation is determined by e.outrec.frontE which is
            // the ascending edge (see AddLocalMinPoly).
            if (prevHotEdge != null) {
                if (usingPolytree) {
                    setOwner(outrec, prevHotEdge.outrec!!)
                }
                outrec.owner = prevHotEdge.outrec
                if (outrecIsAscending(prevHotEdge) == isNew) {
                    setSides(outrec, ae2, ae1)
                } else {
                    setSides(outrec, ae1, ae2)
                }
            } else {
                outrec.owner = null
                if (isNew) {
                    setSides(outrec, ae1, ae2)
                } else {
                    setSides(outrec, ae2, ae1)
                }
            }
        }
        val op = OutPt(pt, outrec)
        outrec.pts = op
        return op
    }

    private fun addLocalMaxPoly(ae1: Active, ae2: Active, pt: Point64): OutPt? {
        if (isJoined(ae1)) {
            split(ae1, pt)
        }
        if (isJoined(ae2)) {
            split(ae2, pt)
        }
        if (isFront(ae1) == isFront(ae2)) {
            if (isOpenEnd(ae1)) {
                swapFrontBackSides(ae1.outrec!!)
            } else if (isOpenEnd(ae2)) {
                swapFrontBackSides(ae2.outrec!!)
            } else {
                succeeded = false
                return null
            }
        }
        val result = addOutPt(ae1, pt)
        if (ae1.outrec === ae2.outrec) {
            val outrec = ae1.outrec
            outrec!!.pts = result
            if (usingPolytree) {
                val e = getPrevHotEdge(ae1)
                if (e == null) {
                    outrec.owner = null
                } else {
                    setOwner(outrec, e.outrec!!)
                    // nb: outRec.owner here is likely NOT the real
                    // owner but this will be fixed in DeepCheckOwner()
                }
            }
            uncoupleOutRec(ae1)
        } else if (isOpen(ae1)) {
            if (ae1.windDx < 0) {
                joinOutrecPaths(ae1, ae2)
            } else {
                joinOutrecPaths(ae2, ae1)
            }
        } else if (ae1.outrec!!.idx < ae2.outrec!!.idx) {
            joinOutrecPaths(ae1, ae2)
        } else {
            joinOutrecPaths(ae2, ae1)
        }
        return result
    }

    private fun newOutRec(): OutRec {
        val result = OutRec()
        result.idx = outrecList.size
        outrecList.add(result)
        return result
    }

    private fun startOpenPath(ae: Active?, pt: Point64?): OutPt {
        val outrec = newOutRec()
        outrec.isOpen = true
        if (ae!!.windDx > 0) {
            outrec.frontEdge = ae
            outrec.backEdge = null
        } else {
            outrec.frontEdge = null
            outrec.backEdge = ae
        }
        ae.outrec = outrec
        val op = OutPt(pt, outrec)
        outrec.pts = op
        return op
    }

    private fun updateEdgeIntoAEL(ae: Active) {
        ae.bot = ae.top
        ae.vertexTop = nextVertex(ae)
        ae.top = ae.vertexTop!!.pt
        ae.curX = ae.bot!!.x
        setDx(ae)
        if (isJoined(ae)) {
            split(ae, ae.bot)
        }
        if (isHorizontal(ae)) {
            return
        }
        scanlineSet.addIfMissingAndSort(ae.top!!.y)
        checkJoinLeft(ae, ae.bot)
        checkJoinRight(ae, ae.bot)
    }

    private fun intersectEdges(ae1: Active, ae2: Active, pt: Point64): OutPt? {
        var ae1 = ae1
        var ae2 = ae2
        var resultOp: OutPt? = null

        // MANAGE OPEN PATH INTERSECTIONS SEPARATELY ...
        if (hasOpenPaths && (isOpen(ae1) || isOpen(ae2))) {
            if (isOpen(ae1) && isOpen(ae2)) {
                return null
            }
            // the following line avoids duplicating quite a bit of code
            if (isOpen(ae2)) {
                val tempRefae1 = RefObject<Active>(ae1)
                val tempRefae2 = RefObject<Active>(ae2)
                swapActives(tempRefae1, tempRefae2)
                ae2 = tempRefae2.argValue!!
                ae1 = tempRefae1.argValue!!
            }
            if (isJoined(ae2)) {
                split(ae2, pt) // needed for safety
            }
            if (cliptype === ClipType.Union) {
                if (!isHotEdge(ae2)) {
                    return null
                }
            } else if (ae2.localMin.polytype === PathType.Subject) {
                return null
            }
            when (fillrule) {
                FillRule.Positive -> if (ae2.windCount != 1) {
                    return null
                }

                FillRule.Negative -> if (ae2.windCount != -1) {
                    return null
                }

                else -> if (abs(ae2.windCount) != 1) {
                    return null
                }
            }

            // toggle contribution ...
            if (isHotEdge(ae1)) {
                resultOp = addOutPt(ae1, pt)
                if (isFront(ae1)) {
                    ae1.outrec!!.frontEdge = null
                } else {
                    ae1.outrec!!.backEdge = null
                }
                ae1.outrec = null
            } else if (pt.opEquals(ae1.localMin.vertex!!.pt) && !isOpenEnd(ae1.localMin.vertex)) {
                // find the other side of the LocMin and
                // if it's 'hot' join up with it ...
                val ae3 = findEdgeWithMatchingLocMin(ae1)
                if (ae3 != null && isHotEdge(ae3)) {
                    ae1.outrec = ae3.outrec
                    if (ae1.windDx > 0) {
                        setSides(ae3.outrec!!, ae1, ae3)
                    } else {
                        setSides(ae3.outrec!!, ae3, ae1)
                    }
                    return ae3.outrec!!.pts
                }
                resultOp = startOpenPath(ae1, pt)
            } else {
                resultOp = startOpenPath(ae1, pt)
            }
            return resultOp
        }

        // MANAGING CLOSED PATHS FROM HERE ON
        if (isJoined(ae1)) {
            split(ae1, pt)
        }
        if (isJoined(ae2)) {
            split(ae2, pt)
        }

        // UPDATE WINDING COUNTS...
        var oldE1WindCount: Int
        val oldE2WindCount: Int
        if (ae1.localMin.polytype === ae2.localMin.polytype) {
            if (fillrule === FillRule.EvenOdd) {
                oldE1WindCount = ae1.windCount
                ae1.windCount = ae2.windCount
                ae2.windCount = oldE1WindCount
            } else {
                if (ae1.windCount + ae2.windDx == 0) {
                    ae1.windCount = -ae1.windCount
                } else {
                    ae1.windCount += ae2.windDx
                }
                if (ae2.windCount - ae1.windDx == 0) {
                    ae2.windCount = -ae2.windCount
                } else {
                    ae2.windCount -= ae1.windDx
                }
            }
        } else {
            if (fillrule !== FillRule.EvenOdd) {
                ae1.windCount2 += ae2.windDx
            } else {
                ae1.windCount2 = if (ae1.windCount2 == 0) 1 else 0
            }
            if (fillrule !== FillRule.EvenOdd) {
                ae2.windCount2 -= ae1.windDx
            } else {
                ae2.windCount2 = if (ae2.windCount2 == 0) 1 else 0
            }
        }
        when (fillrule) {
            FillRule.Positive -> {
                oldE1WindCount = ae1.windCount
                oldE2WindCount = ae2.windCount
            }

            FillRule.Negative -> {
                oldE1WindCount = -ae1.windCount
                oldE2WindCount = -ae2.windCount
            }

            else -> {
                oldE1WindCount = abs(ae1.windCount)
                oldE2WindCount = abs(ae2.windCount)
            }
        }
        val e1WindCountIs0or1 = oldE1WindCount == 0 || oldE1WindCount == 1
        val e2WindCountIs0or1 = oldE2WindCount == 0 || oldE2WindCount == 1
        if (!isHotEdge(ae1) && !e1WindCountIs0or1 || !isHotEdge(ae2) && !e2WindCountIs0or1) {
            return null
        }

        // NOW PROCESS THE INTERSECTION ...

        // if both edges are 'hot' ...
        if (isHotEdge(ae1) && isHotEdge(ae2)) {
            if (oldE1WindCount != 0 && oldE1WindCount != 1 || oldE2WindCount != 0 && oldE2WindCount != 1 || ae1.localMin.polytype !== ae2.localMin.polytype && cliptype !== ClipType.Xor) {
                resultOp = addLocalMaxPoly(ae1, ae2, pt)
            } else if (isFront(ae1) || ae1.outrec === ae2.outrec) {
                // this 'else if' condition isn't strictly needed but
                // it's sensible to split polygons that ony touch at
                // a common vertex (not at common edges).
                resultOp = addLocalMaxPoly(ae1, ae2, pt)
                addLocalMinPoly(ae1, ae2, pt)
            } else {
                // can't treat as maxima & minima
                resultOp = addOutPt(ae1, pt)
                addOutPt(ae2, pt)
                swapOutrecs(ae1, ae2)
            }
        } else if (isHotEdge(ae1)) {
            resultOp = addOutPt(ae1, pt)
            swapOutrecs(ae1, ae2)
        } else if (isHotEdge(ae2)) {
            resultOp = addOutPt(ae2, pt)
            swapOutrecs(ae1, ae2)
        } else {
            val e1Wc2: Long
            val e2Wc2: Long
            when (fillrule) {
                FillRule.Positive -> {
                    e1Wc2 = ae1.windCount2.toLong()
                    e2Wc2 = ae2.windCount2.toLong()
                }

                FillRule.Negative -> {
                    e1Wc2 = -ae1.windCount2.toLong()
                    e2Wc2 = -ae2.windCount2.toLong()
                }

                else -> {
                    e1Wc2 = abs(ae1.windCount2).toLong()
                    e2Wc2 = abs(ae2.windCount2).toLong()
                }
            }
            if (!isSamePolyType(ae1, ae2)) {
                resultOp = addLocalMinPoly(ae1, ae2, pt)
            } else if (oldE1WindCount == 1 && oldE2WindCount == 1) {
                resultOp = null
                when (cliptype) {
                    ClipType.Union -> {
                        if (e1Wc2 > 0 && e2Wc2 > 0) {
                            return null
                        }
                        resultOp = addLocalMinPoly(ae1, ae2, pt)
                    }

                    ClipType.Difference -> if (getPolyType(ae1) === PathType.Clip && e1Wc2 > 0 && e2Wc2 > 0 || getPolyType(
                            ae1
                        ) === PathType.Subject && e1Wc2 <= 0 && e2Wc2 <= 0
                    ) {
                        resultOp = addLocalMinPoly(ae1, ae2, pt)
                    }

                    ClipType.Xor -> resultOp = addLocalMinPoly(ae1, ae2, pt)
                    else -> {
                        if (e1Wc2 <= 0 || e2Wc2 <= 0) {
                            return null
                        }
                        resultOp = addLocalMinPoly(ae1, ae2, pt)
                    }
                }
            }
        }
        return resultOp
    }

    private fun deleteFromAEL(ae: Active?) {
        val prev = ae!!.prevInAEL
        val next = ae.nextInAEL
        if (prev == null && next == null && actives != ae) {
            return // already deleted
        }
        if (prev != null) {
            prev.nextInAEL = next
        } else {
            actives = next
        }
        if (next != null) {
            next.prevInAEL = prev
        }
        // delete &ae;
    }

    private fun adjustCurrXAndCopyToSEL(topY: Long) {
        var ae = actives
        sel = ae
        while (ae != null) {
            ae.prevInSEL = ae.prevInAEL
            ae.nextInSEL = ae.nextInAEL
            ae.jump = ae.nextInSEL
            if (ae.joinWith == JoinWith.Left) {
                ae.curX = ae.prevInAEL!!.curX // this also avoids complications
            } else {
                ae.curX = topX(ae, topY)
            }
            // NB don't update ae.curr.y yet (see AddNewIntersectNode)
            ae = ae.nextInAEL
        }
    }

    protected fun executeInternal(ct: ClipType, fillRule: FillRule) {
        if (ct === ClipType.None) {
            return
        }
        fillrule = fillRule
        cliptype = ct
        reset()
        if (scanlineSet.isEmpty()) {
            return
        }
        var y: Long = scanlineSet.pollLast()
        while (succeeded) {
            insertLocalMinimaIntoAEL(y)
            var ae: Active? = null
            val tempOutae = OutObject<Active>(null)
            while (popHorz(tempOutae)) {
                ae = tempOutae.argValue
                doHorizontal(ae)
            }
            if (!horzSegList.isEmpty()) {
                convertHorzSegsToJoins()
                horzSegList.clear()
            }
            currentBotY = y // bottom of scanbeam
            if (scanlineSet.isEmpty()) {
                break // y new top of scanbeam
            }
            y = scanlineSet.pollLast()
            doIntersections(y)
            doTopOfScanbeam(y)
            val tempOutae2 = OutObject<Active>(null)
            while (popHorz(tempOutae2)) {
                ae = tempOutae2.argValue
                doHorizontal(ae)
            }
        }
        if (succeeded) {
            processHorzJoins()
        }
    }

    private fun doIntersections(topY: Long) {
        if (buildIntersectList(topY)) {
            processIntersectList()
            disposeIntersectNodes()
        }
    }

    private fun disposeIntersectNodes() {
        intersectList.clear()
    }

    private fun addNewIntersectNode(ae1: Active?, ae2: Active?, topY: Long) {
        var ip = Point64()
        if (!getIntersectPt(ae1!!.bot!!, ae1.top!!, ae2!!.bot!!, ae2.top!!, ip)) {
            ip = Point64(ae1.curX, topY)
        }
        if (ip.y > currentBotY || ip.y < topY) {
            val absDx1: Double = abs(ae1.dx)
            val absDx2: Double = abs(ae2.dx)
            if (absDx1 > 100 && absDx2 > 100) {
                ip = if (absDx1 > absDx2) {
                    getClosestPtOnSegment(ip, ae1.bot!!, ae1.top!!)
                } else {
                    getClosestPtOnSegment(ip, ae2.bot!!, ae2.top!!)
                }
            } else if (absDx1 > 100) {
                ip = getClosestPtOnSegment(ip, ae1.bot!!, ae1.top!!)
            } else if (absDx2 > 100) {
                ip = getClosestPtOnSegment(ip, ae2.bot!!, ae2.top!!)
            } else {
                if (ip.y < topY) { ip.y = topY } else { ip.y = currentBotY }
                if (absDx1 < absDx2) { ip.x = topX(ae1, ip.y) } else { ip.x = topX(ae2, ip.y) }
            }
        }
        val node = IntersectNode(ip, ae1, ae2)
        intersectList.add(node)
    }

    private fun extractFromSEL(ae: Active?): Active? {
        val res = ae!!.nextInSEL
        if (res != null) {
            res.prevInSEL = ae.prevInSEL
        }
        ae.prevInSEL!!.nextInSEL = res
        return res
    }

    private fun buildIntersectList(topY: Long): Boolean {
        if (actives == null || actives!!.nextInAEL == null) {
            return false
        }

        // Calculate edge positions at the top of the current scanbeam, and from this
        // we will determine the intersections required to reach these new positions.
        adjustCurrXAndCopyToSEL(topY)

        // Find all edge intersections in the current scanbeam using a stable merge
        // sort that ensures only adjacent edges are intersecting. Intersect info is
        // stored in FIntersectList ready to be processed in ProcessIntersectList.
        // Re merge sorts see https://stackoverflow.com/a/46319131/359538
        var left = sel
        var right: Active?
        var lEnd: Active?
        var rEnd: Active?
        var currBase: Active?
        var prevBase: Active?
        var tmp: Active?

        while (left!!.jump != null) {
            prevBase = null
            while (left?.jump != null) {
                currBase = left
                right = left.jump
                lEnd = right
                rEnd = right!!.jump
                left.jump = rEnd
                while (left !== lEnd && right !== rEnd) {
                    if (right!!.curX < left!!.curX) {
                        tmp = right.prevInSEL
                        while (true) {
                            addNewIntersectNode(tmp, right, topY)
                            if (left == tmp) {
                                break
                            }
                            tmp = tmp!!.prevInSEL
                        }
                        tmp = right
                        right = extractFromSEL(tmp)
                        lEnd = right
                        insert1Before2InSEL(tmp, left)
                        if (left == currBase) {
                            currBase = tmp
                            currBase.jump = rEnd
                            if (prevBase == null) {
                                sel = currBase
                            } else {
                                prevBase.jump = currBase
                            }
                        }
                    } else {
                        left = left.nextInSEL
                    }
                }
                prevBase = currBase
                left = rEnd
            }
            left = sel
        }
        return intersectList.isNotEmpty()
    }

    private fun processIntersectList() {
        // We now have a list of intersections required so that edges will be
        // correctly positioned at the top of the scanbeam. However, it's important
        // that edge intersections are processed from the bottom up, but it's also
        // crucial that intersections only occur between adjacent edges.

        // First we do a quicksort so intersections proceed in a bottom up order ...
        intersectList.sortWith { a: IntersectNode, b: IntersectNode ->
            if (a.pt.y == b.pt.y) {
                if (a.pt.x == b.pt.x) {
                    return@sortWith 0
                }
                return@sortWith if (a.pt.x < b.pt.x) -1 else 1
            }
            if (a.pt.y > b.pt.y) -1 else 1
        }

        // Now as we process these intersections, we must sometimes adjust the order
        // to ensure that intersecting edges are always adjacent ...
        for (i in intersectList.indices) {
            if (!edgesAdjacentInAEL(intersectList[i])) {
                var j = i + 1
                while (!edgesAdjacentInAEL(intersectList[j])) {
                    j++
                }
                // swap
                intersectList.swap(i, j)
            }
            val node = intersectList[i]
            intersectEdges(node.edge1!!, node.edge2!!, node.pt)
            swapPositionsInAEL(node.edge1, node.edge2)
            node.edge1.curX = node.pt.x
            node.edge2.curX = node.pt.x
            checkJoinLeft(node.edge2, node.pt, true)
            checkJoinRight(node.edge1, node.pt, true)
        }
    }

    private fun swapPositionsInAEL(ae1: Active?, ae2: Active?) {
        // preconditon: ae1 must be immediately to the left of ae2
        val next = ae2!!.nextInAEL
        if (next != null) {
            next.prevInAEL = ae1
        }
        val prev = ae1!!.prevInAEL
        if (prev != null) {
            prev.nextInAEL = ae2
        }
        ae2.prevInAEL = prev
        ae2.nextInAEL = ae1
        ae1.prevInAEL = ae2
        ae1.nextInAEL = next
        if (ae2.prevInAEL == null) {
            actives = ae2
        }
    }

    private fun trimHorz(horzEdge: Active?, preserveCollinear: Boolean) {
        var wasTrimmed = false
        var pt = nextVertex(horzEdge!!)!!.pt
        while (pt.y == horzEdge.top!!.y) {
            // always trim 180 deg. spikes (in closed paths)
            // but otherwise break if preserveCollinear = true
            if (preserveCollinear && pt.x < horzEdge.top!!.x != horzEdge.bot!!.x < horzEdge.top!!.x) {
                break
            }
            horzEdge.vertexTop = nextVertex(horzEdge)
            horzEdge.top = pt
            wasTrimmed = true
            if (isMaxima(horzEdge)) {
                break
            }
            pt = nextVertex(horzEdge)!!.pt
        }
        if (wasTrimmed) {
            setDx(horzEdge) // +/-infinity
        }
    }

    private fun addToHorzSegList(op: OutPt?) {
        if (op!!.outrec!!.isOpen) {
            return
        }
        horzSegList.add(HorzSegment(op))
    }

    private fun getLastOp(hotEdge: Active?): OutPt? {
        val outrec = hotEdge!!.outrec
        return if (hotEdge === outrec!!.frontEdge) outrec!!.pts else outrec!!.pts!!.next
    }

    private fun doHorizontal(horz: Active?) /*-
	 * Notes: Horizontal edges (HEs) at scanline intersections (i.e. at the top or  *
	 * bottom of a scanbeam) are processed as if layered.The order in which HEs     *
	 * are processed doesn't matter. HEs intersect with the bottom vertices of      *
	 * other HEs[#] and with non-horizontal edges [*]. Once these intersections     *
	 * are completed, intermediate HEs are 'promoted' to the next edge in their     *
	 * bounds, and they in turn may be intersected[%] by other HEs.                 *
	 *                                                                              *
	 * eg: 3 horizontals at a scanline:    /   |                     /           /  *
	 *              |                     /    |     (HE3)o ========%========== o   *
	 *              o ======= o(HE2)     /     |         /         /                *
	 *          o ============#=========*======*========#=========o (HE1)           *
	 *         /              |        /       |       /                            *
	 *******************************************************************************/ {
        var pt: Point64
        val horzIsOpen = isOpen(horz)
        val Y = horz!!.bot!!.y
        val vertex_max: ClipperBase.Vertex? =
            if (horzIsOpen) getCurrYMaximaVertex_Open(horz) else getCurrYMaximaVertex(horz)

        // remove 180 deg.spikes and also simplify
        // consecutive horizontals when PreserveCollinear = true
        if (vertex_max != null && !horzIsOpen && vertex_max !== horz.vertexTop) {
            trimHorz(horz, preserveCollinear)
        }
        var leftX: Long
        val tempOutleftX = OutObject<Long>(null)
        var rightX: Long
        val tempOutrightX = OutObject<Long>(null)
        var isLeftToRight = resetHorzDirection(horz, vertex_max, tempOutleftX, tempOutrightX)
        rightX = tempOutrightX.argValue!!
        leftX = tempOutleftX.argValue!!
        if (isHotEdge(horz)) {
            val op = addOutPt(horz, Point64(horz.curX, Y))
            addToHorzSegList(op)
        }
        var currOutrec: ClipperBase.OutRec? = horz.outrec
        while (true) {
            // loops through consec. horizontal edges (if open)
            var ae: ClipperBase.Active? = if (isLeftToRight) horz.nextInAEL else horz.prevInAEL
            while (ae != null) {
                if (ae.vertexTop === vertex_max) {
                    // do this first!!
                    if (isHotEdge(horz) && isJoined(ae)) {
                        split(ae, ae.top)
                    }
                    if (isHotEdge(horz)) {
                        while (horz.vertexTop !== vertex_max) {
                            addOutPt(horz, horz.top!!)
                            updateEdgeIntoAEL(horz)
                        }
                        if (isLeftToRight) {
                            addLocalMaxPoly(horz, ae, horz.top!!)
                        } else {
                            addLocalMaxPoly(ae, horz, horz.top!!)
                        }
                    }
                    deleteFromAEL(ae)
                    deleteFromAEL(horz)
                    return
                }

                // if horzEdge is a maxima, keep going until we reach
                // its maxima pair, otherwise check for break conditions
                if (vertex_max !== horz.vertexTop || isOpenEnd(horz)) {
                    // otherwise stop when 'ae' is beyond the end of the horizontal line
                    if (isLeftToRight && ae.curX > rightX || !isLeftToRight && ae.curX < leftX) {
                        break
                    }
                    if (ae.curX == horz.top!!.x && !isHorizontal(ae)) {
                        pt = nextVertex(horz)!!.pt

                        // to maximize the possibility of putting open edges into
                        // solutions, we'll only break if it's past HorzEdge's end
                        if (isOpen(ae) && !isSamePolyType(ae, horz) && !isHotEdge(ae)) {
                            if (isLeftToRight && topX(ae, pt.y) > pt.x || !isLeftToRight && topX(ae, pt.y) < pt.x) {
                                break
                            }
                        } else if (isLeftToRight && topX(ae, pt.y) >= pt.x || !isLeftToRight && topX(
                                ae,
                                pt.y
                            ) <= pt.x
                        ) {
                            break
                        }
                    }
                }
                pt = Point64(ae.curX, Y)
                if (isLeftToRight) {
                    intersectEdges(horz, ae, pt)
                    swapPositionsInAEL(horz, ae)
                    horz.curX = ae.curX
                    ae = horz.nextInAEL
                } else {
                    intersectEdges(ae, horz, pt)
                    swapPositionsInAEL(ae, horz)
                    horz.curX = ae.curX
                    ae = horz.prevInAEL
                }
                if (isHotEdge(horz) && horz.outrec !== currOutrec) {
                    currOutrec = horz.outrec
                    addToHorzSegList(getLastOp(horz))
                }
            } // we've reached the end of this horizontal

            // check if we've finished looping
            // through consecutive horizontals
            if (horzIsOpen && isOpenEnd(horz)) {
                // ie open at top
                if (isHotEdge(horz)) {
                    addOutPt(horz, horz.top!!)
                    if (isFront(horz)) {
                        horz.outrec!!.frontEdge = null
                    } else {
                        horz.outrec!!.backEdge = null
                    }
                    horz.outrec = null
                }
                deleteFromAEL(horz)
                return
            }
            if (nextVertex(horz)!!.pt.y != horz.top!!.y) {
                break
            }

            // still more horizontals in bound to process ...
            if (isHotEdge(horz)) {
                addOutPt(horz, horz.top!!)
            }
            updateEdgeIntoAEL(horz)
            if (preserveCollinear && !horzIsOpen && horzIsSpike(horz)) {
                trimHorz(horz, true)
            }
            val tempOutleftX2 = OutObject<Long>(null)
            val tempOutrightX2 = OutObject<Long>(null)
            isLeftToRight = resetHorzDirection(horz, vertex_max, tempOutleftX2, tempOutrightX2)
            rightX = tempOutrightX2.argValue!!
            leftX = tempOutleftX2.argValue!!
        }
        if (isHotEdge(horz)) {
            addOutPt(horz, horz.top!!)
        }
        updateEdgeIntoAEL(horz) // this is the end of an intermediate horiz.
    }

    private fun doTopOfScanbeam(y: Long) {
        sel = null // sel is reused to flag horizontals (see PushHorz below)
        var ae = actives
        while (ae != null) {
            // NB 'ae' will never be horizontal here
            if (ae.top!!.y == y) {
                ae.curX = ae.top!!.x
                if (isMaxima(ae)) {
                    ae = doMaxima(ae) // TOP OF BOUND (MAXIMA)
                    continue
                }

                // INTERMEDIATE VERTEX ...
                if (isHotEdge(ae)) {
                    addOutPt(ae, ae.top!!)
                }
                updateEdgeIntoAEL(ae)
                if (isHorizontal(ae)) {
                    pushHorz(ae) // horizontals are processed later
                }
            } else { // i.e. not the top of the edge
                ae.curX = topX(ae, y)
            }
            ae = ae.nextInAEL
        }
    }

    private fun doMaxima(ae: Active): Active? {
        var prevE: Active? = null
        var nextE: Active?
        var maxPair: Active? = null
        prevE = ae.prevInAEL
        nextE = ae.nextInAEL
        if (isOpenEnd(ae)) {
            if (isHotEdge(ae)) {
                addOutPt(ae, ae.top!!)
            }
            if (!isHorizontal(ae)) {
                if (isHotEdge(ae)) {
                    if (isFront(ae)) {
                        ae.outrec!!.frontEdge = null
                    } else {
                        ae.outrec!!.backEdge = null
                    }
                    ae.outrec = null
                }
                deleteFromAEL(ae)
            }
            return nextE
        }
        maxPair = getMaximaPair(ae)
        if (maxPair == null) {
            return nextE // eMaxPair is horizontal
        }
        if (isJoined(ae)) {
            split(ae, ae.top)
        }
        if (isJoined(maxPair)) {
            split(maxPair, maxPair.top)
        }

        // only non-horizontal maxima here.
        // process any edges between maxima pair ...
        while (nextE != maxPair) {
            intersectEdges(ae, nextE!!, ae.top!!)
            swapPositionsInAEL(ae, nextE)
            nextE = ae.nextInAEL
        }
        if (isOpen(ae)) {
            if (isHotEdge(ae)) {
                addLocalMaxPoly(ae, maxPair, ae.top!!)
            }
            deleteFromAEL(maxPair)
            deleteFromAEL(ae)
            return if (prevE != null) prevE.nextInAEL else actives
        }

        // here ae.nextInAel == ENext == EMaxPair ...
        if (isHotEdge(ae)) {
            addLocalMaxPoly(ae, maxPair, ae.top!!)
        }
        deleteFromAEL(ae)
        deleteFromAEL(maxPair)
        return if (prevE != null) prevE.nextInAEL else actives
    }

    private fun split(e: Active?, currPt: Point64?) {
        if (e!!.joinWith == JoinWith.Right) {
            e.joinWith = JoinWith.None
            e.nextInAEL!!.joinWith = JoinWith.None
            addLocalMinPoly(e, e.nextInAEL, currPt, true)
        } else {
            e.joinWith = JoinWith.None
            e.prevInAEL!!.joinWith = JoinWith.None
            addLocalMinPoly(e.prevInAEL, e, currPt, true)
        }
    }

    private fun checkJoinLeft(e: Active?, pt: Point64?, checkCurrX: Boolean = false) {
        val prev: ClipperBase.Active? = e!!.prevInAEL
        if (prev == null || isOpen(e) || isOpen(prev) || !isHotEdge(e) || !isHotEdge(prev) || pt!!.y < e.top!!.y + 2 || pt.y < prev.top!!.y + 2) {
            return
        }
        if (checkCurrX) {
            if (perpendicDistFromLineSqrd(pt, prev.bot!!, prev.top!!) > 0.25) {
                return
            }
        } else if (e.curX != prev.curX) {
            return
        }
        if (crossProduct(e.top!!, pt, prev.top!!) != 0.0) {
            return
        }
        if (e.outrec!!.idx == prev.outrec!!.idx) {
            addLocalMaxPoly(prev, e, pt)
        } else if (e.outrec!!.idx < prev.outrec!!.idx) {
            joinOutrecPaths(e, prev)
        } else {
            joinOutrecPaths(prev, e)
        }
        prev.joinWith = JoinWith.Right
        e.joinWith = JoinWith.Left
    }

    private fun checkJoinRight(e: Active?, pt: Point64?, checkCurrX: Boolean = false) {
        val next: ClipperBase.Active? = e!!.nextInAEL
        if (isOpen(e) || !isHotEdge(e) || isJoined(e) || next == null || isOpen(next) || !isHotEdge(next) || pt!!.y < e.top!!.y + 2 || pt.y < next.top!!.y + 2) {
            return
        }
        if (checkCurrX) {
            if (perpendicDistFromLineSqrd(pt, next.bot!!, next.top!!) > 0.25) {
                return
            }
        } else if (e.curX != next.curX) {
            return
        }
        if (crossProduct(e.top!!, pt, next.top!!) != 0.0) {
            return
        }
        if (e.outrec!!.idx == next.outrec!!.idx) {
            addLocalMaxPoly(e, next, pt)
        } else if (e.outrec!!.idx < next.outrec!!.idx) {
            joinOutrecPaths(e, next)
        } else {
            joinOutrecPaths(next, e)
        }
        e.joinWith = JoinWith.Right
        next.joinWith = JoinWith.Left
    }

    private fun convertHorzSegsToJoins() {
        var k = 0
        for (hs in horzSegList) {
            if (updateHorzSegment(hs)) {
                k++
            }
        }
        if (k < 2) {
            return
        }
        horzSegList.sortWith(HorzSegSorter())
        for (i in 0 until k - 1) {
            val hs1 = horzSegList[i]
            // for each HorzSegment, find others that overlap
            for (j in i + 1 until k) {
                val hs2 = horzSegList[j]
                if (hs2.leftOp!!.pt.x >= hs1.rightOp!!.pt.x) {
                    break
                }
                if (hs2.leftToRight == hs1.leftToRight || hs2.rightOp!!.pt.x <= hs1.leftOp!!.pt.x) {
                    continue
                }
                val curr_y = hs1.leftOp!!.pt.y
                if (hs1.leftToRight) {
                    while (hs1.leftOp!!.next.pt.y == curr_y && hs1.leftOp!!.next.pt.x <= hs2.leftOp!!.pt.x) {
                        hs1.leftOp = hs1.leftOp!!.next
                    }
                    while (hs2.leftOp!!.prev!!.pt.y == curr_y && hs2.leftOp!!.prev!!.pt.x <= hs1.leftOp!!.pt.x) {
                        hs2.leftOp = hs2.leftOp!!.prev
                    }
                    val join = HorzJoin(
                        duplicateOp(hs1.leftOp!!, true),
                        duplicateOp(
                            hs2.leftOp!!,
                            false
                        )
                    )
                    _horzJoinList.add(join)
                } else {
                    while (hs1.leftOp!!.prev!!.pt.y == curr_y && hs1.leftOp!!.prev!!.pt.x <= hs2.leftOp!!.pt.x) {
                        hs1.leftOp = hs1.leftOp!!.prev
                    }
                    while (hs2.leftOp!!.next.pt.y == curr_y && hs2.leftOp!!.next.pt.x <= hs1.leftOp!!.pt.x) {
                        hs2.leftOp = hs2.leftOp!!.next
                    }
                    val join = HorzJoin(
                        duplicateOp(hs2.leftOp!!, true),
                        duplicateOp(
                            hs1.leftOp!!,
                            false
                        )
                    )
                    _horzJoinList.add(join)
                }
            }
        }
    }

    private fun processHorzJoins() {
        for (j in _horzJoinList) {
            val or1 = getRealOutRec(j.op1.outrec)
            var or2 = getRealOutRec(j.op2.outrec)
            val op1b = j.op1.next
            val op2b = j.op2.prev
            j.op1.next = j.op2
            j.op2.prev = j.op1
            op1b.prev = op2b
            op2b!!.next = op1b
            if (or1 === or2) {
                or2 = OutRec()
                or2.pts = op1b
                fixOutRecPts(or2)
                if (or1!!.pts!!.outrec === or2) {
                    or1!!.pts = j.op1
                    or1.pts!!.outrec = or1
                }
                if (usingPolytree) {
                    if (path1InsidePath2(or2.pts!!, or1!!.pts!!)) {
                        setOwner(or2, or1)
                    } else if (path1InsidePath2(or1.pts!!, or2.pts!!)) {
                        setOwner(or1, or2)
                    } else {
                        or2.owner = or1
                    }
                } else {
                    or2.owner = or1
                }
                outrecList.add(or2)
            } else {
                or2!!.pts = null
                if (usingPolytree) {
                    setOwner(or2, or1!!)
                } else {
                    or2.owner = or1
                }
            }
        }
    }

    private fun cleanCollinear(outrec: OutRec?) {
        var outrec = outrec
        outrec = getRealOutRec(outrec)
        if (outrec == null || outrec.isOpen) {
            return
        }
        if (!isValidClosedPath(outrec.pts)) {
            outrec.pts = null
            return
        }
        var startOp = outrec.pts
        var op2 = startOp
        while (true) {
            // NB if preserveCollinear == true, then only remove 180 deg. spikes
            if (crossProduct(op2!!.prev!!.pt, op2.pt, op2.next.pt) == 0.0 &&
                (
                    op2.pt.opEquals(op2.prev!!.pt) || op2.pt.opEquals(op2.next.pt) || !preserveCollinear || dotProduct(
                            op2.prev!!.pt,
                            op2.pt,
                            op2.next.pt
                        ) < 0
                    )
            ) {
                if (op2 == outrec.pts) {
                    outrec.pts = op2.prev
                }
                op2 = disposeOutPt(op2)
                if (!isValidClosedPath(op2)) {
                    outrec.pts = null
                    return
                }
                startOp = op2
                continue
            }
            op2 = op2.next
            if (op2 == startOp) {
                break
            }
        }
        fixSelfIntersects(outrec)
    }

    private fun doSplitOp(outrec: OutRec, splitOp: OutPt) {
        // splitOp.prev <=> splitOp &&
        // splitOp.next <=> splitOp.next.next are intersecting
        val prevOp = splitOp.prev
        val nextNextOp = splitOp.next.next
        outrec.pts = prevOp
        // 		OutPt result = prevOp;
        val tmp = PointD()
        getIntersectPoint(prevOp!!.pt, splitOp.pt, splitOp.next.pt, nextNextOp.pt, tmp)
        val ip = Point64(tmp)
        val area1 = area(prevOp)
        val absArea1: Double = abs(area1)
        if (absArea1 < 2) {
            outrec.pts = null
            return
        }

        // nb: area1 is the path's area *before* splitting, whereas area2 is
        // the area of the triangle containing splitOp & splitOp.next.
        // So the only way for these areas to have the same sign is if
        // the split triangle is larger than the path containing prevOp or
        // if there's more than one self=intersection.
        val area2 = areaTriangle(ip, splitOp.pt, splitOp.next.pt)
        val absArea2: Double = abs(area2)

        // de-link splitOp and splitOp.next from the path
        // while inserting the intersection point
        if (ip.opEquals(prevOp.pt) || ip.opEquals(nextNextOp.pt)) {
            nextNextOp.prev = prevOp
            prevOp.next = nextNextOp
        } else {
            val newOp2 = OutPt(ip, outrec)
            newOp2.prev = prevOp
            newOp2.next = nextNextOp
            nextNextOp.prev = newOp2
            prevOp.next = newOp2
        }
        if (absArea2 > 1 && (absArea2 > absArea1 || area2 > 0 == area1 > 0)) {
            val newOutRec = newOutRec()
            newOutRec.owner = outrec.owner
            splitOp.outrec = newOutRec
            splitOp.next.outrec = newOutRec
            if (usingPolytree) {
                if (outrec.splits == null) {
                    outrec.splits = mutableListOf<Int>()
                }
                outrec.splits!!.add(newOutRec.idx)
            }
            val newOp = OutPt(ip, newOutRec)
            newOp.prev = splitOp.next
            newOp.next = splitOp
            newOutRec.pts = newOp
            splitOp.prev = newOp
            splitOp.next.next = newOp
        }
        // else { splitOp = null; splitOp.next = null; }
    }

    private fun fixSelfIntersects(outrec: OutRec) {
        var op2 = outrec.pts
        while (true) {
            // triangles can't self-intersect
            if (op2!!.prev === op2!!.next.next) {
                break
            }
            if (segsIntersect(op2!!.prev!!.pt, op2.pt, op2.next.pt, op2.next.next.pt)) {
                doSplitOp(outrec, op2)
                if (outrec.pts == null) {
                    return
                }
                op2 = outrec.pts
                continue
            } else {
                op2 = op2.next
            }
            if (op2 === outrec.pts) {
                break
            }
        }
    }

    protected fun buildPaths(solutionClosed: Paths64, solutionOpen: Paths64): Boolean {
        solutionClosed.clear()
        solutionOpen.clear()
        var i = 0
        // _outrecList.Count is not static here because
        // CleanCollinear can indirectly add additional OutRec
        while (i < outrecList.size) {
            val outrec = outrecList[i++]
            if (outrec.pts == null) {
                continue
            }
            val path = Path64()
            if (outrec.isOpen) {
                if (buildPath(outrec.pts, reverseSolution, true, path)) {
                    solutionOpen.add(path)
                }
            } else {
                cleanCollinear(outrec)
                // closed paths should always return a Positive orientation
                // except when reverseSolution == true
                if (buildPath(outrec.pts, reverseSolution, false, path)) {
                    solutionClosed.add(path)
                }
            }
        }
        return true
    }

    private fun checkBounds(outrec: OutRec?): Boolean {
        if (outrec!!.pts == null) {
            return false
        }
        if (!outrec.bounds.isEmpty()) {
            return true
        }
        cleanCollinear(outrec)
        if (outrec.pts == null || !buildPath(outrec.pts, reverseSolution, false, outrec.path)) {
            return false
        }
        outrec.bounds = getBounds(outrec.path)
        return true
    }

    private fun recursiveCheckOwners(outrec: OutRec?, polypath: PolyPathBase) {
        // pre-condition: outrec will have valid bounds
        // post-condition: if a valid path, outrec will have a polypath
        if (outrec!!.polypath != null || outrec.bounds.isEmpty()) {
            return
        }
        while (outrec.owner != null && (outrec.owner!!.pts == null || !checkBounds(outrec.owner))) {
            outrec.owner = outrec.owner!!.owner
        }
        if (outrec.owner != null && outrec.owner!!.polypath == null) {
            recursiveCheckOwners(outrec.owner, polypath)
        }
        while (outrec.owner != null) {
            if (outrec.owner!!.bounds.contains(outrec.bounds) && path1InsidePath2(outrec.pts!!, outrec.owner!!.pts!!)) {
                break // found - owner contain outrec!
            } else {
                outrec.owner = outrec.owner!!.owner
            }
        }
        if (outrec.owner != null) {
            outrec.polypath = outrec.owner!!.polypath!!.addChild(outrec.path)
        } else {
            outrec.polypath = polypath.addChild(outrec.path)
        }
    }

    private fun deepCheckOwners(outrec: OutRec, polypath: PolyPathBase) {
        recursiveCheckOwners(outrec, polypath)
        while (outrec.owner != null && outrec.owner!!.splits != null) {
            var split: OutRec? = null
            for (i in outrec.owner!!.splits!!) {
                split = getRealOutRec(outrecList[i])
                if (split != null && split !== outrec && split !== outrec.owner && checkBounds(split) && split.bounds.contains(
                        outrec.bounds
                    ) &&
                    path1InsidePath2(outrec.pts!!, split.pts!!)
                ) {
                    recursiveCheckOwners(split, polypath)
                    outrec.owner = split // found in split
                    break // inner 'for' loop
                } else {
                    split = null
                }
            }
            if (split == null) {
                break
            }
        }
    }

    protected fun buildTree(polytree: PolyPathBase, solutionOpen: Paths64) {
        polytree.clear()
        solutionOpen.clear()
        var i = 0
        // _outrecList.Count is not static here because
        // CheckBounds below can indirectly add additional
        // OutRec (via FixOutRecPts & CleanCollinear)
        while (i < outrecList.size) {
            val outrec = outrecList[i++]
            if (outrec.pts == null) {
                continue
            }
            if (outrec.isOpen) {
                val open_path = Path64()
                if (buildPath(outrec.pts, reverseSolution, true, open_path)) {
                    solutionOpen.add(open_path)
                }
                continue
            }
            if (checkBounds(outrec)) {
                deepCheckOwners(outrec, polytree)
            }
        }
    }

    fun getBounds(): Rect64 {
        val bounds = Clipper.InvalidRect64
        for (t in vertexList) {
            var v = t
            do {
                if (v!!.pt.x < bounds.left) {
                    bounds.left = v.pt.x
                }
                if (v.pt.x > bounds.right) {
                    bounds.right = v.pt.x
                }
                if (v.pt.y < bounds.top) {
                    bounds.top = v.pt.y
                }
                if (v.pt.y > bounds.bottom) {
                    bounds.bottom = v.pt.y
                }
                v = v.next
            } while (v !== t)
        }
        return if (bounds.isEmpty()) Rect64(0, 0, 0, 0) else bounds
    }

    companion object {
        private fun isOdd(v: Int): Boolean {
            return v and 1 != 0
        }

        private fun isHotEdge(ae: Active?): Boolean {
            return ae!!.outrec != null
        }

        private fun isOpen(ae: Active?): Boolean {
            return ae!!.localMin.isOpen
        }

        private fun isOpenEnd(ae: Active?): Boolean {
            return ae!!.localMin.isOpen && isOpenEnd(ae.vertexTop)
        }

        private fun isOpenEnd(v: Vertex?): Boolean {
            return v!!.flags and (VertexFlags.OpenStart or VertexFlags.OpenEnd) != VertexFlags.None
        }

        private fun getPrevHotEdge(ae: Active?): Active? {
            var prev = ae!!.prevInAEL
            while (prev != null && (isOpen(prev) || !isHotEdge(prev))) {
                prev = prev.prevInAEL
            }
            return prev
        }

        private fun isFront(ae: Active?): Boolean {
            return ae === ae!!.outrec!!.frontEdge
        }

        private fun getDx(pt1: Point64, pt2: Point64): Double {
            /*-
		 *  Dx:                             0(90deg)                                    *
		 *                                  |                                           *
		 *               +inf (180deg) <--- o --. -inf (0deg)                           *
		 *******************************************************************************/
            val dy = (pt2.y - pt1.y).toDouble()
            if (dy != 0.0) {
                return (pt2.x - pt1.x) / dy
            }
            return if (pt2.x > pt1.x) {
                Double.NEGATIVE_INFINITY
            } else {
                Double.POSITIVE_INFINITY
            }
        }

        private fun topX(ae: Active, currentY: Long): Long {
            if (currentY == ae.top!!.y || ae.top!!.x == ae.bot!!.x) {
                return ae.top!!.x
            }
            return if (currentY == ae.bot!!.y) {
                ae.bot!!.x
            } else {
                ae.bot!!.x + (ae.dx * (currentY - ae.bot!!.y)).roundToLong()
            }
        }

        private fun isHorizontal(ae: Active): Boolean {
            return ae.top!!.y == ae.bot!!.y
        }

        private fun isHeadingRightHorz(ae: Active): Boolean {
            return Double.NEGATIVE_INFINITY == ae.dx
        }

        private fun isHeadingLeftHorz(ae: Active): Boolean {
            return Double.POSITIVE_INFINITY == ae.dx
        }

        private fun swapActives(ae1: RefObject<Active>, ae2: RefObject<Active>) {
            val temp = ae1.argValue
            ae1.argValue = ae2.argValue
            ae2.argValue = temp
        }

        private fun getPolyType(ae: Active): PathType? {
            return ae.localMin.polytype
        }

        private fun isSamePolyType(ae1: Active, ae2: Active): Boolean {
            return ae1.localMin.polytype === ae2.localMin.polytype
        }

        private fun setDx(ae: Active) {
            ae.dx = getDx(ae.bot!!, ae.top!!)
        }

        private fun nextVertex(ae: Active): Vertex? {
            return if (ae.windDx > 0) {
                ae.vertexTop!!.next
            } else {
                ae.vertexTop!!.prev
            }
        }

        private fun prevPrevVertex(ae: Active): Vertex? {
            return if (ae.windDx > 0) {
                ae.vertexTop!!.prev!!.prev
            } else {
                ae.vertexTop!!.next!!.next
            }
        }

        private fun isMaxima(vertex: Vertex): Boolean {
            return vertex.flags and VertexFlags.LocalMax != VertexFlags.None
        }

        private fun isMaxima(ae: Active): Boolean {
            return isMaxima(ae.vertexTop!!)
        }

        private fun getMaximaPair(ae: Active): Active? {
            var ae2: Active? = null
            ae2 = ae.nextInAEL
            while (ae2 != null) {
                if (ae2.vertexTop === ae.vertexTop) {
                    return ae2 // Found!
                }
                ae2 = ae2.nextInAEL
            }
            return null
        }

        private fun getCurrYMaximaVertex_Open(ae: Active): Vertex? {
            var result: ClipperBase.Vertex? = ae.vertexTop
            if (ae.windDx > 0) {
                while (result!!.next!!.pt.y == result.pt.y && result.flags and (VertexFlags.OpenEnd or VertexFlags.LocalMax) == VertexFlags.None) {
                    result = result.next
                }
            } else {
                while (result!!.prev!!.pt.y == result.pt.y && result.flags and (VertexFlags.OpenEnd or VertexFlags.LocalMax) == VertexFlags.None) {
                    result = result.prev
                }
            }
            if (!isMaxima(result)) {
                result = null // not a maxima
            }
            return result
        }

        private fun getCurrYMaximaVertex(ae: Active): Vertex? {
            var result = ae.vertexTop
            if (ae.windDx > 0) {
                while (result!!.next!!.pt.y == result.pt.y) {
                    result = result.next
                }
            } else {
                while (result!!.prev!!.pt.y == result.pt.y) {
                    result = result.prev
                }
            }
            if (!isMaxima(result)) {
                result = null // not a maxima
            }
            return result
        }

        private fun setSides(outrec: OutRec, startEdge: Active?, endEdge: Active?) {
            outrec.frontEdge = startEdge
            outrec.backEdge = endEdge
        }

        private fun swapOutrecs(ae1: Active, ae2: Active) {
            val or1 = ae1.outrec // at least one edge has
            val or2 = ae2.outrec // an assigned outrec
            if (or1 === or2) {
                val ae = or1!!.frontEdge
                or1.frontEdge = or1.backEdge
                or1.backEdge = ae
                return
            }
            if (or1 != null) {
                if (ae1 === or1.frontEdge) {
                    or1.frontEdge = ae2
                } else {
                    or1.backEdge = ae2
                }
            }
            if (or2 != null) {
                if (ae2 === or2.frontEdge) {
                    or2.frontEdge = ae1
                } else {
                    or2.backEdge = ae1
                }
            }
            ae1.outrec = or2
            ae2.outrec = or1
        }

        private fun setOwner(outrec: OutRec, newOwner: OutRec) {
            // precondition1: new_owner is never null
            while (newOwner.owner != null && newOwner.owner!!.pts == null) {
                newOwner.owner = newOwner.owner!!.owner
            }

            // make sure that outrec isn't an owner of newOwner
            var tmp: ClipperBase.OutRec? = newOwner
            while (tmp != null && tmp !== outrec) {
                tmp = tmp.owner
            }
            if (tmp != null) {
                newOwner.owner = outrec.owner
            }
            outrec.owner = newOwner
        }

        private fun area(op: OutPt): Double {
            // https://en.wikipedia.org/wiki/Shoelaceformula
            var area = 0.0
            var op2 = op
            do {
                area += ((op2.prev!!.pt.y + op2.pt.y) * (op2.prev!!.pt.x - op2.pt.x)).toDouble()
                op2 = op2.next
            } while (op2 !== op)
            return area * 0.5
        }

        private fun areaTriangle(pt1: Point64, pt2: Point64, pt3: Point64): Double {
            return ((pt3.y + pt1.y) * (pt3.x - pt1.x) + (pt1.y + pt2.y) * (pt1.x - pt2.x) + (pt2.y + pt3.y) * (pt2.x - pt3.x)).toDouble()
        }

        private fun getRealOutRec(outRec: OutRec?): OutRec? {
            var outRec = outRec
            while (outRec != null && outRec.pts == null) {
                outRec = outRec.owner
            }
            return outRec
        }

        private fun uncoupleOutRec(ae: Active) {
            val outrec = ae.outrec ?: return
            outrec.frontEdge!!.outrec = null
            outrec.backEdge!!.outrec = null
            outrec.frontEdge = null
            outrec.backEdge = null
        }

        private fun outrecIsAscending(hotEdge: Active): Boolean {
            return hotEdge === hotEdge.outrec!!.frontEdge
        }

        private fun swapFrontBackSides(outrec: OutRec) {
            // while this proc. is needed for open paths
            // it's almost never needed for closed paths
            val ae2 = outrec.frontEdge
            outrec.frontEdge = outrec.backEdge
            outrec.backEdge = ae2
            outrec.pts = outrec.pts!!.next
        }

        private fun edgesAdjacentInAEL(inode: IntersectNode): Boolean {
            return inode.edge1!!.nextInAEL === inode.edge2 || inode.edge1!!.prevInAEL === inode.edge2
        }

        private fun isValidAelOrder(resident: Active, newcomer: Active): Boolean {
            if (newcomer.curX != resident.curX) {
                return newcomer.curX > resident.curX
            }

            // get the turning direction a1.top, a2.bot, a2.top
            val d = crossProduct(resident.top!!, newcomer.bot!!, newcomer.top!!)
            if (d != 0.0) {
                return d < 0
            }

            // edges must be collinear to get here

            // for starting open paths, place them according to
            // the direction they're about to turn
            if (!isMaxima(resident) && resident.top!!.y > newcomer.top!!.y) {
                return crossProduct(newcomer.bot!!, resident.top!!, nextVertex(resident)!!.pt) <= 0
            }
            if (!isMaxima(newcomer) && newcomer.top!!.y > resident.top!!.y) {
                return crossProduct(newcomer.bot!!, newcomer.top!!, nextVertex(newcomer)!!.pt) >= 0
            }
            val y = newcomer.bot!!.y
            val newcomerIsLeft = newcomer.isLeftBound
            if (resident.bot!!.y != y || resident.localMin.vertex!!.pt.y != y) {
                return newcomer.isLeftBound
            }
            // resident must also have just been inserted
            if (resident.isLeftBound != newcomerIsLeft) {
                return newcomerIsLeft
            }
            return if (crossProduct(
                    prevPrevVertex(resident)!!.pt,
                    resident.bot!!,
                    resident.top!!
                ) == 0.0
            ) {
                true
            } else {
                crossProduct(
                    prevPrevVertex(resident)!!.pt,
                    newcomer.bot!!,
                    prevPrevVertex(newcomer)!!.pt
                ) > 0 == newcomerIsLeft
            }
            // compare turning direction of the alternate bound
        }

        private fun joinOutrecPaths(ae1: Active, ae2: Active) {
            // join ae2 outrec path onto ae1 outrec path and then delete ae2 outrec path
            // pointers. (NB Only very rarely do the joining ends share the same coords.)
            val p1Start = ae1.outrec!!.pts
            val p2Start = ae2.outrec!!.pts
            val p1End = p1Start!!.next
            val p2End = p2Start!!.next
            if (isFront(ae1)) {
                p2End.prev = p1Start
                p1Start.next = p2End
                p2Start.next = p1End
                p1End.prev = p2Start
                ae1.outrec!!.pts = p2Start
                // nb: if IsOpen(e1) then e1 & e2 must be a 'maximaPair'
                ae1.outrec!!.frontEdge = ae2.outrec!!.frontEdge
                if (ae1.outrec!!.frontEdge != null) {
                    ae1.outrec!!.frontEdge!!.outrec = ae1.outrec
                }
            } else {
                p1End.prev = p2Start
                p2Start.next = p1End
                p1Start.next = p2End
                p2End.prev = p1Start
                ae1.outrec!!.backEdge = ae2.outrec!!.backEdge
                if (ae1.outrec!!.backEdge != null) {
                    ae1.outrec!!.backEdge!!.outrec = ae1.outrec
                }
            }

            // after joining, the ae2.OutRec must contains no vertices ...
            ae2.outrec!!.frontEdge = null
            ae2.outrec!!.backEdge = null
            ae2.outrec!!.pts = null
            setOwner(ae2.outrec!!, ae1.outrec!!)
            if (isOpenEnd(ae1)) {
                ae2.outrec!!.pts = ae1.outrec!!.pts
                ae1.outrec!!.pts = null
            }

            // and ae1 and ae2 are maxima and are about to be dropped from the Actives list.
            ae1.outrec = null
            ae2.outrec = null
        }

        private fun addOutPt(ae: Active, pt: Point64): OutPt {
            // Outrec.OutPts: a circular doubly-linked-list of POutPt where ...
            // opFront[.Prev]* ~~~> opBack & opBack == opFront.Next
            val outrec = ae.outrec
            val toFront = isFront(ae)
            val opFront = outrec!!.pts
            val opBack = opFront!!.next
            if (toFront && pt.opEquals(opFront.pt)) {
                return opFront
            } else if (!toFront && pt.opEquals(opBack.pt)) {
                return opBack
            }
            val newOp = OutPt(pt, outrec)
            opBack.prev = newOp
            newOp.prev = opFront
            newOp.next = opBack
            opFront.next = newOp
            if (toFront) {
                outrec.pts = newOp
            }
            return newOp
        }

        private fun findEdgeWithMatchingLocMin(e: Active): Active? {
            var result = e.nextInAEL
            while (result != null) {
                if (result.localMin.opEquals(e.localMin)) {
                    return result
                }
                result = if (!isHorizontal(result) && e.bot!!.opNotEquals(result.bot!!)) {
                    null
                } else {
                    result.nextInAEL
                }
            }
            result = e.prevInAEL
            while (result != null) {
                if (result.localMin.opEquals(e.localMin)) {
                    return result
                }
                if (!isHorizontal(result) && e.bot!!.opNotEquals(result.bot!!)) {
                    return null
                }
                result = result.prevInAEL
            }
            return result
        }

        private fun insert1Before2InSEL(ae1: Active, ae2: Active) {
            ae1.prevInSEL = ae2.prevInSEL
            if (ae1.prevInSEL != null) {
                ae1.prevInSEL!!.nextInSEL = ae1
            }
            ae1.nextInSEL = ae2
            ae2.prevInSEL = ae1
        }

        private fun resetHorzDirection(
            horz: Active,
            vertexMax: Vertex?,
            leftX: OutObject<Long>,
            rightX: OutObject<Long>
        ): Boolean {
            if (horz.bot!!.x == horz.top!!.x) {
                // the horizontal edge is going nowhere ...
                leftX.argValue = horz.curX
                rightX.argValue = horz.curX
                var ae = horz.nextInAEL
                while (ae != null && ae.vertexTop !== vertexMax) {
                    ae = ae.nextInAEL
                }
                return ae != null
            }
            if (horz.curX < horz.top!!.x) {
                leftX.argValue = horz.curX
                rightX.argValue = horz.top!!.x
                return true
            }
            leftX.argValue = horz.top!!.x
            rightX.argValue = horz.curX
            return false // right to left
        }

        private fun horzIsSpike(horz: Active): Boolean {
            val nextPt = nextVertex(horz)!!.pt
            return horz.bot!!.x < horz.top!!.x != horz.top!!.x < nextPt.x
        }

        private fun isJoined(e: Active): Boolean {
            return e.joinWith != JoinWith.None
        }

        private fun fixOutRecPts(outrec: OutRec) {
            var op = outrec.pts
            do {
                op!!.outrec = outrec
                op = op.next
            } while (op !== outrec.pts)
        }

        private fun setHorzSegHeadingForward(hs: HorzSegment, opP: OutPt, opN: OutPt): Boolean {
            if (opP.pt.x == opN.pt.x) {
                return false
            }
            if (opP.pt.x < opN.pt.x) {
                hs.leftOp = opP
                hs.rightOp = opN
                hs.leftToRight = true
            } else {
                hs.leftOp = opN
                hs.rightOp = opP
                hs.leftToRight = false
            }
            return true
        }

        private fun updateHorzSegment(hs: HorzSegment): Boolean {
            val op = hs.leftOp
            val outrec = getRealOutRec(op!!.outrec)
            val outrecHasEdges = outrec!!.frontEdge != null
            val curr_y = op.pt.y
            var opP = op
            var opN = op
            if (outrecHasEdges) {
                val opA = outrec.pts
                val opZ = opA!!.next
                while (opP !== opZ && opP!!.prev!!.pt.y == curr_y) {
                    opP = opP.prev
                }
                while (opN !== opA && opN!!.next.pt.y == curr_y) {
                    opN = opN.next
                }
            } else {
                while (opP!!.prev !== opN && opP!!.prev!!.pt.y == curr_y) {
                    opP = opP.prev
                }
                while (opN!!.next !== opP && opN!!.next.pt.y == curr_y) {
                    opN = opN.next
                }
            }
            val result = setHorzSegHeadingForward(hs, opP!!, opN!!) && hs.leftOp!!.horz == null
            if (result) {
                hs.leftOp!!.horz = hs
            } else {
                hs.rightOp = null // (for sorting)
            }
            return result
        }

        private fun duplicateOp(op: OutPt, insertAfter: Boolean): OutPt {
            val result = OutPt(op.pt, op.outrec)
            if (insertAfter) {
                result.next = op.next
                result.next.prev = result
                result.prev = op
                op.next = result
            } else {
                result.prev = op.prev
                result.prev!!.next = result
                result.next = op
                op.prev = result
            }
            return result
        }

        private fun getBounds(op: OutPt): Rect64 {
            val result = Rect64(op.pt.x, op.pt.y, op.pt.x, op.pt.y)
            var op2 = op.next
            while (op2 !== op) {
                if (op2.pt.x < result.left) {
                    result.left = op2.pt.x
                } else if (op2.pt.x > result.right) {
                    result.right = op2.pt.x
                }
                if (op2.pt.y < result.top) {
                    result.top = op2.pt.y
                } else if (op2.pt.y > result.bottom) {
                    result.bottom = op2.pt.y
                }
                op2 = op2.next
            }
            return result
        }

        private fun pointInOpPolygon(pt: Point64, op: OutPt): PointInPolygonResult {
            var op = op
            if (op === op.next || op.prev === op.next) {
                return PointInPolygonResult.IsOutside
            }
            var op2 = op
            do {
                if (op.pt.y != pt.y) {
                    break
                }
                op = op.next
            } while (op !== op2)
            if (op.pt.y == pt.y) { // not a proper polygon
                return PointInPolygonResult.IsOutside
            }

            // must be above or below to get here
            var isAbove = op.pt.y < pt.y
            val startingAbove = isAbove
            var v = 0
            op2 = op.next
            while (op2 !== op) {
                if (isAbove) {
                    while (op2 !== op && op2.pt.y < pt.y) {
                        op2 = op2.next
                    }
                } else {
                    while (op2 !== op && op2.pt.y > pt.y) {
                        op2 = op2.next
                    }
                }
                if (op2 === op) {
                    break
                }

                // must have touched or crossed the pt.y horizonal
                // and this must happen an even number of times
                if (op2.pt.y == pt.y) // touching the horizontal
                    {
                        if (op2.pt.x == pt.x || op2.pt.y == op2.prev!!.pt.y && pt.x < op2.prev!!.pt.x != pt.x < op2.pt.x) {
                            return PointInPolygonResult.IsOn
                        }
                        op2 = op2.next
                        if (op2 === op) {
                            break
                        }
                        continue
                    }
                if (op2.pt.x <= pt.x || op2.prev!!.pt.x <= pt.x) {
                    if (op2.prev!!.pt.x < pt.x && op2.pt.x < pt.x) {
                        v = 1 - v // toggle val
                    } else {
                        val d = crossProduct(op2.prev!!.pt, op2.pt, pt)
                        if (d == 0.0) {
                            return PointInPolygonResult.IsOn
                        }
                        if (d < 0 == isAbove) {
                            v = 1 - v
                        }
                    }
                }
                isAbove = !isAbove
                op2 = op2.next
            }
            if (isAbove != startingAbove) {
                val d = crossProduct(op2.prev!!.pt, op2.pt, pt)
                if (d == 0.0) {
                    return PointInPolygonResult.IsOn
                }
                if (d < 0 == isAbove) {
                    v = 1 - v
                }
            }
            return if (v == 0) {
                PointInPolygonResult.IsOutside
            } else {
                PointInPolygonResult.IsInside
            }
        }

        private fun path1InsidePath2(op1: OutPt, op2: OutPt): Boolean {
            // we need to make some accommodation for rounding errors
            // so we won't jump if the first vertex is found outside
            var outside_cnt = 0
            var op = op1
            do {
                val result = pointInOpPolygon(op.pt, op2)
                if (result === PointInPolygonResult.IsOutside) {
                    ++outside_cnt
                } else if (result === PointInPolygonResult.IsInside) {
                    --outside_cnt
                }
                op = op.next
            } while (op !== op1 && abs(outside_cnt) < 2)
            if (abs(outside_cnt) > 1) {
                return outside_cnt < 0
            }
            // since path1's location is still equivocal, check its midpoint
            val mp = getBounds(op).midPoint()
            return pointInOpPolygon(mp, op2) === PointInPolygonResult.IsInside
        }

        private fun ptsReallyClose(pt1: Point64, pt2: Point64): Boolean {
            return abs(pt1.x - pt2.x) < 2 && abs(pt1.y - pt2.y) < 2
        }

        private fun isVerySmallTriangle(op: OutPt): Boolean {
            return (
                op.next.next === op.prev &&
                    (
                        ptsReallyClose(op.prev!!.pt, op.next.pt) || ptsReallyClose(
                            op.pt,
                            op.next.pt
                        ) || ptsReallyClose(op.pt, op.prev!!.pt)
                        )
                )
        }

        private fun isValidClosedPath(op: OutPt?): Boolean {
            return op != null && op.next !== op && (op.next !== op.prev || !isVerySmallTriangle(op))
        }

        private fun disposeOutPt(op: OutPt): OutPt? {
            val result: ClipperBase.OutPt? = if (op.next === op) null else op.next
            op.prev!!.next = op.next
            op.next.prev = op.prev
            // op == null;
            return result
        }

        fun buildPath(op: OutPt?, reverse: Boolean, isOpen: Boolean, path: Path64): Boolean {
            var op = op
            if (op == null || op.next === op || !isOpen && op.next === op.prev) {
                return false
            }
            path.clear()
            var lastPt: Point64
            var op2: OutPt?
            if (reverse) {
                lastPt = op.pt
                op2 = op.prev
            } else {
                op = op.next
                lastPt = op.pt
                op2 = op.next
            }
            path.add(lastPt)
            while (op2 !== op) {
                if (op2!!.pt.opNotEquals(lastPt)) {
                    lastPt = op2.pt
                    path.add(lastPt)
                }
                op2 = if (reverse) {
                    op2.prev
                } else {
                    op2.next
                }
            }
            return !(path.size == 3 && isVerySmallTriangle(op2))
        }

        fun getBounds(path: Path64): Rect64 {
            if (path.isEmpty()) {
                return Rect64()
            }
            val result = Clipper.InvalidRect64
            for (pt in path) {
                if (pt.x < result.left) {
                    result.left = pt.x
                }
                if (pt.x > result.right) {
                    result.right = pt.x
                }
                if (pt.y < result.top) {
                    result.top = pt.y
                }
                if (pt.y > result.bottom) {
                    result.bottom = pt.y
                }
            }
            return result
        }
    }
}
