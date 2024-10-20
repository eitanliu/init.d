import org.gradle.util.GradleVersion

fun parseVersion(version: String): Comparable<Comparable<*>> {
    @Suppress("UNCHECKED_CAST")
    return try {
        GradleVersion.version(version) as Comparable<Comparable<*>>
    } catch (_: Throwable) {
        version as Comparable<Comparable<*>>
    }
}

fun classForName(className: String, classLoader: ClassLoader? = null): Class<*>? {
    return try {
        if (classLoader != null) {
            Class.forName(className, true, classLoader)
        } else Class.forName(className)
    } catch (e: Throwable) {
        null
    }
}

val Class<*>.allSuperClass: List<Class<*>>
    get() {
        val classList = arrayListOf<Class<*>>(*interfaces)
        var clazz: Class<*>? = this
        while (clazz?.superclass.also { clazz = it } != null) {
            classList.add(clazz!!)
            classList.addAll(clazz!!.interfaces)
        }
        return classList
    }

fun systemEnv(key: String): String? = System.getenv(key)

fun projProp(key: String, project: Project? = null): String =
    project?.findProperty(key)?.toString() ?: System.getProperty(key)

val constRandomString = file("project/const_random_string.gradle.kts")

allprojects {
    println("<<< Project allprojects $project >>>")
}

gradle.beforeProject {
    println("<<< Project beforeProject $project has been configured >>>")
}

gradle.afterProject {
    println("<<< Project afterProject ${project.name} has been configured >>>")
    println("$project apply (from = \"$constRandomString\")")
    project.apply(from = constRandomString)
}

gradle.projectsEvaluated {
    println("<<< All projects have been evaluated >>>")
}
