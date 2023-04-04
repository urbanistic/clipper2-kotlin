package clipper2

import Clipper
import clipper2.clipper32.core.Paths32
import clipper2.clipper32.engine.Clipper32
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

public class TestPolygons32 {
    val iterations = 1

    @OptIn(ExperimentalTime::class)
    @Test
    fun runTests32() {
        var counter = 0
        val data = ClipperFileIO.loadTestCases32("Polygons.txt")
        val dataMap = mutableMapOf<String, ClipperFileIO.TestCase32>()

        for (testcase in data) {
            var label = testcase.caption ?: ("PolygonTest:" + counter++)
            if (label.isEmpty()) {
                label = "PolygonTest:" + counter++
            }
            label += "(32)"

            dataMap[label] = testcase
        }

        for (test in dataMap) {
            print(" ${test.key} ${test.value.clipType} ${test.value.fillRule}")
            val time2 = measureTime {
                for (i in 0 until iterations) {
                    runPolygonsTestCase32(test.value)
                }
            }
            println(" -> passed in $time2")
        }
    }

    private fun runPolygonsTestCase32(test: ClipperFileIO.TestCase32) {
        val c32 = Clipper32()
        val solution = Paths32()
        val solutionOpen = Paths32()
        c32.addSubjects(test.subj)
        c32.addOpenSubjects(test.subj_open)
        c32.addClips(test.clip)
        c32.execute(test.clipType!!, test.fillRule!!, solution, solutionOpen)
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

        // println(" adr:$areaDiffRatio sc:$storedCount")

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
