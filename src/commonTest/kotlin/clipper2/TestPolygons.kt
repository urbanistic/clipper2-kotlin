package clipper2

import Clipper
import clipper2.core.Paths64
import clipper2.engine.Clipper64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

//class MyPolygonTests : FunSpec(){
//    var counter = 0
//    val data = ClipperFileIO.loadTestCases("Polygons.txt").associateBy { it: ClipperFileIO.TestCase -> it.caption ?: ("PolygonsTest:" + counter++) }
//
//    //println("Running Polygon Tests: " + data.size)
//
//    init {
//        withData(
//            data
//        ) {
//            runPolygonsTestCaseKotest(it)
//        }
//    }
//}

//fun runPolygonsTestCaseKotest(test: ClipperFileIO.TestCase) {
//    print(test.testNum)
//
//    val c64 = Clipper64()
//    val solution = Paths64()
//    val solutionOpen = Paths64()
//    c64.addSubjects(test.subj)
//    c64.addOpenSubjects(test.subj_open)
//    c64.addClips(test.clip)
//    c64.execute(test.clipType!!, test.fillRule!!, solution, solutionOpen)
//    val measuredCount: Int = solution.size
//    val measuredArea = Clipper.area(solution).toLong()
//    val storedCount: Int = test.count
//    val storedArea: Long = test.area
//    val countDiff = if (storedCount > 0) abs(storedCount - measuredCount) else 0
//    val areaDiff: Long = if (storedArea > 0) abs(storedArea - measuredArea) else 0
//    val areaDiffRatio: Double = if (storedArea <= 0) {
//        0.0
//    } else {
//        areaDiff.toDouble() / storedArea
//    }
//
//    println(" adr:$areaDiffRatio sc:$storedCount")
//
//    // check polygon counts
//    if (storedCount > 0) {
//        if (listOf(140, 150, 165, 166, 172, 173, 176, 177, 179).contains(test.testNum)) {
//            (countDiff <= 9) shouldBe true
//        } else if (test.testNum >= 120) {
//            (countDiff <= 6) shouldBe true
//        } else if (listOf(27, 121, 126).contains(test.testNum)) {
//            (countDiff <= 2) shouldBe true
//        } else if (listOf(23, 37, 43, 45, 87, 102, 111, 118, 119).contains(test.testNum)) {
//            (countDiff <= 1) shouldBe true
//        } else {
//            (countDiff == 0) shouldBe true
//        }
//    }
//
//    // check polygon areas
//    if (storedArea > 0) {
//        if (listOf(19, 22, 23, 24).contains(test.testNum)) {
//            (areaDiffRatio <= 0.5) shouldBe true
//        } else if (test.testNum == 193) {
//            (areaDiffRatio <= 0.25) shouldBe true
//        } else if (test.testNum == 63) {
//            (areaDiffRatio <= 0.1) shouldBe true
//        } else if (test.testNum == 16) {
//            (areaDiffRatio <= 0.075) shouldBe true
//        } else if (listOf(15, 26).contains(test.testNum)) {
//            (areaDiffRatio <= 0.05) shouldBe true
//        } else if (listOf(52, 53, 54, 59, 60, 117, 118, 119, 184).contains(test.testNum)) {
//            (areaDiffRatio <= 0.02) shouldBe true
//        } else if (listOf(64, 66).contains(test.testNum)) { // maybe different rounding?
//            (areaDiffRatio <= 0.03) shouldBe true
//        } else {
//            (areaDiffRatio <= 0.01) shouldBe true
//        }
//    }
//}

public class TestPolygons {
    @OptIn(ExperimentalTime::class)
    @Test
    fun runTests() {
        var counter = 0
        val data = ClipperFileIO.loadTestCases("Polygons.txt")
        val dataMap = mutableMapOf<String, ClipperFileIO.TestCase>()

        for (testcase in data) {
            var label = testcase.caption ?: ("PolygonTest:" + counter++)
            if (label.isEmpty()) {
                label = "PolygonTest:" + counter++
            }
            dataMap[label] = testcase
        }

        println("Running PolygonTests")
        val time = measureTime {
            for(test in dataMap){
                print(" ${test.key} ${test.value.clipType} ${test.value.fillRule}")
                runPolygonsTestCase(test.value)
            }
        }
        println("finished in $time")
    }

    private fun runPolygonsTestCase(test: ClipperFileIO.TestCase) {
        print(test.testNum)

        val c64 = Clipper64()
        val solution = Paths64()
        val solutionOpen = Paths64()
        c64.addSubjects(test.subj)
        c64.addOpenSubjects(test.subj_open)
        c64.addClips(test.clip)
        c64.execute(test.clipType!!, test.fillRule!!, solution, solutionOpen)
        val measuredCount: Int = solution.size
        val measuredArea = Clipper.area(solution).toLong()
        val storedCount: Int = test.count
        val storedArea: Long = test.area
        val countDiff = if (storedCount > 0) abs(storedCount - measuredCount) else 0
        val areaDiff: Long = if (storedArea > 0) abs(storedArea - measuredArea) else 0
        val areaDiffRatio: Double = if (storedArea <= 0) {
            0.0
        } else {
            areaDiff.toDouble() / storedArea
        }

        println(" adr:$areaDiffRatio sc:$storedCount")

        // check polygon counts
        if (storedCount > 0) {
            if (listOf(140, 150, 165, 166, 172, 173, 176, 177, 179).contains(test.testNum)) {
                assertTrue(countDiff <= 9)
            } else if (test.testNum >= 120) {
                assertTrue(countDiff <= 6)
            } else if (listOf(27, 121, 126).contains(test.testNum)) {
                assertTrue(countDiff <= 2)
            } else if (listOf(23, 37, 43, 45, 87, 102, 111, 118, 119).contains(test.testNum)) {
                assertTrue(countDiff <= 1)
            } else {
                assertTrue(countDiff == 0)
            }
        }

        // check polygon areas
        if (storedArea > 0) {
            if (listOf(19, 22, 23, 24).contains(test.testNum)) {
                assertTrue(areaDiffRatio <= 0.5)
            } else if (test.testNum == 193) {
                assertTrue(areaDiffRatio <= 0.25)
            } else if (test.testNum == 63) {
                assertTrue(areaDiffRatio <= 0.1)
            } else if (test.testNum == 16) {
                assertTrue(areaDiffRatio <= 0.075)
            } else if (listOf(15, 26).contains(test.testNum)) {
                assertTrue(areaDiffRatio <= 0.05)
            } else if (listOf(52, 53, 54, 59, 60, 117, 118, 119, 184).contains(test.testNum)) {
                assertTrue(areaDiffRatio <= 0.02)
            } else if (listOf(64, 66).contains(test.testNum)) { // maybe different rounding?
                assertTrue(areaDiffRatio <= 0.03)
            } else {
                assertTrue(areaDiffRatio <= 0.01)
            }
        }
    }
}