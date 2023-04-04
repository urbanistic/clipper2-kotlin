package clipper2

import Clipper
import Clipper.pointInPolygon
import Clipper.polyTreeToPaths64
import clipper2.core.Path64
import clipper2.core.Paths64
import clipper2.core.Point64
import clipper2.engine.Clipper64
import clipper2.engine.PointInPolygonResult
import clipper2.engine.PolyPath64
import clipper2.engine.PolyTree64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import tangible.RefObject

//class MyPolytreeTests : FunSpec() {
//    var counter = 0
//    val data = ClipperFileIO.loadTestCases("PolytreeHoleOwner2.txt")
//    val dataMap = mutableMapOf<String, ClipperFileIO.TestCase>()
//
//    init {
//        for(testcase in data){
//            var label = testcase.caption ?: ("PolytreeTest:" + counter++)
//            if(label.isEmpty()){
//                label = "PolytreeTest:" + counter++
//            }
//            dataMap[label] = testcase
//        }
//
//        withData(
//            dataMap
//        ) {
//            runPolytreesTestCaseKotest(it)
//        }
//    }
//}

//fun runPolytreesTestCaseKotest(test: ClipperFileIO.TestCase) {
//    val solutionTree = PolyTree64()
//    val solution_open = Paths64()
//    val clipper = Clipper64()
//    val subject: Paths64 = test.subj
//    val subjectOpen: Paths64 = test.subj_open
//    val clip: Paths64 = test.clip
//    val pointsOfInterestOutside: List<Point64> = listOf(
//        Point64(21887, 10420), Point64(21726, 10825),
//        Point64(21662, 10845), Point64(21617, 10890)
//    )
//    for (pt in pointsOfInterestOutside) {
//        for (path in subject) {
//            PointInPolygonResult.IsOutside shouldBe pointInPolygon(pt, path) // "outside point of interest found inside subject"
//        }
//    }
//    val pointsOfInterestInside: List<Point64> = listOf(
//        Point64(21887, 10430), Point64(21843, 10520),
//        Point64(21810, 10686), Point64(21900, 10461)
//    )
//    for (pt in pointsOfInterestInside) {
//        var poi_inside_counter = 0
//        for (path in subject) {
//            if (pointInPolygon(pt, path) === PointInPolygonResult.IsInside) {
//                poi_inside_counter++
//            }
//        }
//        1 shouldBe poi_inside_counter // "poi_inside_counter - expected 1 but got $poi_inside_counter"
//    }
//    clipper.addSubjects(subject)
//    clipper.addOpenSubjects(subjectOpen)
//    clipper.addClips(clip)
//    clipper.execute(test.clipType!!, test.fillRule!!, solutionTree, solution_open)
//    val solutionPaths: Paths64 = polyTreeToPaths64(solutionTree)
//    val a1: Double = Clipper.area(solutionPaths)
//    val a2: Double = solutionTree.area()
//
//    //CHECKS
//    (a1 > 330000) shouldBe true // "solution has wrong area - value expected: 331,052; value returned; $a1 "
//    (abs(a1 - a2) < 0.0001) shouldBe true // "solution tree has wrong area - value expected: $a1; value returned; $a2 "
//    checkPolytreeFullyContainsChildren(solutionTree) shouldBe true // "The polytree doesn't properly contain its children"
//    for (pt in pointsOfInterestOutside) {
//        polytreeContainsPointKotest(solutionTree, pt) shouldBe false // "The polytree indicates it contains a point that it should not contain"
//    }
//    for (pt in pointsOfInterestInside) {
//        polytreeContainsPointKotest(solutionTree, pt) shouldBe true // "The polytree indicates it does not contain a point that it should contain"
//    }
//}
//
//private fun checkPolytreeFullyContainsChildren(polytree: PolyTree64): Boolean {
//    for (p in polytree) {
//        val child = p as PolyPath64
//        if (child.count > 0 && !polyPathFullyContainsChildren(child)) {
//            return false
//        }
//    }
//    return true
//}
//
//private fun polyPathFullyContainsChildren(pp: PolyPath64): Boolean {
//    for (c in pp) {
//        val child = c as PolyPath64
//        for (pt in child.polygon!!) {
//            if (pointInPolygon(pt, pp.polygon) === PointInPolygonResult.IsOutside) {
//                return false
//            }
//        }
//        if (child.count > 0 && !polyPathFullyContainsChildren(child)) {
//            return false
//        }
//    }
//    return true
//}
//
//fun polytreeContainsPointKotest(pp: PolyTree64, pt: Point64): Boolean {
//    var counter = 0
//    for (i in 0 until pp.count) {
//        val child: PolyPath64 = pp.get(i)
//        val tempRef_counter = RefObject(counter)
//        PolyPathContainsPointKotest(child, pt, tempRef_counter)
//        counter = tempRef_counter.argValue!!
//    }
//    (counter >= 0) shouldBe true // "Polytree has too many holes"
//    return counter != 0
//}
//
//fun PolyPathContainsPointKotest(pp: PolyPath64, pt: Point64, counter: RefObject<Int>) {
//    if (pointInPolygon(pt, pp.polygon) !== PointInPolygonResult.IsOutside) {
//        if (pp.isHole) {
//            counter.argValue = counter.argValue!! - 1
//        } else {
//            counter.argValue = counter.argValue!! + 1
//        }
//    }
//    for (i in 0 until pp.count) {
//        val child = pp[i]
//        PolyPathContainsPointKotest(child, pt, counter)
//    }
//}

