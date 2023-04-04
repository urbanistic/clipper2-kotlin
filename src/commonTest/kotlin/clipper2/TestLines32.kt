package clipper2

import Clipper
import clipper2.clipper32.core.Paths32
import clipper2.core.Paths64
import clipper2.clipper32.engine.Clipper32
import clipper2.engine.Clipper64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

//class MyLineTests : FunSpec() {
//    var counter = 0
//    val data = loadTestCases("Lines.txt").associateBy { it: ClipperFileIO.TestCase -> it.caption ?: ("LinesTest:" + counter++) }
//
//    init {
//        withData(
//            data
//        ) {
//            runLinesTestCaseKotest(it)
//        }
//    }
//}

//fun runLinesTestCaseKotest(test: ClipperFileIO.TestCase) {
//    val c64 = Clipper64()
//    val solution = Paths64()
//    val solution_open = Paths64()
//    c64.addSubjects(test.subj)
//    c64.addOpenSubjects(test.subj_open)
//    c64.addClips(test.clip)
//    c64.execute(test.clipType!!, test.fillRule!!, solution, solution_open)
//    if (test.area > 0) {
//        val area2: Double = Clipper.area(solution)
//        test.area.toDouble() shouldBe area2.plusOrMinus(test.area * 0.005)
//    }
//    if (test.count > 0 && abs(solution.size - test.count) > 0) {
//        (abs(solution.size - test.count) < 2) shouldBe true // "Vertex count incorrect. Difference=${solution.size - test.count()}"
//    }
//}

public class TestLines32 {
    val iterations = 1

    @OptIn(ExperimentalTime::class)
    @Test
    fun runTests32() {
        var counter = 0
        val data = ClipperFileIO.loadTestCases32("Lines.txt")
        val dataMap = mutableMapOf<String, ClipperFileIO.TestCase32>()

        for (testcase in data) {
            var label = testcase.caption ?: ("LinesTest:" + counter++)
            if (label.isEmpty()) {
                label = "LinesTest:" + counter++
            }
            label += "(32)"

            dataMap[label] = testcase
        }

        for(test in dataMap){
            print("${test.key} ${test.value.clipType} ${test.value.fillRule}")
            val time = measureTime {
                for(i in 0 until iterations) {
                    runLinesTestCase32(test.value)
                }
            }
            println(" -> passed in $time")
        }
    }

    fun runLinesTestCase32(test: ClipperFileIO.TestCase32) {
        val c32 = Clipper32()
        val solution = Paths32()
        val solution_open = Paths32()
        c32.addSubjects(test.subj)
        c32.addOpenSubjects(test.subj_open)
        c32.addClips(test.clip)
        c32.execute(test.clipType!!, test.fillRule!!, solution, solution_open)
        if (test.area > 0) {
            val area2: Double = Clipper.area(solution)
            assertEquals(test.area.toDouble(), area2, test.area.toDouble() * 0.005)
        }
        if (test.count > 0 && abs(solution.size - test.count) > 0) {
            assertTrue(abs(solution.size - test.count) < 2, "Vertex count incorrect. Difference=${solution.size - test.count}")
        }
    }
}