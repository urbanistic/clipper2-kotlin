package clipper2

import clipper2.core.*

///** Read the resource as Strings. */
//expect fun readStringResource(
//    resourceName: String
//): List<String>

object ClipperFileIO {
    fun loadTestCases(testFileName: String): List<TestCase> {
        //var lines: MutableList<String> = readStringResource(testFileName).toMutableList() // MutableList<String> = Files.readAllLines(Paths.get(String.format("src/test/resources/%s", testFileName)))
        val str = testTextMap[testFileName] ?: return listOf()
        val lines = str.lines().toMutableList()

        lines.add("")
        var caption = ""
        var ct = ClipType.None
        var fillRule = FillRule.EvenOdd
        var area: Long = 0
        var count = 0
        var GetIdx = 0
        val subj = Paths64()
        val subj_open = Paths64()
        val clip = Paths64()

        val cases: MutableList<TestCase> = mutableListOf<TestCase>()

        for (s in lines) {
            if (s.matches("\\s*".toRegex())) {
                if (GetIdx != 0) {
                    cases.add(
                        TestCase(
                            caption,
                            ct,
                            fillRule,
                            area,
                            count,
                            GetIdx,
                            Paths64.of(subj),
                            Paths64.of(subj_open),
                            Paths64.of(clip),
                            cases.size + 1
                        )
                    )
                    subj.clear()
                    subj_open.clear()
                    clip.clear()
                    GetIdx = 0
                }
                continue
            }
            if (s.indexOf("CAPTION: ") == 0) {
                caption = s.substring(9)
                continue
            }
            if (s.indexOf("CLIPTYPE: ") == 0) {
                ct = if (s.indexOf("INTERSECTION") > 0) {
                    ClipType.Intersection
                } else if (s.indexOf("UNION") > 0) {
                    ClipType.Union
                } else if (s.indexOf("DIFFERENCE") > 0) {
                    ClipType.Difference
                } else {
                    ClipType.Xor
                }
                continue
            }
            if (s.indexOf("FILLTYPE: ") == 0 || s.indexOf("FILLRULE: ") == 0) {
                fillRule = if (s.indexOf("EVENODD") > 0) {
                    FillRule.EvenOdd
                } else if (s.indexOf("POSITIVE") > 0) {
                    FillRule.Positive
                } else if (s.indexOf("NEGATIVE") > 0) {
                    FillRule.Negative
                } else {
                    FillRule.NonZero
                }
                continue
            }
            if (s.indexOf("SOL_AREA: ") == 0) {
                area = s.substring(10).toLong()
                continue
            }
            if (s.indexOf("SOL_COUNT: ") == 0) {
                count = s.substring(11).toInt()
                continue
            }
            if (s.indexOf("SUBJECTS_OPEN") == 0) {
                GetIdx = 2
                continue
            } else if (s.indexOf("SUBJECTS") == 0) {
                GetIdx = 1
                continue
            } else if (s.indexOf("CLIPS") == 0) {
                GetIdx = 3
                continue
            } else {
//				continue;
            }
            val paths = PathFromStr(s) // 0 or 1 path
            if (paths.isNullOrEmpty()) {
                if (GetIdx == 3) {
//					return result;
                }
                if (s.indexOf("SUBJECTS_OPEN") == 0) {
                    GetIdx = 2
                } else if (s.indexOf("CLIPS") == 0) {
                    GetIdx = 3
                } else {
//					return result;
                }
                continue
            }
            if (GetIdx == 1 && !paths[0].isEmpty()) {
                subj.add(paths[0])
            } else if (GetIdx == 2) {
                subj_open.add(paths[0])
            } else {
                clip.add(paths[0])
            }
        }
        return cases
    }

