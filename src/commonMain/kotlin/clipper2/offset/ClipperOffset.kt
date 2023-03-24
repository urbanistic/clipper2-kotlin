package clipper2.offset

import Clipper
import Clipper.area
import Clipper.reversePath
import Clipper.sqr
import Clipper.stripDuplicates
import clipper2.core.ClipType
import clipper2.core.FillRule
import clipper2.core.InternalClipper.DEFAULT_ARC_TOLERANCE
import clipper2.core.InternalClipper.crossProduct
import clipper2.core.InternalClipper.dotProduct
import clipper2.core.InternalClipper.isAlmostZero
import clipper2.core.Path64
import clipper2.core.PathD
import clipper2.core.Paths64
import clipper2.core.Point64
import clipper2.core.PointD
import clipper2.core.Rect64
import clipper2.engine.Clipper64
import kotlin.js.JsExport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import tangible.OutObject
import tangible.RefObject


/**
 * Geometric offsetting refers to the process of creating parallel curves that
 * are offset a specified distance from their primary curves.
 *
 *
 * The ClipperOffset class manages the process of offsetting
 * (inflating/deflating) both open and closed paths using a number of different
 * join types and end types. The library user will rarely need to access this
 * unit directly since it will generally be easier to use the
 * [ InflatePaths()][Clipper.InflatePaths] function when doing polygon offsetting.
 *
 *
 * Caution: Offsetting self-intersecting polygons may produce unexpected
 * results.
 *
 *
 * Note: When inflating polygons, it's important that you select
 * [EndType.Polygon]. If you select one of the open path end types instead
 * (including EndType.Join), you'll simply inflate the polygon's outline.
 */
