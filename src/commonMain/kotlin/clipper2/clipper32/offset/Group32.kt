package clipper2.clipper32.offset

import clipper2.clipper32.core.Path32
import clipper2.clipper32.core.Paths32
import clipper2.offset.EndType
import clipper2.offset.JoinType
import kotlin.jvm.JvmOverloads

internal class Group32 @JvmOverloads constructor(
    paths: Paths32,
    joinType: JoinType,
    endType: EndType = EndType.Polygon
) {
    var inPaths: Paths32
    var outPath: Path32
    var outPaths: Paths32
    var joinType: JoinType
    var endType: EndType
    var pathsReversed: Boolean

    init {
        inPaths = Paths32.of(paths) // paths
        this.joinType = joinType
        this.endType = endType
        outPath = Path32()
        outPaths = Paths32()
        pathsReversed = false
    }
}
