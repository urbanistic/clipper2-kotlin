package clipper2.offset

import clipper2.core.Path64
import clipper2.core.Paths64
import kotlin.jvm.JvmOverloads

internal class Group @JvmOverloads constructor(
    paths: Paths64,
    joinType: JoinType,
    endType: EndType = EndType.Polygon
) {
    var inPaths: Paths64
    var outPath: Path64
    var outPaths: Paths64
    var joinType: JoinType
    var endType: EndType
    var pathsReversed: Boolean

    init {
        inPaths = Paths64.copy(paths) // paths
        this.joinType = joinType
        this.endType = endType
        outPath = Path64()
        outPaths = Paths64()
        pathsReversed = false
    }
}
