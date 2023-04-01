@file:Suppress("unused")

import clipper2.Minkowski
import clipper2.RectClip.RectClip
import clipper2.RectClip.RectClipLines
import clipper2.core.ClipType
import clipper2.core.FillRule
import clipper2.core.InternalClipper
import clipper2.core.Path64
import clipper2.core.PathD
import clipper2.core.PathType
import clipper2.core.Paths64
import clipper2.core.PathsD
import clipper2.core.Point64
import clipper2.core.PointD
import clipper2.core.Rect64
import clipper2.core.RectD
import clipper2.engine.Clipper64
import clipper2.engine.ClipperD
import clipper2.engine.PointInPolygonResult
import clipper2.engine.PolyPath64
import clipper2.engine.PolyPathD
import clipper2.engine.PolyTree64
import clipper2.engine.PolyTreeD
import clipper2.offset.ClipperOffset
import clipper2.offset.EndType
import clipper2.offset.JoinType
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Clipper {
    val InvalidRect64 = Rect64(false)
    val InvalidRectD = RectD(false)

    fun intersect(subject: Paths64, clip: Paths64, fillRule: FillRule): Paths64 {
        return booleanOp(ClipType.Intersection, subject, clip, fillRule)
    }

    fun intersect(subject: PathsD, clip: PathsD, fillRule: FillRule, precision: Int = 2): PathsD {
        return booleanOp(ClipType.Intersection, subject, clip, fillRule, precision)
    }

    fun union(subject: Paths64, fillRule: FillRule): Paths64 {
        return booleanOp(ClipType.Union, subject, null, fillRule)
    }

    fun union(subject: Paths64, clip: Paths64? = null, fillRule: FillRule): Paths64 {
        return booleanOp(ClipType.Union, subject, clip, fillRule)
    }

    fun union(subject: PathsD, fillRule: FillRule): PathsD {
        return booleanOp(ClipType.Union, subject, null, fillRule)
    }

    fun union(subject: PathsD, clip: PathsD? = null, fillRule: FillRule, precision: Int = 2): PathsD {
        return booleanOp(ClipType.Union, subject, clip, fillRule, precision)
    }

    fun difference(subject: Paths64, clip: Paths64, fillRule: FillRule): Paths64 {
        return booleanOp(ClipType.Difference, subject, clip, fillRule)
    }

    fun difference(subject: PathsD, clip: PathsD, fillRule: FillRule, precision: Int = 2): PathsD {
        return booleanOp(ClipType.Difference, subject, clip, fillRule, precision)
    }

    fun xor(subject: Paths64, clip: Paths64, fillRule: FillRule): Paths64 {
        return booleanOp(ClipType.Xor, subject, clip, fillRule)
    }

    fun xor(subject: PathsD, clip: PathsD, fillRule: FillRule, precision: Int = 2): PathsD {
        return booleanOp(ClipType.Xor, subject, clip, fillRule, precision)
    }

    fun booleanOp(
        clipType: ClipType,
        subject: Paths64?,
        clip: Paths64?,
        fillRule: FillRule
    ): Paths64 {
        val solution = Paths64()
        if (subject == null) {
            return solution
        }
        val c = Clipper64()
        c.addPaths(subject, PathType.Subject)
        if (clip != null) {
            c.addPaths(clip, PathType.Clip)
        }
        c.execute(clipType, fillRule, solution)
        return solution
    }

    fun booleanOp(
        clipType: ClipType,
        subject: Paths64?,
        clip: Paths64?,
        polytree: PolyTree64,
        fillRule: FillRule
    ) {
        if (subject == null) {
            return
        }
        val c = Clipper64()
        c.addPaths(subject, PathType.Subject)
        if (clip != null) {
            c.addPaths(clip, PathType.Clip)
        }
        c.execute(clipType, fillRule, polytree)
    }

    fun booleanOp(
        clipType: ClipType,
        subject: PathsD,
        clip: PathsD?,
        fillRule: FillRule,
        precision: Int = 2
    ): PathsD {
        val solution = PathsD()
        val c = ClipperD(precision)
        c.addSubjects(subject)
        if (clip != null) {
            c.addClips(clip)
        }
        c.execute(clipType, fillRule, solution)
        return solution
    }

    fun booleanOp(
        clipType: ClipType,
        subject: PathsD?,
        clip: PathsD?,
        polytree: PolyTreeD,
        fillRule: FillRule
    ) {
        booleanOp(clipType, subject, clip, polytree, fillRule, 2)
    }

    fun booleanOp(
        clipType: ClipType,
        subject: PathsD?,
        clip: PathsD?,
        polytree: PolyTreeD,
        fillRule: FillRule,
        precision: Int
    ) {
        if (subject == null) {
            return
        }
        val c = ClipperD(precision)
        c.addPaths(subject, PathType.Subject)
        if (clip != null) {
            c.addPaths(clip, PathType.Clip)
        }
        c.execute(clipType, fillRule, polytree)
    }

    fun inflatePaths(
        paths: Paths64,
        delta: Double,
        joinType: JoinType,
        endType: EndType
    ): Paths64 {
        return inflatePaths(paths, delta, joinType, endType, 2.0)
    }

    /**
     * These functions encapsulate [ClipperOffset], the class that performs
     * both polygon and open path offsetting.
     *
     *
     * When using this function to inflate polygons (ie closed paths), it's
     * important that you select [EndType.Polygon]. If instead you select one
     * of the open path end types (including [EndType.Joined]), you'll inflate
     * the polygon's outline.
     *
     *
     * With closed paths (polygons), a positive delta specifies how much outer
     * polygon contours will expand and how much inner "hole" contours will contract
     * (and the converse with negative deltas).
     *
     *
     * With open paths (polylines), including [EndType.Joined], delta
     * specifies the width of the inflated line.
     *
     *
     * Caution: Offsetting self-intersecting polygons may produce unexpected
     * results.
     *
     * @param paths
     * @param delta      With closed paths (polygons), a positive `delta`
     * specifies how much outer polygon contours will expand and
     * how much inner "hole" contours will contract (and the
     * converse with negative deltas).
     *
     *
     * With open paths (polylines), including EndType.Join,
     * `delta` specifies the width of the inflated
     * line.
     * @param joinType
     * @param endType
     * @param miterLimit sets the maximum distance in multiples of delta that
     * vertices can be offset from their original positions before
     * squaring is applied. (Squaring truncates a miter by
     * 'cutting it off' at 1 × delta distance from the original
     * vertex.)
     *
     *
     * The default value for MiterLimit is 2 (ie twice delta).
     * This is also the smallest MiterLimit that's allowed. If
     * mitering was unrestricted (ie without any squaring), then
     * offsets at very acute angles would generate unacceptably
     * long 'spikes'.
     * @return
     */
    fun inflatePaths(
        paths: Paths64,
        delta: Double,
        joinType: JoinType,
        endType: EndType,
        miterLimit: Double
    ): Paths64 {
        val co = ClipperOffset(miterLimit)
        co.addPaths(paths, joinType, endType)
        val solution = Paths64()
        co.execute(delta, solution)
        return solution
    }

    fun inflatePaths(
        paths: PathsD,
        delta: Double,
        joinType: JoinType,
        endType: EndType
    ): PathsD {
        return inflatePaths(paths, delta, joinType, endType, 2.0, 2)
    }

    fun inflatePaths(
        paths: PathsD,
        delta: Double,
        joinType: JoinType,
        endType: EndType,
        miterLimit: Double
    ): PathsD {
        return inflatePaths(paths, delta, joinType, endType, miterLimit, 2)
    }

    fun inflatePaths(
        paths: PathsD,
        delta: Double,
        joinType: JoinType,
        endType: EndType,
        miterLimit: Double,
        precision: Int
    ): PathsD {
        InternalClipper.checkPrecision(precision)
        val scale: Double = 10.0.pow(precision.toDouble())
        val tmp = scalePaths64(paths, scale)
        val co = ClipperOffset(miterLimit)
        co.addPaths(tmp, joinType, endType)
        co.execute(delta * scale, tmp) // reuse 'tmp' to receive (scaled) solution
        return scalePathsD(tmp, 1 / scale)
    }

    fun executeRectClip(
        rect: Rect64,
        paths: Paths64,
        convexOnly: Boolean = false
    ): Paths64 {
        if (rect.isEmpty() || paths.size == 0) {
            return Paths64()
        }
        val rc = RectClip(rect)
        return rc.execute(paths, convexOnly)
    }

    fun executeRectClip(
        rect: Rect64,
        path: Path64,
        convexOnly: Boolean = false
    ): Paths64 {
        if (rect.isEmpty() || path.size == 0) {
            return Paths64()
        }
        val tmp = Paths64()
        tmp.add(path)
        return executeRectClip(rect, tmp, convexOnly)
    }

    fun executeRectClip(
        rect: RectD,
        paths: PathsD,
        precision: Int = 2,
        convexOnly: Boolean = false
    ): PathsD {
        InternalClipper.checkPrecision(precision)
        if (rect.isEmpty() || paths.size == 0) {
            return PathsD()
        }
        val scale: Double = 10.0.pow(precision.toDouble())
        val r = scaleRect(rect, scale)
        var tmpPath = scalePaths64(paths, scale)
        val rc = RectClip(r)
        tmpPath = rc.execute(tmpPath, convexOnly)
        return scalePathsD(tmpPath, 1 / scale)
    }

    fun executeRectClip(
        rect: RectD,
        path: PathD,
        precision: Int = 2,
        convexOnly: Boolean = false
    ): PathsD {
        if (rect.isEmpty() || path.size == 0) {
            return PathsD()
        }
        val tmp = PathsD()
        tmp.add(path)
        return executeRectClip(rect, tmp, precision, convexOnly)
    }

    fun executeRectClipLines(
        rect: Rect64,
        paths: Paths64
    ): Paths64 {
        if (rect.isEmpty() || paths.size == 0) {
            return Paths64()
        }
        val rc = RectClipLines(rect)
        return rc.execute(paths)
    }

    fun executeRectClipLines(
        rect: Rect64,
        path: Path64
    ): Paths64 {
        if (rect.isEmpty() || path.size == 0) {
            return Paths64()
        }
        val tmp = Paths64()
        tmp.add(path)
        return executeRectClipLines(rect, tmp)
    }

    fun executeRectClipLines(
        rect: RectD,
        paths: PathsD,
        precision: Int = 2
    ): PathsD {
        InternalClipper.checkPrecision(precision)
        if (rect.isEmpty() || paths.size == 0) {
            return PathsD()
        }
        val scale: Double = 10.0.pow(precision.toDouble())
        val r = scaleRect(rect, scale)
        var tmpPath = scalePaths64(paths, scale)
        val rc = RectClipLines(r)
        tmpPath = rc.execute(tmpPath)
        return scalePathsD(tmpPath, 1 / scale)
    }

    fun executeRectClipLines(
        rect: RectD,
        path: PathD,
        precision: Int = 2
    ): PathsD {
        if (rect.isEmpty() || path.size == 0) {
            return PathsD()
        }
        val tmp = PathsD()
        tmp.add(path)
        return executeRectClipLines(rect, tmp, precision)
    }

    fun minkowskiSum(pattern: Path64?, path: Path64?, isClosed: Boolean): Paths64 {
        return Minkowski.sum(pattern!!, path!!, isClosed)
    }

    fun minkowskiSum(pattern: PathD, path: PathD, isClosed: Boolean): PathsD {
        return Minkowski.sum(pattern, path, isClosed)
    }

    fun minkowskiDiff(pattern: Path64, path: Path64, isClosed: Boolean): Paths64 {
        return Minkowski.diff(pattern, path, isClosed)
    }

    fun minkowskiDiff(pattern: PathD, path: PathD, isClosed: Boolean): PathsD {
        return Minkowski.diff(pattern, path, isClosed)
    }

    /**
     * Returns the area of the supplied polygon. It's assumed that the path is
     * closed and does not self-intersect. Depending on the path's winding
     * orientation, this value may be positive or negative. If the winding is
     * clockwise, then the area will be positive and conversely, if winding is
     * counter-clockwise, then the area will be negative.
     *
     * @param path
     * @return
     */
    fun area(path: Path64): Double {
        // https://en.wikipedia.org/wiki/Shoelace_formula
        var a = 0.0
        val cnt = path.size
        if (cnt < 3) {
            return 0.0
        }
        var prevPt = path[cnt - 1]
        for (pt in path) {
            a += (prevPt.y + pt.y).toDouble() * (prevPt.x - pt.x)
            prevPt = pt
        }
        return a * 0.5
    }

    /**
     * Returns the area of the supplied polygon. It's assumed that the path is
     * closed and does not self-intersect. Depending on the path's winding
     * orientation, this value may be positive or negative. If the winding is
     * clockwise, then the area will be positive and conversely, if winding is
     * counter-clockwise, then the area will be negative.
     *
     * @param paths
     * @return
     */
    fun area(paths: Paths64): Double {
        var a = 0.0
        for (path in paths) {
            a += area(path)
        }
        return a
    }

    fun area(path: PathD): Double {
        var a = 0.0
        val cnt = path.size
        if (cnt < 3) {
            return 0.0
        }
        var prevPt = path[cnt - 1]
        for (pt in path) {
            a += (prevPt.y + pt.y) * (prevPt.x - pt.x)
            prevPt = pt
        }
        return a * 0.5
    }

    fun area(paths: PathsD): Double {
        var a = 0.0
        for (path in paths) {
            a += area(path)
        }
        return a
    }

    /**
     * This function assesses the winding orientation of closed paths.
     *
     *
     * Positive winding paths will be oriented in an anti-clockwise direction in
     * Cartesian coordinates (where coordinate values increase when heading
     * rightward and upward). Nevertheless, it's common for graphics libraries to use
     * inverted Y-axes (where Y values decrease heading upward). In these libraries,
     * paths with Positive winding will be oriented clockwise.
     *
     *
     * Note: Self-intersecting polygons have indeterminate orientation since some
     * path segments will commonly wind in opposite directions to other segments.
     */
    fun isPositive(poly: Path64): Boolean {
        return area(poly) >= 0
    }

    /**
     * This function assesses the winding orientation of closed paths.
     *
     *
     * Positive winding paths will be oriented in an anti-clockwise direction in
     * Cartesian coordinates (where coordinate values increase when heading
     * rightward and upward). Nevertheless, it's common for graphics libraries to use
     * inverted Y-axes (where Y values decrease heading upward). In these libraries,
     * paths with Positive winding will be oriented clockwise.
     *
     *
     * Note: Self-intersecting polygons have indeterminate orientation since some
     * path segments will commonly wind in opposite directions to other segments.
     */
    fun isPositive(poly: PathD): Boolean {
        return area(poly) >= 0
    }

    fun path64ToString(path: Path64): String {
        return path.toString() + "\n"
    }

    fun paths64ToString(paths: Paths64): String {
        return paths.toString()
    }

    fun pathDToString(path: PathD): String {
        return path.toString() + "\n"
    }

    fun pathsDToString(paths: PathsD): String {
        return paths.toString()
    }

    fun offsetPath(
        path: Path64,
        dx: Long,
        dy: Long
    ): Path64 {
        val result = Path64()
        for (pt in path) {
            result.add(Point64(pt.x + dx, pt.y + dy))
        }
        return result
    }

    fun scalePoint64(
        pt: Point64,
        scale: Double
    ): Point64 {
        val result = Point64()
        result.x = (pt.x * scale).toLong()
        result.y = (pt.y * scale).toLong()
        return result
    }

    fun scalePointD(
        pt: Point64,
        scale: Double
    ): PointD {
        val result = PointD()
        result.x = pt.x * scale
        result.y = pt.y * scale
        return result
    }

    fun scaleRect(
        rec: RectD,
        scale: Double
    ): Rect64 {
        return Rect64(
            (rec.left * scale).toLong(),
            (rec.top * scale).toLong(),
            (rec.right * scale).toLong(),
            (rec.bottom * scale).toLong()
        )
    }

    private fun scalePath(
        path: Path64,
        scale: Double
    ): Path64 {
        if (InternalClipper.isAlmostZero(scale - 1)) {
            return path
        }
        val result = Path64()
        for (pt in path) {
            result.add(Point64(pt.x * scale, pt.y * scale))
        }
        return result
    }

    private fun scalePaths(paths: Paths64, scale: Double): Paths64 {
        if (InternalClipper.isAlmostZero(scale - 1)) {
            return paths
        }
        val result = Paths64()
        for (path in paths) {
            result.add(scalePath(path, scale))
        }
        return result
    }

    private fun scalePath(path: PathD, scale: Double): PathD {
        if (InternalClipper.isAlmostZero(scale - 1)) {
            return path
        }
        val result = PathD()
        for (pt in path) {
            result.add(PointD(pt, scale))
        }
        return result
    }

    private fun scalePaths(paths: PathsD, scale: Double): PathsD {
        if (InternalClipper.isAlmostZero(scale - 1)) {
            return paths
        }
        val result = PathsD()
        for (path in paths) {
            result.add(scalePath(path, scale))
        }
        return result
    }

    // Unlike ScalePath, both ScalePath64 & ScalePathD also involve type conversion
    fun scalePath64(path: PathD, scale: Double): Path64 {
        val res = Path64() //path.size
        for (pt in path) {
            res.add(Point64(pt, scale))
        }
        return res
    }

    fun scalePaths64(paths: PathsD, scale: Double): Paths64 {
        val res = Paths64() //paths.size
        for (path in paths) {
            res.add(scalePath64(path, scale))
        }
        return res
    }

    fun scalePathD(path: Path64, scale: Double): PathD {
        val res = PathD() //path.size
        for (pt in path) {
            res.add(PointD(pt, scale))
        }
        return res
    }

    fun scalePathsD(paths: Paths64, scale: Double): PathsD {
        val res = PathsD() //paths.size
        for (path in paths) {
            res.add(scalePathD(path, scale))
        }
        return res
    }

    fun translatePath(path: Path64, dx: Long, dy: Long): Path64 {
        val result = Path64() //path.size
        for (pt in path) {
            result.add(Point64(pt.x + dx, pt.y + dy))
        }
        return result
    }

    fun translatePaths(paths: Paths64, dx: Long, dy: Long): Paths64 {
        val result = Paths64() //paths.size
        for (path in paths) {
            result.add(offsetPath(path, dx, dy))
        }
        return result
    }

    fun translatePath(path: PathD, dx: Double, dy: Double): PathD {
        val result = PathD() //path.size
        for (pt in path) {
            result.add(PointD(pt.x + dx, pt.y + dy))
        }
        return result
    }

    fun translatePaths(paths: PathsD, dx: Double, dy: Double): PathsD {
        val result = PathsD() //paths.size
        for (path in paths) {
            result.add(translatePath(path, dx, dy))
        }
        return result
    }

    /**
     * returns a reversed copy of the Path
     */
    fun reversePath(path: Path64): Path64 {
        return path.reversed() as Path64
    }

    fun reversePath(path: PathD): PathD {
        return path.reversed() as PathD
    }

    fun reversePaths(paths: Paths64): Paths64 {
        val result = Paths64() //paths.size
        for (t in paths) {
            result.add(reversePath(t))
        }
        return result
    }

    fun reversePaths(paths: PathsD): PathsD {
        val result = PathsD() //paths.size
        for (path in paths) {
            result.add(reversePath(path))
        }
        return result
    }

    fun getBounds(path: Path64): Rect64 {
        val result = InvalidRect64
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
        return if (result.left == Long.MAX_VALUE) Rect64() else result
    }

    fun getBounds(paths: Paths64): Rect64 {
        val result = InvalidRect64
        for (path in paths) {
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
        }
        return if (result.left == Long.MAX_VALUE) Rect64() else result
    }

    fun getBounds(path: PathD): RectD {
        val result = InvalidRectD
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
        return if (result.left == Double.MAX_VALUE) RectD() else result
    }

    fun getBounds(paths: PathsD): RectD {
        val result = InvalidRectD
        for (path in paths) {
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
        }
        return if (result.left == Double.MAX_VALUE) RectD() else result
    }

    fun makePath(arr: IntArray): Path64 {
        val len = arr.size / 2
        val p = Path64() //len
        for (i in 0 until len) {
            p.add(Point64(arr[i * 2].toLong(), arr[i * 2 + 1].toLong()))
        }
        return p
    }

    fun makePath(arr: LongArray): Path64 {
        val len = arr.size / 2
        val p = Path64() //len
        for (i in 0 until len) {
            p.add(Point64(arr[i * 2], arr[i * 2 + 1]))
        }
        return p
    }

    fun makePath(arr: DoubleArray): PathD {
        val len = arr.size / 2
        val p = PathD() //len
        for (i in 0 until len) {
            p.add(PointD(arr[i * 2], arr[i * 2 + 1]))
        }
        return p
    }

    fun sqr(value: Double): Double {
        return value * value
    }

    fun pointsNearEqual(pt1: PointD, pt2: PointD, distanceSqrd: Double): Boolean {
        return sqr(pt1.x - pt2.x) + sqr(pt1.y - pt2.y) < distanceSqrd
    }

    fun stripNearDuplicates(path: PathD, minEdgeLenSqrd: Double, isClosedPath: Boolean): PathD {
        val cnt = path.size
        val result = PathD() //cnt
        if (cnt == 0) {
            return result
        }
        var lastPt = path[0]
        result.add(lastPt)
        for (i in 1 until cnt) {
            if (!pointsNearEqual(lastPt, path[i], minEdgeLenSqrd)) {
                lastPt = path[i]
                result.add(lastPt)
            }
        }
        if (isClosedPath && pointsNearEqual(lastPt, result[0], minEdgeLenSqrd)) {
            result.removeAt(result.size - 1)
        }
        return result
    }

    fun stripDuplicates(path: Path64, isClosedPath: Boolean): Path64 {
        val cnt = path.size
        val result = Path64() //cnt
        if (cnt == 0) {
            return result
        }
        var lastPt = path[0]
        result.add(lastPt)
        for (i in 1 until cnt) {
            if (!path[i].equals(lastPt)) {
                lastPt = path[i]
                result.add(lastPt)
            }
        }
        if (isClosedPath && result[0].equals(lastPt)) {
            result.removeAt(result.size - 1)
        }
        return result
    }

    private fun addPolyNodeToPaths(polyPath: PolyPath64, paths: Paths64) {
        if (!polyPath.polygon.isNullOrEmpty()) {
            paths.add(polyPath.polygon!!)
        }

        val iterator = polyPath.iterator()
        while (iterator.hasNext()) {
            addPolyNodeToPaths(iterator.next() as PolyPath64, paths)
        }
    }

    fun polyTreeToPaths64(polyTree: PolyTree64): Paths64 {
        val result = Paths64()

        val iterator = polyTree.iterator()
        while (iterator.hasNext()) {
            addPolyNodeToPaths(iterator.next() as PolyPath64, result)
        }

        return result
    }

    fun addPolyNodeToPathsD(polyPath: PolyPathD, paths: PathsD) {
        if (!polyPath.polygon.isNullOrEmpty()) {
            paths.add(polyPath.polygon!!)
        }

        val iterator = polyPath.iterator()
        while (iterator.hasNext()) {
            addPolyNodeToPathsD(iterator.next() as PolyPathD, paths)
        }
    }

    fun polyTreeToPathsD(polyTree: PolyTreeD): PathsD {
        val result = PathsD()
        for (polyPathBase in polyTree) {
            val p: PolyPathD = polyPathBase as PolyPathD
            addPolyNodeToPathsD(p, result)
        }
        return result
    }

    fun perpendicDistFromLineSqrd(pt: PointD, line1: PointD, line2: PointD): Double {
        val a = pt.x - line1.x
        val b = pt.y - line1.y
        val c = line2.x - line1.x
        val d = line2.y - line1.y
        return if (c == 0.0 && d == 0.0) {
            0.0
        } else sqr(a * d - c * b) / (c * c + d * d)
    }

    fun perpendicDistFromLineSqrd(pt: Point64, line1: Point64, line2: Point64): Double {
        val a = pt.x.toDouble() - line1.x
        val b = pt.y.toDouble() - line1.y
        val c = line2.x.toDouble() - line1.x
        val d = line2.y.toDouble() - line1.y
        return if (c == 0.0 && d == 0.0) {
            0.0
        } else sqr(a * d - c * b) / (c * c + d * d)
    }

    fun rdp(path: Path64, begin: Int, end: Int, epsSqrd: Double, flags: MutableList<Boolean>) {
        var end = end
        var idx = 0
        var maxD = 0.0
        while (end > begin && path[begin] == path[end]) {
            flags[end--] = false
        }
        for (i in begin + 1 until end) {
            // PerpendicDistFromLineSqrd - avoids expensive Sqrt()
            val d = perpendicDistFromLineSqrd(path[i], path[begin], path[end])
            if (d <= maxD) {
                continue
            }
            maxD = d
            idx = i
        }
        if (maxD <= epsSqrd) {
            return
        }
        flags[idx] = true
        if (idx > begin + 1) {
            rdp(path, begin, idx, epsSqrd, flags)
        }
        if (idx < end - 1) {
            rdp(path, idx, end, epsSqrd, flags)
        }
    }

    /**
     * The Ramer-Douglas-Peucker algorithm is very useful in removing path segments
     * that don't contribute meaningfully to the path's shape. The algorithm's
     * aggressiveness is determined by the epsilon parameter, with larger values
     * removing more vertices. (Somewhat simplistically, the algorithm removes
     * vertices that are less than epsilon distance from imaginary lines passing
     * through their adjacent vertices.)
     *
     *
     * This function can be particularly useful when offsetting paths (ie
     * inflating/shrinking) where the offsetting process often creates tiny
     * segments. These segments don't enhance curve quality, but they will slow path
     * processing (whether during file storage, or when rendering, or in subsequent
     * offsetting procedures).
     *
     * @param path
     * @param epsilon
     * @return
     */
    fun ramerDouglasPeuckerPath(path: Path64, epsilon: Double): Path64 {
        val len = path.size
        if (len < 5) {
            return path
        }
        val flags: MutableList<Boolean> = MutableList<Boolean>(len) {false}
        flags[0] = true
        flags[len - 1] = true
        rdp(path, 0, len - 1, sqr(epsilon), flags)
        val result = Path64() //len
        for (i in 0 until len) {
            if (flags[i]) {
                result.add(path[i])
            }
        }
        return result
    }

    /**
     * The Ramer-Douglas-Peucker algorithm is very useful in removing path segments
     * that don't contribute meaningfully to the path's shape. The algorithm's
     * aggressiveness is determined by the epsilon parameter, with larger values
     * removing more vertices. (Somewhat simplistically, the algorithm removes
     * vertices that are less than epsilon distance from imaginary lines passing
     * through their adjacent vertices.)
     *
     *
     * This function can be particularly useful when offsetting paths (ie
     * inflating/shrinking) where the offsetting process often creates tiny
     * segments. These segments don't enhance curve quality, but they will slow path
     * processing (whether during file storage, or when rendering, or in subsequent
     * offsetting procedures).
     *
     * @param paths
     * @param epsilon
     * @return
     */
    fun ramerDouglasPeucker(paths: Paths64, epsilon: Double): Paths64 {
        val result = Paths64() //paths.size
        for (path in paths) {
            result.add(ramerDouglasPeuckerPath(path, epsilon))
        }
        return result
    }

    fun rdp(path: PathD, begin: Int, end: Int, epsSqrd: Double, flags: MutableList<Boolean>) {
        var end = end
        var idx = 0
        var maxD = 0.0
        while (end > begin && path[begin] == path[end]) {
            flags[end--] = false
        }
        for (i in begin + 1 until end) {
            // PerpendicDistFromLineSqrd - avoids expensive Sqrt()
            val d = perpendicDistFromLineSqrd(path[i], path[begin], path[end])
            if (d <= maxD) {
                continue
            }
            maxD = d
            idx = i
        }
        if (maxD <= epsSqrd) {
            return
        }
        flags[idx] = true
        if (idx > begin + 1) {
            rdp(path, begin, idx, epsSqrd, flags)
        }
        if (idx < end - 1) {
            rdp(path, idx, end, epsSqrd, flags)
        }
    }

    fun ramerDouglasPeucker(path: PathD, epsilon: Double): PathD {
        val len = path.size
        if (len < 5) {
            return path
        }
        val flags: MutableList<Boolean> = MutableList<Boolean>(len) {false}
        flags[0] = true
        flags[len - 1] = true
        rdp(path, 0, len - 1, sqr(epsilon), flags)
        val result = PathD() //len
        for (i in 0 until len) {
            if (flags[i]) {
                result.add(path[i])
            }
        }
        return result
    }

    fun ramerDouglasPeucker(paths: PathsD, epsilon: Double): PathsD {
        val result = PathsD() //paths.size
        for (path in paths) {
            result.add(ramerDouglasPeucker(path, epsilon))
        }
        return result
    }

    private fun getNext(current: Int, high: Int, flags: BooleanArray): Int {
        var current = current
        ++current
        while (current <= high && flags[current]) {
            ++current
        }
        if (current <= high) {
            return current
        }
        current = 0
        while (flags[current]) {
            ++current
        }
        return current
    }

    private fun getPrior(current: Int, high: Int, flags: BooleanArray): Int {
        var current = current
        if (current == 0) {
            current = high
        } else {
            --current
        }
        while (current > 0 && flags[current]) {
            --current
        }
        if (!flags[current]) {
            return current
        }
        current = high
        while (flags[current]) {
            --current
        }
        return current
    }

    fun simplifyPath(path: Path64, epsilon: Double, isOpenPath: Boolean = false): Path64 {
        val len = path.size
        val high = len - 1
        val epsSqr = sqr(epsilon)
        if (len < 4) {
            return path
        }
        val flags = BooleanArray(len)
        val dsq = DoubleArray(len)
        var prev = high
        var curr = 0
        var start: Int
        var next: Int
        var prior2: Int
        var next2: Int
        if (isOpenPath) {
            dsq[0] = Double.MAX_VALUE
            dsq[high] = Double.MAX_VALUE
        } else {
            dsq[0] = perpendicDistFromLineSqrd(path[0], path[high], path[1])
            dsq[high] = perpendicDistFromLineSqrd(path[high], path[0], path[high - 1])
        }
        for (i in 1 until high) {
            dsq[i] = perpendicDistFromLineSqrd(path[i], path[i - 1], path[i + 1])
        }
        while (true) {
            if (dsq[curr] > epsSqr) {
                start = curr
                do {
                    curr = getNext(curr, high,  /* ref */flags)
                } while (curr != start && dsq[curr] > epsSqr)
                if (curr == start) {
                    break
                }
            }
            prev = getPrior(curr, high,  /* ref */flags)
            next = getNext(curr, high,  /* ref */flags)
            if (next == prev) {
                break
            }
            if (dsq[next] < dsq[curr]) {
                flags[next] = true
                next = getNext(next, high,  /* ref */flags)
                next2 = getNext(next, high,  /* ref */flags)
                dsq[curr] = perpendicDistFromLineSqrd(path[curr], path[prev], path[next])
                if (next != high || !isOpenPath) {
                    dsq[next] = perpendicDistFromLineSqrd(path[next], path[curr], path[next2])
                }
                curr = next
            } else {
                flags[curr] = true
                curr = next
                next = getNext(next, high,  /* ref */flags)
                prior2 = getPrior(prev, high,  /* ref */flags)
                dsq[curr] = perpendicDistFromLineSqrd(path[curr], path[prev], path[next])
                if (prev != 0 || !isOpenPath) {
                    dsq[prev] = perpendicDistFromLineSqrd(path[prev], path[prior2], path[curr])
                }
            }
        }
        val result = Path64() //len
        for (i in 0 until len) {
            if (!flags[i]) {
                result.add(path[i])
            }
        }
        return result
    }

    fun simplifyPaths(paths: Paths64, epsilon: Double, isOpenPath: Boolean = false): Paths64 {
        val result = Paths64() //paths.size
        for (path in paths) {
            result.add(simplifyPath(path, epsilon, isOpenPath))
        }
        return result
    }

    fun simplifyPath(path: PathD, epsilon: Double, isOpenPath: Boolean = false): PathD {
        val len = path.size
        val high = len - 1
        val epsSqr = sqr(epsilon)
        if (len < 4) {
            return path
        }
        val flags = BooleanArray(len)
        val dsq = DoubleArray(len)
        var prev = high
        var curr = 0
        var start: Int
        var next: Int
        var prior2: Int
        var next2: Int
        if (isOpenPath) {
            dsq[0] = Double.MAX_VALUE
            dsq[high] = Double.MAX_VALUE
        } else {
            dsq[0] = perpendicDistFromLineSqrd(path[0], path[high], path[1])
            dsq[high] = perpendicDistFromLineSqrd(path[high], path[0], path[high - 1])
        }
        for (i in 1 until high) {
            dsq[i] = perpendicDistFromLineSqrd(path[i], path[i - 1], path[i + 1])
        }
        while (true) {
            if (dsq[curr] > epsSqr) {
                start = curr
                do {
                    curr = getNext(curr, high,  /* ref */flags)
                } while (curr != start && dsq[curr] > epsSqr)
                if (curr == start) {
                    break
                }
            }
            prev = getPrior(curr, high,  /* ref */flags)
            next = getNext(curr, high,  /* ref */flags)
            if (next == prev) {
                break
            }
            if (dsq[next] < dsq[curr]) {
                flags[next] = true
                next = getNext(next, high,  /* ref */flags)
                next2 = getNext(next, high,  /* ref */flags)
                dsq[curr] = perpendicDistFromLineSqrd(path[curr], path[prev], path[next])
                if (next != high || !isOpenPath) {
                    dsq[next] = perpendicDistFromLineSqrd(path[next], path[curr], path[next2])
                }
                curr = next
            } else {
                flags[curr] = true
                curr = next
                next = getNext(next, high,  /* ref */flags)
                prior2 = getPrior(prev, high,  /* ref */flags)
                dsq[curr] = perpendicDistFromLineSqrd(path[curr], path[prev], path[next])
                if (prev != 0 || !isOpenPath) {
                    dsq[prev] = perpendicDistFromLineSqrd(path[prev], path[prior2], path[curr])
                }
            }
        }
        val result = PathD() //len
        for (i in 0 until len) {
            if (!flags[i]) {
                result.add(path[i])
            }
        }
        return result
    }

    fun simplifyPaths(paths: PathsD, epsilon: Double, isOpenPath: Boolean = false): PathsD {
        val result = PathsD() //paths.size
        for (path in paths) {
            result.add(simplifyPath(path, epsilon, isOpenPath))
        }
        return result
    }

    /**
     * This function removes the vertices between adjacent collinear segments. It
     * will also remove duplicate vertices (adjacent vertices with identical
     * coordinates).
     *
     *
     * Note: Duplicate vertices will be removed automatically from clipping
     * solutions, but not collinear edges unless the Clipper object's
     * PreserveCollinear property had been disabled.
     */
    fun trimCollinear(path: Path64, isOpen: Boolean = false): Path64 {
        var len = path.size
        var i = 0
        if (!isOpen) {
            while (i < len - 1 && InternalClipper.crossProduct(path[len - 1], path[i], path[i + 1]) == 0.0) {
                i++
            }
            while (i < len - 1 && InternalClipper.crossProduct(path[len - 2], path[len - 1], path[i]) == 0.0) {
                len--
            }
        }
        if (len - i < 3) {
            return if (!isOpen || len < 2 || path[0].equals(path[1])) {
                Path64()
            } else path
        }
        val result = Path64() //len - i
        var last = path[i]
        result.add(last)
        i++
        while (i < len - 1) {
            if (InternalClipper.crossProduct(last, path[i], path[i + 1]) == 0.0) {
                i++
                continue
            }
            last = path[i]
            result.add(last)
            i++
        }
        if (isOpen) {
            result.add(path[len - 1])
        } else if (InternalClipper.crossProduct(last, path[len - 1], result[0]) != 0.0) {
            result.add(path[len - 1])
        } else {
            while (result.size > 2
                && InternalClipper.crossProduct(result[result.size - 1], result[result.size - 2], result[0]) == 0.0
            ) {
                result.removeAt(result.size - 1)
            }
            if (result.size < 3) {
                result.clear()
            }
        }
        return result
    }

    /**
     * This function removes the vertices between adjacent collinear segments. It
     * will also remove duplicate vertices (adjacent vertices with identical
     * coordinates).
     *
     *
     * With floating point paths, the precision parameter indicates the decimal
     * precision that's required when determining collinearity.
     *
     *
     * Note: Duplicate vertices will be removed automatically from clipping
     * solutions, but not collinear edges unless the Clipper object's
     * PreserveCollinear property had been disabled.
     */
    fun trimCollinear(path: PathD, precision: Int, isOpen: Boolean = false): PathD {
        InternalClipper.checkPrecision(precision)
        val scale: Double = 10.0.pow(precision.toDouble())
        var p = scalePath64(path, scale)
        p = trimCollinear(p, isOpen)
        return scalePathD(p, 1 / scale)
    }

    fun pointInPolygon(pt: Point64, polygon: Path64): PointInPolygonResult {
        return InternalClipper.pointInPolygon(pt, polygon)
    }

    fun pointInPolygon(pt: PointD?, polygon: PathD, precision: Int = 2): PointInPolygonResult {
        InternalClipper.checkPrecision(precision)
        val scale: Double = 10.0.pow(precision.toDouble())
        val p = Point64(pt!!, scale)
        val path = scalePath64(polygon, scale)
        return InternalClipper.pointInPolygon(p, path)
    }

    fun ellipse(center: Point64, radiusX: Double, radiusY: Double = 0.0, steps: Int = 0): Path64 {
        var radiusY = radiusY
        var steps = steps
        if (radiusX <= 0) {
            return Path64()
        }
        if (radiusY <= 0) {
            radiusY = radiusX
        }
        if (steps <= 2) {
            steps = ceil(PI * sqrt((radiusX + radiusY) / 2)).toInt()
        }
        val si: Double = sin(2 * PI / steps)
        val co: Double = cos(2 * PI / steps)
        var dx = co
        var dy = si
        val result = Path64() //steps
        result.add(Point64(center.x + radiusX, center.x.toDouble()))
        for (i in 1 until steps) {
            result.add(Point64(center.x + radiusX * dx, center.y + radiusY * dy))
            val x = dx * co - dy * si
            dy = dy * co + dx * si
            dx = x
        }
        return result
    }

    fun ellipse(center: PointD, radiusX: Double, radiusY: Double = 0.0, steps: Int = 0): PathD {
        var radiusY = radiusY
        var steps = steps
        if (radiusX <= 0) {
            return PathD()
        }
        if (radiusY <= 0) {
            radiusY = radiusX
        }
        if (steps <= 2) {
            steps = ceil(PI * sqrt((radiusX + radiusY) / 2)).toInt()
        }
        val si: Double = sin(2 * PI / steps)
        val co: Double = cos(2 * PI / steps)
        var dx = co
        var dy = si
        val result = PathD() //steps
        result.add(PointD(center.x + radiusX, center.y))
        for (i in 1 until steps) {
            result.add(PointD(center.x + radiusX * dx, center.y + radiusY * dy))
            val x = dx * co - dy * si
            dy = dy * co + dx * si
            dx = x
        }
        return result
    }
}