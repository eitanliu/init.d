import org.gradle.util.GradleVersion

Closure<Comparable> parseVersion = { String version ->
    try {
        return (Comparable) GradleVersion.version(version)
    } catch (Throwable ignored) {
    }
    return version
}

Closure<Class> classForName = { String className, ClassLoader classLoader = null ->
    try {
        return classLoader != null ? Class.forName(className, true, classLoader) : Class.forName(className)
    } catch (Throwable e) {
        e.printStackTrace()
    }
    return null
}

static List<Class> allSuperClass(Class clazz) {
    def classList = new ArrayList<Class>()
    classList.addAll(clazz.interfaces)
    while ((clazz = clazz.superclass) != null) {
        classList.add(clazz)
        classList.addAll(clazz.interfaces)
    }
    return classList
}

Closure<String> systemEnv = { String key ->
    return System.getenv(key)
}

Closure<String> projProp = { String key, Project project = null ->
    return project?.findProperty(key)?.toString() ?: System.getProperty(key)
}
