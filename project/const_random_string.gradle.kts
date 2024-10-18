/**
 * `constSaveHistory` 固化当前字符串
 * `constRandomString` 替换随机字符串
 *
 * Linux/Mac
 * ```bat
 * ./gradlew :constSaveHistory
 * ./gradlew :constRandomString
 * ```
 * Windows
 * ```bat
 * .\gradlew.bat :constSaveHistory
 * .\gradlew.bat :constRandomString
 * ```
 */

fun Project?.projProp(key: String): String? =
    this?.findProperty(key)?.toString() ?: System.getProperty(key)


val history = LinkedHashMap<String, String>()
val historyFile = rootProject.file("const_history.cfg")
val regex = """((?:.+))=\s?"(.*)"((?:.+)?)""".toRegex()

val ignoreKeys = projProp("const_ignore")?.split(",") ?: emptyList()

fun readHistory(file: File, history: MutableMap<String, String>) {
    if (file.isFile) {
        val lines = file.readLines()
        val list = lines.mapNotNull map@{ line ->
            // println("${line}")
            // if (!regex.matches(line)) return@map null
            val result = regex.find(line) ?: return@map null
            val left = result.groups[1]?.value ?: return@map null
            val key = left.trim()
            if (key.startsWith("//")) return@map null
            if (ignoreKeys.any { key == it }) return@map null
            val value = result.groups[2]?.value?.trim()?.trim('"') ?: return@map null
            key to value
        }
        list.toMap(history)
    }
}

fun randomString(length: Int, history: MutableMap<String, String>): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    val value = (1..length)
        .map { chars.random() }
        .joinToString("")
    if (value in history.values) return randomString(length, history)
    return value
}

fun replaceString(inFile: File, outFile: File, history: MutableMap<String, String>) {
    if (inFile.isFile) {
        val lines = inFile.readLines()
        val list = lines.map map@{ line ->
            if (!regex.matches(line)) return@map line
            val result = regex.find(line) ?: return@map line
            val left = result.groups[1]?.value ?: return@map line
            val key = left.trim()
            if (key.startsWith("//")) return@map line
            if (ignoreKeys.any { key == it }) return@map line
            val comments = result.groups[3]?.value.orEmpty()
            val value = history[key] ?: randomString(10, history)
            history[key] = value
            "$left= \"$value\"$comments"
        }
        outFile.parentFile?.apply { if (!isDirectory) mkdirs() }
        outFile.writeText(list.joinToString(System.lineSeparator()))
    }

}

fun constRandomString() {
    readHistory(historyFile, history)

    val files = projProp("const_files")?.split(",")
    files?.forEach { path ->
        val inFile = file(path)
        replaceString(inFile, inFile, history)
    }

    historyFile.writeText(history.toList().joinToString(System.lineSeparator()) { entry ->
        println("${entry.first}, ${entry.second}")
        "${entry.first} = \"${entry.second}\""
    })
}

fun constSaveHistory() {

    readHistory(historyFile, history)
    val files = projProp("const_files")?.split(",")
    files?.forEach { path ->
        val inFile = file(path)
        readHistory(inFile, history)
    }

    historyFile.writeText(history.toList().joinToString(System.lineSeparator()) { entry ->
        println("${entry.first}, ${entry.second}")
        "${entry.first} = \"${entry.second}\""
    })
}

tasks.apply {
    if (tasks.findByName("constRandomString") == null)
        register("constRandomString") { doLast { constRandomString() } }
    if (tasks.findByName("constSaveHistory") == null)
        register("constSaveHistory") { doLast { constSaveHistory() } }
}