@JsExport
class ClipperOffset constructor(
    miterLimit: Double = 2.0,
    arcTolerance: Double = 0.25,
    preserveCollinear: Boolean = false,
    reverseSolution: Boolean = false
) {
    private val _groupList: MutableList<Group> = mutableListOf<Group>()
    private val normals = PathD()
    private val _solution = Paths64()
    private var group_delta = 0.0 // *0.5 for open paths; *-1.0 for negative areas
    private var delta = 0.0
    private var abs_group_delta = 0.0
    private var mitLimSqr = 0.0
    private var stepsPerRad = 0.0
    private var _stepSin = 0.0
    private var _stepCos = 0.0
    private var joinType: JoinType? = null
    private var endType: EndType? = null
    var arcTolerance = 0.0
    var mergeGroups = false
    var miterLimit = 0.0
    var preserveCollinear = false
    var reverseSolution = false
    /**
     * Creates a ClipperOffset object, using the supplied parameters.
     *
     * @param miterLimit        This property sets the maximum distance in multiples
     * of group_delta that vertices can be offset from
     * their original positions before squaring is applied.
     * (Squaring truncates a miter by 'cutting it off' at 1
     * × group_delta distance from the original vertex.)
     *
     *
     * The default value for `miterLimit` is 2
     * (i.e. twice group_delta). This is also the smallest
     * MiterLimit that's allowed. If mitering was
     * unrestricted (ie without any squaring), then offsets
     * at very acute angles would generate unacceptably
     * long 'spikes'.
     * @param arcTolerance      Since flattened paths can never perfectly represent
     * arcs (see Trigonometry), this property specifies a
     * maximum acceptable imperfection for rounded curves
     * during offsetting.
     *
     *
     * It's important to make arcTolerance a sensible
     * fraction of the offset group_delta (arc radius).
     * Large tolerances relative to the offset group_delta
     * will produce poor arc approximations but, just as
     * importantly, very small tolerances will slow
     * offsetting performance without noticeably improving
     * curve quality.
     *
     *
     * arcTolerance is only relevant when offsetting with
     * [JoinType.Round] and / or
     * [EndType.Round] (see
     * {[                          AddPath()][.AddPath] and
     * [                          AddPaths()][.AddPaths]. The default arcTolerance is 0.25.
     * @param preserveCollinear When adjacent edges are collinear in closed path
     * solutions, the common vertex can safely be removed
     * to simplify the solution without altering path
     * shape. However, because some users prefer to retain
     * these common vertices, this feature is optional.
     * Nevertheless, when adjacent edges in solutions are
     * collinear and also create a 'spike' by overlapping,
     * the vertex creating the spike will be removed
     * irrespective of the PreserveCollinear setting. This
     * property is false by default.
     * @param reverseSolution   reverses the solution's orientation
     */
    /**
     * Creates a ClipperOffset object, using default parameters.
     *
     * @see .ClipperOffset
     */
    /**
     * @see .ClipperOffset
     */
    /**
     * @see .ClipperOffset
     */
    /**
     * @see .ClipperOffset
     */
    init {
        this.miterLimit = miterLimit
        this.arcTolerance = arcTolerance
        mergeGroups = true
        this.preserveCollinear = preserveCollinear
        this.reverseSolution = reverseSolution
    }

    fun clear() {
        _groupList.clear()
    }

    fun addPath(path: Path64, joinType: JoinType?, endType: EndType?) {
        val cnt = path.size
        if (cnt == 0) {
            return
        }
        val pp = Paths64.of(path) //Arrays.asList(path))
        addPaths(pp, joinType, endType)
    }

    fun addPaths(paths: Paths64, joinType: JoinType?, endType: EndType?) {
        val cnt = paths.size
        if (cnt == 0) {
            return
        }
        _groupList.add(Group(paths, joinType!!, endType!!))
    }

    fun executeInternal(delta: Double) {
        _solution.clear()
        if (_groupList.isEmpty()) {
            return
        }

        if (abs(delta) < 0.5) {
            for (group in _groupList) {
                for (path in group.inPaths) {
                    _solution.add(path)
                }
            }
        } else {
            this.delta = delta
            mitLimSqr = if (miterLimit <= 1) 2.0 else 2.0 / sqr(miterLimit)
            for (group in _groupList) {
                doGroupOffset(group)
            }
        }
    }

    fun execute(delta: Double, solution: Paths64): Paths64 {
        solution.clear()
        executeInternal(delta)

        // clean up self-intersections ...
        val c = Clipper64()
        c.preserveCollinear = preserveCollinear
        c.reverseSolution = reverseSolution != _groupList[0].pathsReversed

        c.addSubjects(_solution)
        if (_groupList[0].pathsReversed) {
            c.execute(ClipType.Union, FillRule.Negative, _solution)
        } else {
            c.execute(ClipType.Union, FillRule.Positive, _solution)
        }
        return _solution
    }

    private fun getPerpendic(pt: Point64, norm: PointD): Point64 {
        return Point64(pt.x + norm.x * group_delta, pt.y + norm.y * group_delta)
    }

    private fun getPerpendicD(pt: Point64, norm: PointD): PointD {
        return PointD(pt.x + norm.x * group_delta, pt.y + norm.y * group_delta)
    }

    private fun doSquare(group: Group, path: Path64, j: Int, k: Int) {
        val vec: PointD
        vec = if (j == k) {
            PointD(normals[0].y, -normals[0].x)
        } else {
            getAvgUnitVector(PointD(-normals[k].y, normals[k].x), PointD(normals[j].y, -normals[j].x))
        }

        // now offset the original vertex delta units along unit vector
        var ptQ = PointD(path[j])
        ptQ = translatePoint(ptQ, abs_group_delta * vec.x, abs_group_delta * vec.y)

        // get perpendicular vertices
        val pt1 = translatePoint(ptQ, group_delta * vec.y, group_delta * -vec.x)
        val pt2 = translatePoint(ptQ, group_delta * -vec.y, group_delta * vec.x)
        // get 2 vertices along one edge offset
        val pt3 = getPerpendicD(path[k], normals[k])
        if (j == k) {
            val pt4 = PointD(pt3.x + vec.x * group_delta, pt3.y + vec.y * group_delta)
            val pt = intersectPoint(pt1, pt2, pt3, pt4)
            // get the second intersect point through reflecion
            group.outPath.add(Point64(reflectPoint(pt, ptQ)))
            group.outPath.add(Point64(pt))
        } else {
            val pt4 = getPerpendicD(path[j], normals[k])
            val pt = intersectPoint(pt1, pt2, pt3, pt4)
            group.outPath.add(Point64(pt))
            // get the second intersect point through reflecion
            group.outPath.add(Point64(reflectPoint(pt, ptQ)))
        }
    }

    private fun doMiter(group: Group, path: Path64, j: Int, k: Int, cosA: Double) {
        val q = group_delta / (cosA + 1)
        group.outPath.add(
            Point64(
                path[j].x + (normals[k].x + normals[j].x) * q,
                path[j].y + (normals[k].y + normals[j].y) * q
            )
        )
    }

    private fun doRound(group: Group, path: Path64, j: Int, k: Int, angle: Double) {
        val pt = path[j]
        var offsetVec = PointD(normals[k].x * group_delta, normals[k].y * group_delta)
        if (j == k) {
            offsetVec.negate()
        }
        group.outPath.add(Point64(pt.x + offsetVec.x, pt.y + offsetVec.y))
        if (angle > -PI + 0.01) // avoid 180deg concave
        {
            //val steps: Int = max(2, ceil(stepsPerRad * abs(angle)).toInt())
            val steps = ceil(stepsPerRad * abs(angle)).toInt()
//            val stepSin: Double = sin(angle / steps)
//            val stepCos: Double = cos(angle / steps)
            for (i in 1 until steps)  // ie 1 less than steps
            {
                offsetVec =
                    PointD(offsetVec.x * _stepCos - _stepSin * offsetVec.y, offsetVec.x * _stepSin + offsetVec.y * _stepCos)
                group.outPath.add(Point64(pt.x + offsetVec.x, pt.y + offsetVec.y))
            }
        }
        group.outPath.add(getPerpendic(pt, normals[j]))
    }

    private fun buildNormals(path: Path64) {
        val cnt = path.size
        normals.clear()
        for (i in 0 until cnt - 1) {
            normals.add(getUnitNormal(path[i], path[i + 1]))
        }
        normals.add(getUnitNormal(path[cnt - 1], path[0]))
    }

    private fun offsetPoint(group: Group, path: Path64, j: Int, k: RefObject<Int>) {
        // Let A = change in angle where edges join
        // A == 0: ie no change in angle (flat join)
        // A == PI: edges 'spike'
        // sin(A) < 0: right turning
        // cos(A) < 0: change in angle is more than 90 degree
        var sinA = crossProduct(normals[j], normals[k.argValue!!])
        val cosA = dotProduct(normals[j], normals[k.argValue!!])
        if (sinA > 1.0) {
            sinA = 1.0
        } else if (sinA < -1.0) {
            sinA = -1.0
        }
        if (almostZero(cosA - 1, 0.01)) // almost straight
        {
            group.outPath.add(getPerpendic(path[j], normals[k.argValue!!]))
            group.outPath.add(getPerpendic(path[j], normals[j])) // (#418)
        } else if (!almostZero(cosA + 1, 0.01) && sinA * group_delta < 0) // is concave
        {
            group.outPath.add(getPerpendic(path[j], normals[k.argValue!!]))
            // this extra point is the only (simple) way to ensure that
            // path reversals are fully cleaned with the trailing clipper
            group.outPath.add(path[j]) // (#405)
            group.outPath.add(getPerpendic(path[j], normals[j]))
        } else if (joinType === JoinType.Round) {
            doRound(group, path, j, k.argValue!!, atan2(sinA, cosA))
        } else if (joinType === JoinType.Miter) {
            // miter unless the angle is so acute the miter would exceeds ML
            if (cosA > mitLimSqr - 1) {
                doMiter(group, path, j, k.argValue!!, cosA)
            } else {
                doSquare(group, path, j, k.argValue!!)
            }
        } else if (cosA > 0.9) {
            doMiter(group, path, j, k.argValue!!, cosA)
        } else {
            doSquare(group, path, j, k.argValue!!)
        }
        k.argValue = j
    }

    private fun offsetPolygon(group: Group, path: Path64) {
        group.outPath = Path64()
        val cnt = path.size
        val prev = RefObject(cnt - 1)
        for (i in 0 until cnt) {
            offsetPoint(group, path, i, prev)
        }
        group.outPaths.add(group.outPath)
    }

    private fun offsetOpenJoined(group: Group, path: Path64) {
        var path = path
        offsetPolygon(group, path)
        path = reversePath(path)
        buildNormals(path)
        offsetPolygon(group, path)
    }

    private fun offsetOpenPath(group: Group, path: Path64) {
        group.outPath = Path64()
        val highI = path.size - 1
        when (endType) {
            EndType.Butt -> {
                group.outPath.add(
                    Point64(
                        path[0].x - normals[0].x * group_delta,
                        path[0].y - normals[0].y * group_delta
                    )
                )
                group.outPath.add(getPerpendic(path[0], normals[0]))
            }

            EndType.Round -> doRound(group, path, 0, 0, PI)
            else -> doSquare(group, path, 0, 0)
        }

        // offset the left side going forward
        val k = RefObject(0)
        for (i in 1 until highI) {
            offsetPoint(group, path, i, k)
        }

        // reverse normals ...
        for (i in highI downTo 1) {
            normals[i] = PointD(-normals[i - 1].x, -normals[i - 1].y)
        }
        normals[0] = normals[highI]
        when (endType) {
            EndType.Butt -> {
                group.outPath.add(
                    Point64(
                        path[highI].x - normals[highI].x * group_delta,
                        path[highI].y - normals[highI].y * group_delta
                    )
                )
                group.outPath.add(getPerpendic(path[highI], normals[highI]))
            }

            EndType.Round -> doRound(group, path, highI, highI, PI)
            else -> doSquare(group, path, highI, highI)
        }

        // offset the left side going back
        k.argValue = 0
        for (i in highI downTo 1) {
            offsetPoint(group, path, i, k)
        }
        group.outPaths.add(group.outPath)
    }

    private fun doGroupOffset(group: Group) {
        if (group.endType === EndType.Polygon) {
            // the lowermost polygon must be an outer polygon. So we can use that as the
            // designated orientation for outer polygons (needed for tidy-up clipping)
            val lowestIdx = OutObject<Int>(null)
            val grpBounds = OutObject<Rect64>(null)
            getBoundsAndLowestPolyIdx(group.inPaths, lowestIdx, grpBounds)
            if (lowestIdx.argValue!! < 0) {
                return
            }
            val area = area(group.inPaths[lowestIdx.argValue!!])
//            if (area == 0.0) { // this is probably unhelpful (#430)
//                return
//            }
            group.pathsReversed = area < 0
            if (group.pathsReversed) {
                group_delta = -delta
            } else {
                group_delta = delta
            }
        } else {
            group.pathsReversed = false
            group_delta = abs(delta) * 0.5
        }
        abs_group_delta = abs(group_delta)
        joinType = group.joinType
        endType = group.endType

        // calculate a sensible number of steps (for 360 deg for the given offset
        if (group.joinType === JoinType.Round || group.endType === EndType.Round) {
            // arcTol - when fArcTolerance is undefined (0), the amount of
            // curve imprecision that's allowed is based on the size of the
            // offset (delta). Obviously very large offsets will almost always
            // require much less precision. See also offset_triginometry2.svg
            val arcTol = if (arcTolerance > 0.01) arcTolerance else log10(2 + abs_group_delta) * DEFAULT_ARC_TOLERANCE
            //stepsPerRad = 0.5 / acos(1 - arcTol / abs_group_delta)
            val stepsPer360: Double = PI / acos(1 - arcTol / abs_group_delta)
            _stepSin = sin(2 * PI / stepsPer360)
            _stepCos = cos(2 * PI / stepsPer360)
            if (group_delta < 0.0) _stepSin = -_stepSin
            stepsPerRad = stepsPer360 / (2 * PI)
        }
        val isJoined = group.endType === EndType.Joined || group.endType === EndType.Polygon
        for (p in group.inPaths) {
            val path = stripDuplicates(p, isJoined)
            val cnt = path.size
            if (cnt == 0 || cnt < 3 && endType === EndType.Polygon) {
                continue
            }
            if (cnt == 1) {
                group.outPath = Path64()
                // single vertex so build a circle or square ...
                if (group.endType === EndType.Round) {
                    val r = abs_group_delta
                    group.outPath = Clipper.ellipse(path[0], r, r)
                } else {
                    val d: Int = ceil(group_delta).toInt()
                    val r = Rect64(path[0].x - d, path[0].y - d, path[0].x - d, path[0].y - d)
                    group.outPath = r.asPath()
                }
                group.outPaths.add(group.outPath)
            } else {
                if (cnt == 2 && group.endType === EndType.Joined) {
                    if (group.joinType === JoinType.Round) {
                        endType = EndType.Round
                    } else {
                        endType = EndType.Square
                    }
                }
                buildNormals(path)
                if (endType === EndType.Polygon) {
                    offsetPolygon(group, path)
                } else if (endType === EndType.Joined) {
                    offsetOpenJoined(group, path)
                } else {
                    offsetOpenPath(group, path)
                }
            }
        }
        _solution.addAll(group.outPaths)
        group.outPaths.clear()
    }

    companion object {
        private fun getUnitNormal(pt1: Point64, pt2: Point64): PointD {
            var dx = (pt2.x - pt1.x).toDouble()
            var dy = (pt2.y - pt1.y).toDouble()
            if (dx == 0.0 && dy == 0.0) {
                return PointD()
            }
            val f: Double = 1.0 / sqrt(dx * dx + dy * dy)
            dx *= f
            dy *= f
            return PointD(dy, -dx)
        }

        private fun getBoundsAndLowestPolyIdx(paths: Paths64, index: OutObject<Int>, recRef: OutObject<Rect64>) {
            val rec = Rect64(false) // ie invalid rect
            recRef.argValue = rec
            var lpX = Long.MIN_VALUE
            index.argValue = -1
            for (i in paths.indices) {
                for (pt in paths[i]) {
                    if (pt.y >= rec.bottom) {
                        if (pt.y > rec.bottom || pt.x < lpX) {
                            index.argValue = i
                            lpX = pt.x
                            rec.bottom = pt.y
                        }
                    } else if (pt.y < rec.top) {
                        rec.top = pt.y
                    }
                    if (pt.x > rec.right) {
                        rec.right = pt.x
                    } else if (pt.x < rec.left) {
                        rec.left = pt.y
                    }
                }
            }
        }

        private fun translatePoint(pt: PointD, dx: Double, dy: Double): PointD {
            return PointD(pt.x + dx, pt.y + dy)
        }

        private fun reflectPoint(pt: PointD, pivot: PointD): PointD {
            return PointD(pivot.x + (pivot.x - pt.x), pivot.y + (pivot.y - pt.y))
        }

        private fun almostZero(value: Double, epsilon: Double = 0.001): Boolean {
            return abs(value) < epsilon
        }

        private fun hypotenuse(x: Double, y: Double): Double {
            return sqrt(x * x + y * y)
        }

        private fun normalizeVector(vec: PointD): PointD {
            val h = hypotenuse(vec.x, vec.y)
            if (almostZero(h)) {
                return PointD(0, 0)
            }
            val inverseHypot = 1 / h
            return PointD(vec.x * inverseHypot, vec.y * inverseHypot)
        }

        private fun getAvgUnitVector(vec1: PointD, vec2: PointD): PointD {
            return normalizeVector(PointD(vec1.x + vec2.x, vec1.y + vec2.y))
        }

        private fun intersectPoint(pt1a: PointD, pt1b: PointD, pt2a: PointD, pt2b: PointD): PointD {
            if (isAlmostZero(pt1a.x - pt1b.x)) { // vertical
                if (isAlmostZero(pt2a.x - pt2b.x)) {
                    return PointD(0, 0)
                }
                val m2 = (pt2b.y - pt2a.y) / (pt2b.x - pt2a.x)
                val b2 = pt2a.y - m2 * pt2a.x
                return PointD(pt1a.x, m2 * pt1a.x + b2)
            }
            return if (isAlmostZero(pt2a.x - pt2b.x)) { // vertical
                val m1 = (pt1b.y - pt1a.y) / (pt1b.x - pt1a.x)
                val b1 = pt1a.y - m1 * pt1a.x
                PointD(pt2a.x, m1 * pt2a.x + b1)
            } else {
                val m1 = (pt1b.y - pt1a.y) / (pt1b.x - pt1a.x)
                val b1 = pt1a.y - m1 * pt1a.x
                val m2 = (pt2b.y - pt2a.y) / (pt2b.x - pt2a.x)
                val b2 = pt2a.y - m2 * pt2a.x
                if (isAlmostZero(m1 - m2)) {
                    return PointD(0, 0)
                }
                val x = (b2 - b1) / (m1 - m2)
                PointD(x, m1 * x + b1)
            }
        }
    }
}