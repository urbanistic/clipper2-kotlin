package clipper2

import Clipper
import clipper2.core.*
import clipper2.offset.ClipperOffset
import clipper2.offset.EndType
import clipper2.offset.JoinType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class TestOffset {
    val iterations = 1

    @OptIn(ExperimentalTime::class)
    @Test
    fun runTests() {
        var counter = 0
        val data = ClipperFileIO.loadTestCases("Offsets.txt")
        val dataMap = mutableMapOf<String, ClipperFileIO.TestCase>()

        for (testcase in data) {
            var label = testcase.caption ?: ("LinesTest:" + counter++)
            if (label.isEmpty()) {
                label = "LinesTest:" + counter++
            }
            dataMap[label] = testcase
        }

        for (test in dataMap) {
            print("${test.key} ${test.value.clipType} ${test.value.fillRule}")
            val time = measureTime {
                for (i in 0 until iterations) {
                    runOffsetTestCase(test.value)
                }
            }
            println(" -> passed in $time")
        }
    }

    fun runOffsetTestCase(test: ClipperFileIO.TestCase) {

        val co = ClipperOffset()
        co.addPaths(test.subj, JoinType.Round, EndType.Polygon)

        val outputs = Paths64()
        co.execute(1.0, outputs)

        // is the sum total area of the solution is positive
        val outer_is_positive = Clipper.area(outputs) > 0

        // there should be exactly one exterior path
        val is_positive_count = outputs.filter { Clipper.isPositive(it) }.size
        val is_negative_count = outputs.size - is_positive_count

        if (outer_is_positive) {
            assertEquals(1, is_positive_count)
        } else {
            assertEquals(1, is_negative_count)
        }
    }

    fun midPoint(p1: Point64, p2: Point64): Point64 {
        val result = Point64()
        result.x = (p1.x + p2.x) / 2
        result.y = (p1.y + p2.y) / 2
        return result
    }

    @Test
    fun test2() { // see #448 & #456
        val scale = 10.0
        val delta: Double = 10.0 * scale
        val arc_tol: Double = 0.25 * scale

        val solution = Paths64()

        var subject = Paths64()
        subject.add(
            Path64.of(
                Point64(50, 50),
                Point64(100, 50),
                Point64(100, 150),
                Point64(50, 150),
                Point64(0, 100)
            )
        )

        subject = Clipper.scalePaths(subject, scale)

        val co: ClipperOffset = ClipperOffset()
        co.addPaths(subject, JoinType.Round, EndType.Polygon)
        co.arcTolerance = arc_tol
        co.execute(delta, solution)

        var min_dist = delta * 2
        var max_dist = 0.0

        for (subjPt in subject[0]) {
            var prevPt: Point64 = solution[0][solution[0].size - 1]
            for (pt in solution[0]) {
                val mp: Point64 = midPoint(prevPt, pt)
                val d: Double = Clipper.distance(mp, subjPt)
                if (d < delta * 2) {
                    if (d < min_dist) min_dist = d
                    if (d > max_dist) max_dist = d
                }
                prevPt = pt
            }
        }

        assertTrue { min_dist + 1 > delta - arc_tol }
        assertTrue { solution[0].size <= 21 }
    }

    @Test
    fun test3() { // see #424
        val subjects = Paths64.of(
            Clipper.makePath(
                longArrayOf(
                    1525311078, 1352369439,
                    1526632284, 1366692987,
                    1519397110, 1367437476,
                    1520246456, 1380177674,
                    1520613458, 1385913385,
                    1517383844, 1386238444,
                    1517771817, 1392099983,
                    1518233190, 1398758441,
                    1518421934, 1401883197,
                    1518694564, 1406612275,
                    1520267428, 1430289121,
                    1520770744, 1438027612,
                    1521148232, 1443438264,
                    1521441833, 1448964260,
                    1521683005, 1452518932,
                    1521819320, 1454374912,
                    1527943004, 1454154711,
                    1527649403, 1448523858,
                    1535901696, 1447989084,
                    1535524209, 1442788147,
                    1538953052, 1442463089,
                    1541553521, 1442242888,
                    1541459149, 1438855987,
                    1538764308, 1439076188,
                    1538575565, 1436832236,
                    1538764308, 1436832236,
                    1536509870, 1405374956,
                    1550497874, 1404347351,
                    1550214758, 1402428457,
                    1543818445, 1402868859,
                    1543734559, 1402124370,
                    1540672717, 1402344571,
                    1540473487, 1399995761,
                    1524996506, 1400981422,
                    1524807762, 1398223667,
                    1530092585, 1397898609,
                    1531675935, 1397783265,
                    1531392819, 1394920653,
                    1529809469, 1395025510,
                    1529348096, 1388880855,
                    1531099218, 1388660654,
                    1530826588, 1385158410,
                    1532955197, 1384938209,
                    1532661596, 1379003269,
                    1532472852, 1376235028,
                    1531277476, 1376350372,
                    1530050642, 1361806623,
                    1599487345, 1352704983,
                    1602758902, 1378489467,
                    1618990858, 1376350372,
                    1615058698, 1344085688,
                    1603230761, 1345700495,
                    1598648484, 1346329641,
                    1598931599, 1348667965,
                    1596698132, 1348993024,
                    1595775386, 1342722540
                )
            )
        )

        val solution = Clipper.inflatePaths(subjects, -209715.0, JoinType.Miter, EndType.Polygon)

        assertTrue(solution[0].size - subjects[0].size <= 1)
    }

    @Test
    fun test4() { // see #482
        var paths = Paths64.of(
            Clipper.makePath(
                longArrayOf(
                    0, 0,
                    20000, 200,
                    40000, 0,
                    40000, 50000,
                    0, 50000,
                    0, 0
                )
            )
        )
        var solution = Clipper.inflatePaths(paths, -5000.0, JoinType.Square, EndType.Polygon)
        // std::cout << solution[0].size() << std::endl;
        assertEquals(5, solution[0].size)

        paths = Paths64.of(
            Clipper.makePath(
                longArrayOf(
                    0, 0,
                    20000, 400,
                    40000, 0,
                    40000, 50000,
                    0, 50000,
                    0, 0
                )
            )
        )
        solution = Clipper.inflatePaths(paths, -5000.0, JoinType.Square, EndType.Polygon)
        // std::cout << solution[0].size() << std::endl;
        assertEquals(6, solution[0].size)

        paths = Paths64.of(
            Clipper.makePath(
                longArrayOf(
                    0, 0,
                    20000, 400,
                    40000, 0,
                    40000, 50000,
                    0, 50000,
                    0, 0
                )
            )
        )
        solution = Clipper.inflatePaths(paths, -5000.0, JoinType.Round, EndType.Polygon)
        // std::cout << solution[0].size() << std::endl;
        assertEquals(6, solution[0].size)

        paths = Paths64.of(
            Clipper.makePath(
                longArrayOf(
                    0, 0,
                    20000, 1500,
                    40000, 0,
                    40000, 50000,
                    0, 50000,
                    0, 0
                )
            )
        )
        solution = Clipper.inflatePaths(paths, -5000.0, JoinType.Round, EndType.Polygon)
        // std::cout << solution[0].size() << std::endl;
        assertTrue(solution[0].size > 6)
    }

    @Test
    fun testOffsetOrientation() {
        val co = ClipperOffset()

        val input = Clipper.makePath(
            longArrayOf(
                0, 0,
                0, 5,
                5, 5,
                5, 0
            )
        )

        co.addPath(input, JoinType.Round, EndType.Polygon)

        val outputs = Paths64()
        co.execute(1.0, outputs)

        assertEquals(1, outputs.size)
        // when offsetting, output orientation should match input
        assertTrue { Clipper.isPositive(input) == Clipper.isPositive(outputs[0]) }
    }
}