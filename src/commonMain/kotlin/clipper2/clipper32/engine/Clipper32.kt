package clipper2.clipper32.engine

import clipper2.clipper32.core.Paths32
import clipper2.core.ClipType
import clipper2.core.FillRule
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * The Clipper class performs boolean 'clipping'. This class is very similar to
 * ClipperD except that coordinates passed to Clipper64 objects are of type
 * `long` instead of type `double`.
 */
@JsExport
class Clipper32 : ClipperBase32() {
    /**
     * Once subject and clip paths have been assigned (via
     * [addSubject()][.addSubject], [ addOpenSubject()][.addOpenSubject] and [addClip()][.addClip] methods),
     * `Execute()` can then perform the specified clipping operation
     * (intersection, union, difference or XOR).
     *
     *
     * The solution parameter can be either a Paths64 or a PolyTree64, though since
     * the Paths64 structure is simpler and more easily populated (with clipping
     * about 5% faster), it should generally be preferred.
     *
     *
     * While polygons in solutions should never intersect (either with other
     * polygons or with themselves), they will frequently be nested such that outer
     * polygons will contain inner 'hole' polygons with in turn may contain outer
     * polygons (to any level of nesting). And given that PolyTree64 and PolyTreeD
     * preserve these parent-child relationships, these two PolyTree classes will be
     * very useful to some users.
     */
    @JvmOverloads
    fun execute(
        clipType: ClipType,
        fillRule: FillRule,
        solutionClosed: Paths32,
        solutionOpen: Paths32 = Paths32()
    ): Boolean {
        solutionClosed.clear()
        solutionOpen.clear()
        try {
            executeInternal(clipType, fillRule)
            buildPaths(solutionClosed, solutionOpen)
        } catch (e: Exception) {
            succeeded = false
        }
        clearSolutionOnly()
        return succeeded
    }

    @JvmOverloads
    @JsName("executePolytree")
    fun execute(
        clipType: ClipType,
        fillRule: FillRule,
        polytree: PolyTree32,
        openPaths: Paths32 = Paths32()
    ): Boolean {
        polytree.clear()
        openPaths.clear()
        usingPolytree = true
        try {
            executeInternal(clipType, fillRule)
            buildTree(polytree, openPaths)
        } catch (e: Exception) {
            succeeded = false
        }
        clearSolutionOnly()
        return succeeded
    }
}