    fun loadTestCases32(testFileName: String): List<TestCase32> {
        //var lines: MutableList<String> = readStringResource(testFileName).toMutableList() // MutableList<String> = Files.readAllLines(Paths.get(String.format("src/test/resources/%s", testFileName)))
        val str = testTextMap[testFileName] ?: return listOf()
        val lines = str.lines().toMutableList()

        lines.add("")
        var caption = ""
        var ct = ClipType.None
        var fillRule = FillRule.EvenOdd
        var area: Long = 0
        var count = 0
        var GetIdx = 0
        val subj = Paths32()
        val subj_open = Paths32()
        val clip = Paths32()

        val cases: MutableList<TestCase32> = mutableListOf<TestCase32>()

        for (s in lines) {
            if (s.matches("\\s*".toRegex())) {
                if (GetIdx != 0) {
                    cases.add(
                        TestCase32(
                            caption,
                            ct,
                            fillRule,
                            area,
                            count,
                            GetIdx,
                            Paths32.of(subj),
                            Paths32.of(subj_open),
                            Paths32.of(clip),
                            cases.size + 1
                        )
                    )
                    subj.clear()
                    subj_open.clear()
                    clip.clear()
                    GetIdx = 0
                }
                continue
            }
            if (s.indexOf("CAPTION: ") == 0) {
                caption = s.substring(9)
                continue
            }
            if (s.indexOf("CLIPTYPE: ") == 0) {
                ct = if (s.indexOf("INTERSECTION") > 0) {
                    ClipType.Intersection
                } else if (s.indexOf("UNION") > 0) {
                    ClipType.Union
                } else if (s.indexOf("DIFFERENCE") > 0) {
                    ClipType.Difference
                } else {
                    ClipType.Xor
                }
                continue
            }
            if (s.indexOf("FILLTYPE: ") == 0 || s.indexOf("FILLRULE: ") == 0) {
                fillRule = if (s.indexOf("EVENODD") > 0) {
                    FillRule.EvenOdd
                } else if (s.indexOf("POSITIVE") > 0) {
                    FillRule.Positive
                } else if (s.indexOf("NEGATIVE") > 0) {
                    FillRule.Negative
                } else {
                    FillRule.NonZero
                }
                continue
            }
            if (s.indexOf("SOL_AREA: ") == 0) {
                area = s.substring(10).toLong()
                continue
            }
            if (s.indexOf("SOL_COUNT: ") == 0) {
                count = s.substring(11).toInt()
                continue
            }
            if (s.indexOf("SUBJECTS_OPEN") == 0) {
                GetIdx = 2
                continue
            } else if (s.indexOf("SUBJECTS") == 0) {
                GetIdx = 1
                continue
            } else if (s.indexOf("CLIPS") == 0) {
                GetIdx = 3
                continue
            } else {
//				continue;
            }
            val paths = Path32FromStr(s) // 0 or 1 path
            if (paths.isNullOrEmpty()) {
                if (GetIdx == 3) {
//					return result;
                }
                if (s.indexOf("SUBJECTS_OPEN") == 0) {
                    GetIdx = 2
                } else if (s.indexOf("CLIPS") == 0) {
                    GetIdx = 3
                } else {
//					return result;
                }
                continue
            }
            if (GetIdx == 1 && !paths[0].isEmpty()) {
                subj.add(paths[0])
            } else if (GetIdx == 2) {
                subj_open.add(paths[0])
            } else {
                clip.add(paths[0])
            }
        }
        return cases
    }

