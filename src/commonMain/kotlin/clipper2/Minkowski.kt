package clipper2

import clipper2.core.FillRule
import clipper2.core.Path64
import clipper2.core.PathD
import clipper2.core.Paths64
import clipper2.core.PathsD
import clipper2.core.Point64
import kotlin.math.pow

object Minkowski {
    fun sum(pattern: Path64, path: Path64, isClosed: Boolean): Paths64 {
        return Clipper.Union(minkowskiInternal(pattern, path, true, isClosed), null, FillRule.NonZero)
    }

    fun sum(pattern: PathD, path: PathD, isClosed: Boolean): PathsD {
        return sum(pattern, path, isClosed, 2)
    }

    fun sum(pattern: PathD, path: PathD, isClosed: Boolean, decimalPlaces: Int): PathsD {
        val scale: Double = 10.0.pow(decimalPlaces.toDouble())
        val tmp: Paths64 = Clipper.Union(
            minkowskiInternal(Clipper.scalePath64(pattern, scale), Clipper.scalePath64(path, scale), true, isClosed),
            null,
            FillRule.NonZero
        )
        return Clipper.scalePathsD(tmp, 1 / scale)
    }

    fun diff(pattern: Path64, path: Path64, isClosed: Boolean): Paths64 {
        return Clipper.Union(minkowskiInternal(pattern, path, false, isClosed), null, FillRule.NonZero)
    }

    fun diff(pattern: PathD, path: PathD, isClosed: Boolean): PathsD {
        return diff(pattern, path, isClosed, 2)
    }

    fun diff(pattern: PathD, path: PathD, isClosed: Boolean, decimalPlaces: Int): PathsD {
        val scale: Double = 10.0.pow(decimalPlaces.toDouble())
        val tmp: Paths64 = Clipper.Union(
            minkowskiInternal(Clipper.scalePath64(pattern, scale), Clipper.scalePath64(path, scale), false, isClosed),
            null,
            FillRule.NonZero
        )
        return Clipper.scalePathsD(tmp, 1 / scale)
    }

    private fun minkowskiInternal(pattern: Path64, path: Path64, isSum: Boolean, isClosed: Boolean): Paths64 {
        val delta = if (isClosed) 0 else 1
        val patLen: Int = pattern.size
        val pathLen: Int = path.size
        val tmp: Paths64 = Paths64()//new Paths64(pathLen);
        for (pathPt in path) {
            val path2: Path64 = Path64() //new Path64(patLen);
            if (isSum) {
                for (basePt in pattern) {
                    path2.add(Point64.opAdd(pathPt, basePt))
                }
            } else {
                for (basePt in pattern) {
                    path2.add(Point64.opSubtract(pathPt, basePt))
                }
            }
            tmp.add(path2)
        }
        val result: Paths64 = Paths64() // Paths64((pathLen - delta) * patLen)
        var g = if (isClosed) pathLen - 1 else 0
        var h = patLen - 1
        for (i in delta until pathLen) {
            for (j in 0 until patLen) {
                val quad: Path64 = Path64.of(tmp[g][h], tmp[i][h], tmp[i][j], tmp[g][j])
                if (!Clipper.isPositive(quad)) {
                    result.add(Clipper.reversePath(quad))
                } else {
                    result.add(quad)
                }
                h = j
            }
            g = i
        }
        return result
    }
}