package clipper2.core

import clipper2.engine.PointInPolygonResult
import kotlin.math.abs
import kotlin.math.roundToLong

object InternalClipper {
    private const val MAXCOORD = Long.MAX_VALUE / 4
    private const val MAX_COORD = MAXCOORD.toDouble()
    private const val MIN_COORD = -MAXCOORD.toDouble()
    private const val Invalid64 = Long.MAX_VALUE
    const val DEFAULT_ARC_TOLERANCE = 0.25
    private const val FLOATING_POINT_TOLERANCE = 1E-12

    //	private static final double DEFAULT_MIN_EDGE_LENGTH = 0.1;
    private const val PRECISION_RANGE_ERROR = "Error: Precision is out of range."

    fun checkPrecision(precision: Int) {
        if (precision < -8 || precision > 8) {
            throw IllegalArgumentException(PRECISION_RANGE_ERROR)
        }
    }

    fun isAlmostZero(value: Double): Boolean {
        return abs(value) <= FLOATING_POINT_TOLERANCE
    }

    fun crossProduct(pt1: Point64, pt2: Point64, pt3: Point64): Double {
        return ((pt2.x - pt1.x) * (pt3.y - pt2.y) - (pt2.y - pt1.y) * (pt3.x - pt2.x)).toDouble()
    }

    fun dotProduct(pt1: Point64, pt2: Point64, pt3: Point64): Double {
        return ((pt2.x - pt1.x) * (pt3.x - pt2.x) + (pt2.y - pt1.y) * (pt3.y - pt2.y)).toDouble()
    }

    fun crossProduct(vec1: PointD, vec2: PointD): Double {
        return vec1.y * vec2.x - vec2.y * vec1.x
    }

    fun dotProduct(vec1: PointD, vec2: PointD): Double {
        return vec1.x * vec2.x + vec1.y * vec2.y
    }

    fun checkCastInt64(v: Double): Long {
        return if (v >= MAX_COORD || v <= MIN_COORD) {
            Invalid64
        } else {
            v.roundToLong()
        }
    }

    fun getIntersectPt(
        ln1a: Point64, ln1b: Point64, ln2a: Point64, ln2b: Point64,  /* out */
        ip: Point64
    ): Boolean {
        val dy1 = (ln1b.y - ln1a.y).toDouble()
        val dx1 = (ln1b.x - ln1a.x).toDouble()
        val dy2 = (ln2b.y - ln2a.y).toDouble()
        val dx2 = (ln2b.x - ln2a.x).toDouble()
        val cp = dy1 * dx2 - dy2 * dx1
        if (cp == 0.0) {
            return false
        }
        val qx = dx1 * ln1a.y - dy1 * ln1a.x
        val qy = dx2 * ln2a.y - dy2 * ln2a.x
        ip.x = checkCastInt64((dx1 * qy - dx2 * qx) / cp)
        ip.y = checkCastInt64((dy1 * qy - dy2 * qx) / cp)
        return ip.x != Invalid64 && ip.y != Invalid64
    }

    fun getIntersectPoint(
        ln1a: Point64, ln1b: Point64, ln2a: Point64, ln2b: Point64,  /* out */
        ip: PointD
    ): Boolean {
        val dy1 = (ln1b.y - ln1a.y).toDouble()
        val dx1 = (ln1b.x - ln1a.x).toDouble()
        val dy2 = (ln2b.y - ln2a.y).toDouble()
        val dx2 = (ln2b.x - ln2a.x).toDouble()
        val q1 = dy1 * ln1a.x - dx1 * ln1a.y
        val q2 = dy2 * ln2a.x - dx2 * ln2a.y
        val cross_prod = dy1 * dx2 - dy2 * dx1
        if (cross_prod == 0.0) {
            return false
        }
        ip.x = (dx2 * q1 - dx1 * q2) / cross_prod
        ip.y = (dy2 * q1 - dy1 * q2) / cross_prod
        return true
    }