public class TestPolytrees {
    var pointInPolygonTotalCheckTime: Duration = Duration.ZERO
    var pointInPolygonNumChecks: Int = 0

    @OptIn(ExperimentalTime::class)
    @Test
    fun runTests() {
        val data = ClipperFileIO.loadTestCases("PolytreeHoleOwner2.txt")
        val testcase: ClipperFileIO.TestCase = data[0]

        pointInPolygonTotalCheckTime = Duration.ZERO
        pointInPolygonNumChecks = 0

        println("${testcase.caption} ${testcase.clipType} ${testcase.fillRule}")
        val time = measureTime {
            runPolytreesTestCase(testcase)
        }
        println(" -> passed in $time")
    }

    @OptIn(ExperimentalTime::class)
    private fun runPolytreesTestCase(test: ClipperFileIO.TestCase) {
        val solutionTree = PolyTree64()
        val solution_open = Paths64()
        val clipper = Clipper64()
        val subject: Paths64 = test.subj
        val subjectOpen: Paths64 = test.subj_open
        val clip: Paths64 = test.clip

        val pointsOfInterestOutside: List<Point64> = listOf(
            Point64(21887, 10420),
            Point64(21726, 10825),
            Point64(21662, 10845),
            Point64(21617, 10890)
        )

        for (pt in pointsOfInterestOutside) {
            for (path in subject) {
                val result = pointInPolygon(
                    pt,
                    path
                )
                assertEquals(PointInPolygonResult.IsOutside, result, "outside point of interest found inside subject")
            }
        }

        val pointsOfInterestInside: List<Point64> = listOf(
            Point64(21887, 10430),
            Point64(21843, 10520),
            Point64(21810, 10686),
            Point64(21900, 10461)
        )

        for (pt in pointsOfInterestInside) {
            var poi_inside_counter = 0
            for (path in subject) {
                if (pointInPolygon(pt, path) === PointInPolygonResult.IsInside) {
                    poi_inside_counter++
                }
            }
            assertEquals(1, poi_inside_counter, "poi_inside_counter - expected 1 but got $poi_inside_counter")
        }

        var time = measureTime {
            clipper.addSubjects(subject)
            clipper.addOpenSubjects(subjectOpen)
            clipper.addClips(clip)
        }
        println("filled clipper in $time")

        time = measureTime {
            clipper.execute(test.clipType!!, test.fillRule!!, solutionTree, solution_open)
        }
        println("executed in $time")

        var solutionPaths: Paths64
        time = measureTime {
            solutionPaths = polyTreeToPaths64(solutionTree)
        }
        println("solutionTree to solutionPaths in $time")

        var a1: Double
        var a2: Double
        time = measureTime {
            a1 = Clipper.area(solutionPaths)
            a2 = solutionTree.area()
        }
        println("calculating area in $time")

        //CHECKS
        assertTrue(a1 > 330000, "solution has wrong area - value expected: 331,052; value returned; $a1 ")
        assertTrue(abs(a1 - a2) < 0.0001, "solution tree has wrong area - value expected: $a1; value returned; $a2 ")
        time = measureTime {
            assertTrue(
                checkPolytreeFullyContainsChildren(solutionTree),
                "The polytree doesn't properly contain its children"
            )
        }
        println("checktime per point: ${pointInPolygonTotalCheckTime / pointInPolygonNumChecks} = time(${pointInPolygonTotalCheckTime})/point($pointInPolygonNumChecks)")
        println("checkPolytreeFullyContainsChildren in $time")

        for (pt in pointsOfInterestOutside) {
            time = measureTime {
                val result = polytreeContainsPoint(
                    solutionTree,
                    pt
                )
                assertFalse(result, "The polytree indicates it contains a point that it should not contain")
            }
            println("polytreeContainsPoint $pt not in $time")
        }

        for (pt in pointsOfInterestInside) {
            time = measureTime {
                val result = polytreeContainsPoint(
                    solutionTree,
                    pt
                )
                assertTrue(result, "The polytree indicates it does not contain a point that it should contain")
            }
            println("polytreeContainsPoint $pt in $time")
        }
    }

