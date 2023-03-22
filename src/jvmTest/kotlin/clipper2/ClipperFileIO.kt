package clipper2

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.streams.toList


/** Read the given resource as binary data. */
//actual fun readStringResource(
//    resourceName: String
//): List<String> {
//    val resource = ClassLoader.getSystemResourceAsStream(resourceName)!!
//    return BufferedReader(
//        InputStreamReader(
//            resource,
//            StandardCharsets.UTF_8
//        )
//    ).lines().toList()
//}