    fun PathFromStr(s: String?): Paths64 {
        if (s == null) {
            return Paths64()
        }
        var p = Path64()
        val pp = Paths64()
        val len = s.length
        var i = 0
        var j: Int
        while (i < len) {
            var isNeg: Boolean
            while (s[i].code < 33 && i < len) {
                i++
            }
            if (i >= len) {
                break
            }
            // get X ...
            isNeg = s[i].code == 45
            if (isNeg) {
                i++
            }
            if (i >= len || s[i].code < 48 || s[i].code > 57) {
                break
            }
            j = i + 1
            while (j < len && s[j].code > 47 && s[j].code < 58) {
                j++
            }
            var x = LongTryParse(s.substring(i, j)) ?: break
            if (isNeg) {
                x = -x
            }
            // skip space or comma between X & Y ...
            i = j
            while (i < len && (s[i].code == 32 || s[i].code == 44)) {
                i++
            }
            // get Y ...
            if (i >= len) {
                break
            }
            isNeg = s[i].code == 45
            if (isNeg) {
                i++
            }
            if (i >= len || s[i].code < 48 || s[i].code > 57) {
                break
            }
            j = i + 1
            while (j < len && s[j].code > 47 && s[j].code < 58) {
                j++
            }
            var y = LongTryParse(s.substring(i, j)) ?: break
            if (isNeg) {
                y = -y
            }
            p.add(Point64(x, y))
            // skip trailing space, comma ...
            i = j
            var nlCnt = 0
            while (i < len && (s[i].code < 33 || s[i].code == 44)) {
                if (i >= len) {
                    break
                }
                if (s[i].code == 10) {
                    nlCnt++
                    if (nlCnt == 2) {
                        if (p.size > 0) {
                            pp.add(p)
                        }
                        p = Path64()
                    }
                }
                i++
            }
        }
        if (p.size > 0) {
            pp.add(p)
        }
        return pp
    }

    fun Path32FromStr(s: String?): Paths32 {
        if (s == null) {
            return Paths32()
        }
        var p = Path32()
        val pp = Paths32()
        val len = s.length
        var i = 0
        var j: Int
        while (i < len) {
            var isNeg: Boolean
            while (s[i].code < 33 && i < len) {
                i++
            }
            if (i >= len) {
                break
            }
            // get X ...
            isNeg = s[i].code == 45
            if (isNeg) {
                i++
            }
            if (i >= len || s[i].code < 48 || s[i].code > 57) {
                break
            }
            j = i + 1
            while (j < len && s[j].code > 47 && s[j].code < 58) {
                j++
            }
            var x = IntTryParse(s.substring(i, j)) ?: break
            if (isNeg) {
                x = -x
            }
            // skip space or comma between X & Y ...
            i = j
            while (i < len && (s[i].code == 32 || s[i].code == 44)) {
                i++
            }
            // get Y ...
            if (i >= len) {
                break
            }
            isNeg = s[i].code == 45
            if (isNeg) {
                i++
            }
            if (i >= len || s[i].code < 48 || s[i].code > 57) {
                break
            }
            j = i + 1
            while (j < len && s[j].code > 47 && s[j].code < 58) {
                j++
            }
            var y = IntTryParse(s.substring(i, j)) ?: break
            if (isNeg) {
                y = -y
            }
            p.add(Point32(x, y))
            // skip trailing space, comma ...
            i = j
            var nlCnt = 0
            while (i < len && (s[i].code < 33 || s[i].code == 44)) {
                if (i >= len) {
                    break
                }
                if (s[i].code == 10) {
                    nlCnt++
                    if (nlCnt == 2) {
                        if (p.size > 0) {
                            pp.add(p)
                        }
                        p = Path32()
                    }
                }
                i++
            }
        }
        if (p.size > 0) {
            pp.add(p)
        }
        return pp
    }

    private fun LongTryParse(s: String): Long? {
        return try {
            s.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun IntTryParse(s: String): Int? {
        return try {
            s.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    class TestCase(
        val caption: String?,
        val clipType: ClipType?,
        val fillRule: FillRule?,
        val area: Long,
        val count: Int,
        val GetIdx: Int,
        val subj: Paths64,
        val subj_open: Paths64,
        val clip: Paths64,
        val testNum: Int
    )

    class TestCase32(
        val caption: String?,
        val clipType: ClipType?,
        val fillRule: FillRule?,
        val area: Long,
        val count: Int,
        val GetIdx: Int,
        val subj: Paths32,
        val subj_open: Paths32,
        val clip: Paths32,
        val testNum: Int
    )
}