    private fun checkPolytreeFullyContainsChildren(polytree: PolyTree64): Boolean {
        for (p in polytree) {
            val child = p as PolyPath64
            if (child.count > 0 && !polyPathFullyContainsChildren(child)) {
                return false
            }
        }
        return true
    }

    @OptIn(ExperimentalTime::class)
    private fun polyPathFullyContainsChildren(pp: PolyPath64): Boolean {
//        var totalPointChecks: Int = 0
//        var totalTime: Duration = Duration.ZERO

        val ppPolygon: Path64 = pp.polygon!!
        for (c in pp) {
            val child = c as PolyPath64

            for (pt in child.polygon!!) {
                var pointInPolygon: PointInPolygonResult
                val time = measureTime {
                    pointInPolygon = pointInPolygon(pt, ppPolygon)
                }
                pointInPolygonTotalCheckTime += time
                pointInPolygonNumChecks++

//                totalTime += time
//                totalPointChecks++

                if (pointInPolygon === PointInPolygonResult.IsOutside) {
                    return false
                }
            }
            if (child.count > 0 && !polyPathFullyContainsChildren(child)) {
                return false
            }
        }
        //println("${totalTime / totalPointChecks} = time(${totalTime})/point($totalPointChecks)")
        return true
    }

    fun polytreeContainsPoint(pp: PolyTree64, pt: Point64): Boolean {
        var counter = 0
        for (i in 0 until pp.count) {
            val child: PolyPath64 = pp.get(i)
            val tempRef_counter = RefObject(counter)
            polyPathContainsPoint(child, pt, tempRef_counter)
            counter = tempRef_counter.argValue!!
        }
        assertTrue(counter >= 0, "Polytree has too many holes")
        return counter != 0
    }

    fun polyPathContainsPoint(pp: PolyPath64, pt: Point64, counter: RefObject<Int>) {
        if (pointInPolygon(pt, pp.polygon!!) !== PointInPolygonResult.IsOutside) {
            if (pp.isHole) {
                counter.argValue = counter.argValue!! - 1
            } else {
                counter.argValue = counter.argValue!! + 1
            }
        }
        for (i in 0 until pp.count) {
            val child = pp[i]
            polyPathContainsPoint(child, pt, counter)
        }
    }
}

