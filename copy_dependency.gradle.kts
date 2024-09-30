// apply(from = "copy_dependency.gradle.kts")
// apply from: "repo_maven.gradle"
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

val gradleVersion = parseVersion(gradle.gradleVersion)
val version8_1 = parseVersion("8.1")
println("GradleVersion current: $gradleVersion")

val interfaceMinimalExternalModuleDependency: Class<*>? = classForName(
    "org.gradle.api.artifacts.MinimalExternalModuleDependency"
)
val classDefaultFileCollectionDependency: Class<*>? = classForName(
    "org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency"
)

allprojects {

    fun copyDependency(destDirectory: File) {
        val classAndroidAppPlugin: Class<*>? = classForName(
            "com.android.build.gradle.AppPlugin"
        )

        val classAndroidLibraryPlugin: Class<*>? = classForName(
            "com.android.build.gradle.LibraryPlugin"
        )
        val classpathCfg = project.rootProject.buildscript.configurations.getByName("classpath")
        val agpVersion = parseVersion(
            classpathCfg.dependencies.find { dependency ->
                println("AndroidPlugin find classpath ${dependency.run { "$group:$name:$version" }}")
                if (dependency.name == "com.android.application.gradle.plugin") {
                    return@find true
                } else if (dependency.name == "com.android.library.gradle.plugin") {
                    return@find true
                } else if (dependency.group == "com.android.tools.build" && dependency.name == "gradle") {
                    return@find true
                }
                false
            }?.version ?: gradle.gradleVersion
        )
        println("AndroidPluginVersion $agpVersion")

        val isAndroidApp = project.plugins.any { classAndroidAppPlugin?.isInstance(it) ?: false }
        val isAndroidLibrary = project.plugins.any {
            classAndroidLibraryPlugin?.isInstance(it) ?: false
        }
        println("copyProject: $project, $isAndroidApp, $isAndroidLibrary")

        configurations.all {
            println("configuration: $isCanBeResolved, $isCanBeConsumed, ${this.name}.")

            if (isCanBeResolved && !resolvedConfiguration.hasError()) {
                println("resolvedConfiguration: ${resolvedConfiguration.hasError()}, $resolvedConfiguration")
                val lenientConfiguration = resolvedConfiguration.lenientConfiguration
                println("lenientConfiguration: $lenientConfiguration")

                lenientConfiguration.getArtifacts filter@{
                    if (it is ProjectDependency) {
                        println("ignoreDependency_ProjectDependency. $it")
                        return@filter false
                    }
                    if (it is FileCollectionDependency) {
                        if (classDefaultFileCollectionDependency?.isInstance(it) == true) {
                            println("ignoreDependency_FileCollectionDependency: ${it.files.joinToString()}")
                            return@filter false
                        }
                        val classNpmDependency: Class<*>? = classForName(
                            "org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency"
                        )
                        if (classNpmDependency?.isInstance(it) == true) {
                            println("ignoreDependency_NpmDependency: ${it.files.joinToString()}")
                            return@filter false
                        }
                    }
                    if (it is ExternalModuleDependency) {
                        if (it.toString().startsWith("DefaultExternalModuleDependency")) {
                            return@filter if (agpVersion >= version8_1) {
                                println("ignoreDependency_DefaultExternalModuleDependency: ${it.module}:${it.version}, $it")
                                false
                            } else true
                        }
                        if (it.toString().startsWith("DefaultMinimalDependencyVariant")) {
                            // println("ignoreDependency_DefaultMinimalDependencyVariant: ${it.module}:${it.version}")
                        }
                    }
                    // println("resolvedDependency: $it, ${it::class.java.allSuperClass}")
                    interfaceMinimalExternalModuleDependency?.isInstance(it) ?: true
                }.forEach { artifact ->
                    val cacheDir = artifact.file.parentFile.parentFile

                    val moduleVersionIdentifier = artifact.moduleVersion.id
                    val artifactPath = arrayOf(
                        *moduleVersionIdentifier.group.split(".").toTypedArray(),
                        moduleVersionIdentifier.name, moduleVersionIdentifier.version
                    )
                    val artifactDir = File(destDirectory, artifactPath.joinToString(File.separator))
                    if (!artifactDir.isDirectory) artifactDir.mkdirs()

                    val cacheFiles = cacheDir.walk().filter {
                        it.isFile && it.parentFile.path.contains(moduleVersionIdentifier.group)
                    }
                    println("Cache_Artifact: $name ${artifact.moduleVersion} Files: ${cacheFiles.joinToString()}")
                    cacheFiles.forEach {
                        val dest = File(artifactDir, it.name)
                        if (!dest.isFile) {
                            if (dest.exists()) dest.delete()
                            println("Copy_Artifact: $name ${artifact.moduleVersion} File: $it")
                            it.copyTo(dest)
                        } else {
                            // println("Exists_Artifact: $name ${artifact.moduleVersion} File: $it")
                        }
                    }
                }
            }
        }
    }

    if (project.tasks.findByName("copyDependencyToProject") == null) project.tasks.register("copyDependencyToProject") {
        doLast {
            val dir = project.layout.projectDirectory.file("repository").asFile
            copyDependency(dir)
        }
    }


    if (project.tasks.findByName("copyDependencyToRootProject") == null) project.tasks.register("copyDependencyToRootProject") {
        doLast {
            val dir = rootProject.layout.projectDirectory.file("repository").asFile
            copyDependency(dir)
        }
    }

    if (project.tasks.findByName("copyDependencyToMavenLocal") == null) project.tasks.register("copyDependencyToMavenLocal") {
        doLast {
            val repositoryPath = arrayOf(".m2", "repository").joinToString(File.separator)
            val dir = File(System.getProperty("user.home"), repositoryPath)
            copyDependency(dir)
        }
    }
}
