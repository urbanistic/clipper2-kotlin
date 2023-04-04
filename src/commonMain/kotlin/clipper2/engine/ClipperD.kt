package clipper2.engine

import Clipper.scalePath64
import Clipper.scalePathD
import Clipper.scalePaths64
import clipper2.core.ClipType
import clipper2.core.FillRule
import clipper2.core.PathD
import clipper2.core.PathType
import clipper2.core.Paths64
import clipper2.core.PathsD
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.math.pow

/**
 * The ClipperD class performs boolean 'clipping'. This class is very similar to
 * Clipper64 except that coordinates passed to ClipperD objects are of type
 * `double` instead of type `long`.
 */
@JsExport
class ClipperD constructor(roundingDecimalPrecision: Int = 2) : ClipperBase() {
    private val scale: Double
    private val invScale: Double

    /**
     * @param roundingDecimalPrecision default = 2
     */
    init {
        if (roundingDecimalPrecision < -8 || roundingDecimalPrecision > 8) {
            throw IllegalArgumentException("Error - RoundingDecimalPrecision exceeds the allowed range.")
        }
        scale = (10.0).pow(roundingDecimalPrecision.toDouble())
        invScale = 1 / scale
    }

    @JvmOverloads
    fun addPath(path: PathD?, polytype: PathType, isOpen: Boolean = false) {
        super.addPath(scalePath64(path!!, scale), polytype, isOpen)
    }

    @JvmOverloads
    fun addPaths(paths: PathsD?, polytype: PathType, isOpen: Boolean = false) {
        super.addPaths(scalePaths64(paths!!, scale), polytype, isOpen)
    }

    fun addSubject(path: PathD?) {
        addPath(path, PathType.Subject)
    }

    fun addOpenSubject(path: PathD?) {
        addPath(path, PathType.Subject, true)
    }

    fun addClip(path: PathD?) {
        addPath(path, PathType.Clip)
    }

    fun addSubjects(paths: PathsD?) {
        addPaths(paths, PathType.Subject)
    }

    fun addOpenSubjects(paths: PathsD?) {
        addPaths(paths, PathType.Subject, true)
    }

    fun addClips(paths: PathsD?) {
        addPaths(paths, PathType.Clip)
    }

    @JvmOverloads
    fun execute(
        clipType: ClipType,
        fillRule: FillRule,
        solutionClosed: PathsD,
        solutionOpen: PathsD = PathsD()
    ): Boolean {
        val solClosed64 = Paths64()
        val solOpen64 = Paths64()
        var success = true
        solutionClosed.clear()
        solutionOpen.clear()
        try {
            executeInternal(clipType, fillRule)
            buildPaths(solClosed64, solOpen64)
        } catch (e: Exception) {
            success = false
        }
        clearSolutionOnly()
        if (!success) {
            return false
        }
        for (path in solClosed64) {
            solutionClosed.add(scalePathD(path, invScale))
        }
        for (path in solOpen64) {
            solutionOpen.add(scalePathD(path, invScale))
        }
        return true
    }

    @JvmOverloads
    @JsName("executePolytree")
    fun execute(
        clipType: ClipType,
        fillRule: FillRule,
        polytree: PolyTreeD,
        openPaths: PathsD = PathsD()
    ): Boolean {
        polytree.clear()
        polytree.scale = scale
        openPaths.clear()
        val oPaths = Paths64()
        var success = true
        try {
            executeInternal(clipType, fillRule)
            buildTree(polytree, oPaths)
        } catch (e: Exception) {
            success = false
        }
        clearSolutionOnly()
        if (!success) {
            return false
        }
        if (!oPaths.isEmpty()) {
            for (path in oPaths) {
                openPaths.add(scalePathD(path, invScale))
            }
        }
        return true
    }
}
