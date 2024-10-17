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