    fun segsIntersect(
        seg1a: Point64,
        seg1b: Point64,
        seg2a: Point64,
        seg2b: Point64,
        inclusive: Boolean = false
    ): Boolean {
        return if (inclusive) {
            val res1 = crossProduct(seg1a, seg2a, seg2b)
            val res2 = crossProduct(seg1b, seg2a, seg2b)
            if (res1 * res2 > 0) {
                return false
            }
            val res3 = crossProduct(seg2a, seg1a, seg1b)
            val res4 = crossProduct(seg2b, seg1a, seg1b)
            if (res3 * res4 > 0) {
                false
            } else res1 != 0.0 || res2 != 0.0 || res3 != 0.0 || res4 != 0.0
            // ensure NOT collinear
        } else {
            crossProduct(seg1a, seg2a, seg2b) * crossProduct(seg1b, seg2a, seg2b) < 0 && crossProduct(
                seg2a,
                seg1a,
                seg1b
            ) * crossProduct(seg2b, seg1a, seg1b) < 0
        }
    }

    fun getClosestPtOnSegment(offPt: Point64, seg1: Point64, seg2: Point64): Point64 {
        if (seg1.x == seg2.x && seg1.y == seg2.y) {
            return seg1
        }
        val dx = (seg2.x - seg1.x).toDouble()
        val dy = (seg2.y - seg1.y).toDouble()
        var q = ((offPt.x - seg1.x) * dx + (offPt.y - seg1.y) * dy) / (dx * dx + dy * dy)
        if (q < 0) {
            q = 0.0
        } else if (q > 1) {
            q = 1.0
        }
        return Point64(seg1.x + (q * dx).roundToLong(), seg1.y + (q * dy).roundToLong())
    }

    fun pointInPolygon(pt: Point64, polygon: Path64): PointInPolygonResult {
        val len: Int = polygon.size
        var start = 0
        if (len < 3) {
            return PointInPolygonResult.IsOutside
        }
        while (start < len && polygon[start].y == pt.y) {
            start++
        }
        if (start == len) {
            return PointInPolygonResult.IsOutside
        }
        var d: Double
        var isAbove = polygon[start].y < pt.y
        val startingAbove = isAbove
        var v = 0
        var i = start + 1
        var end = len
        while (true) {
            if (i == end) {
                if (end == 0 || start == 0) {
                    break
                }
                end = start
                i = 0
            }
            if (isAbove) {
                while (i < end && polygon[i].y < pt.y) {
                    i++
                }
                if (i == end) {
                    continue
                }
            } else {
                while (i < end && polygon[i].y > pt.y) {
                    i++
                }
                if (i == end) {
                    continue
                }
            }
            val curr = polygon[i]
            val prev: Point64 = if (i > 0) {
                polygon[i - 1]
            } else {
                polygon[len - 1]
            }
            if (curr.y == pt.y) {
                if (curr.x == pt.x || curr.y == prev.y && pt.x < prev.x != pt.x < curr.x) {
                    return PointInPolygonResult.IsOn
                }
                i++
                if (i == start) {
                    break
                }
                continue
            }
            if (pt.x < curr.x && pt.x < prev.x) {
                // we're only interested in edges crossing on the left
            } else if (pt.x > prev.x && pt.x > curr.x) {
                v = 1 - v // toggle val
            } else {
                d = crossProduct(prev, curr, pt)
                if (d == 0.0) {
                    return PointInPolygonResult.IsOn
                }
                if (d < 0 == isAbove) {
                    v = 1 - v
                }
            }
            isAbove = !isAbove
            i++
        }
        if (isAbove != startingAbove) {
            if (i == len) {
                i = 0
            }
            d = if (i == 0) {
                crossProduct(polygon[len - 1], polygon[0], pt)
            } else {
                crossProduct(polygon[i - 1], polygon[i], pt)
            }
            if (d == 0.0) {
                return PointInPolygonResult.IsOn
            }
            if (d < 0 == isAbove) {
                v = 1 - v
            }
        }
        return if (v == 0) {
            PointInPolygonResult.IsOutside
        } else PointInPolygonResult.IsInside
    }
}