// apply(from = "copy_dependency.gradle.kts")
// apply from: "repo_maven.gradle"
// import org.gradle.api.internal.plugins.PluginManagerInternal
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

    fun copyDependency(
        project: Project, destDirectory: File, plugin: Plugin<*>?,
        agpVersion: Comparable<Comparable<*>>?, tag: String = "project"
    ) {
        val ignoreDefault = (project.findProperty(
            "copy_dependency_ignore_default"
        )?.toString() ?: "false").toBoolean()

        val container = if (tag == "project") {
            project.configurations
        } else {
            project.buildscript.configurations
        }
        val version = plugin?.let { _ ->
            // val pluginId = plugin.javaClass.name
            // val pm = project.pluginManager as PluginManagerInternal
            // val pluginId = pm.findPluginIdForClass(plugin::class.java).get()
            // println("PluginId: $plugin, ${pluginId.id}")
            val pluginVersion = plugin.javaClass.classLoader
                // .getResource("META-INF/gradle-plugins/${pluginId.id}.properties")
                .getResource("META-INF/MANIFEST.MF")
                ?.readText()
                // ?.let { text -> Regex("version=(.*)").find(text)?.groupValues?.get(1) }
                ?.let { text ->
                    Regex("Plugin\\-Version\\:(.*)").find(text)?.groupValues?.get(1)?.trim()
                }
                ?: try {
                    val versionField = plugin.javaClass.getDeclaredField("VERSION")
                    versionField.isAccessible = true
                    versionField.get(null) as? String
                } catch (_: Throwable) {
                    null
                }
            pluginVersion
        }
        println("PluginVersion: $project $version $plugin")
        container.all {
            println("$tag.configuration: $name, $isCanBeResolved, $isCanBeConsumed")

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
                            return@filter if (ignoreDefault
                                && agpVersion != null && agpVersion >= version8_1
                            ) {
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
                    val moduleVersionId = artifact.moduleVersion.id
                    val ignoreList = listOf("org.jetbrains.kotlin:kotlin-native-prebuilt")
                    val moduleName = moduleVersionId.module.toString()
                    if (moduleName in ignoreList) {
                        println("ignoreArtifactModule: $moduleVersionId")
                        return@forEach
                    }
                    val artifactPath = arrayOf(
                        *moduleVersionId.group.split(".").toTypedArray(),
                        moduleVersionId.name, moduleVersionId.version
                    )

                    val artifactDir = File(destDirectory, artifactPath.joinToString(File.separator))
                    if (!artifactDir.isDirectory) artifactDir.mkdirs()

                    val cacheDir = artifact.file.parentFile?.parentFile
                    val cacheFiles = cacheDir?.walk()?.filter {
                        it.isFile && it.parentFile.path.contains(moduleVersionId.group)
                    }
                    println("Find_Artifact: $name ${artifact.moduleVersion} Files: ${cacheFiles?.joinToString()}")
                    cacheFiles?.forEach {
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

    fun copyDependency(project: Project, destDirectory: File) {
        val classAndroidApp = project.plugins.find {
            it::class.java.name == "com.android.build.gradle.AppPlugin"
        }
        val classAndroidLibrary = project.plugins.find {
            it::class.java.name == "com.android.build.gradle.LibraryPlugin"
        }
        val plugin = classAndroidApp ?: classAndroidLibrary
        val classpathCfg = project.rootProject.buildscript.configurations.getByName("classpath")
        val agpVersion = classpathCfg.dependencies.find { dependency ->
            println("AndroidPlugin find classpath ${dependency.run { "$group:$name:$version" }}")
            if (dependency.name == "com.android.application.gradle.plugin") {
                return@find true
            } else if (dependency.name == "com.android.library.gradle.plugin") {
                return@find true
            } else if (dependency.group == "com.android.tools.build" && dependency.name == "gradle") {
                return@find true
            }
            false
        }?.version?.let { parseVersion(it) }
        println("AndroidPlugin $agpVersion")
        println("copyProject: $project, $plugin")
        copyDependency(project, destDirectory, plugin, agpVersion)
        copyDependency(project, destDirectory, plugin, agpVersion, "buildscript")
    }

    if (project.tasks.findByName("copyDependencyToProject") == null) project.tasks.register("copyDependencyToProject") {
        doLast {
            val dir = project.layout.projectDirectory.file("repository").asFile
            copyDependency(project, dir)
        }
    }

    if (project.tasks.findByName("copyDependencyToRootProject") == null) project.tasks.register("copyDependencyToRootProject") {
        doLast {
            val dir = rootProject.layout.projectDirectory.file("repository").asFile
            copyDependency(project, dir)
        }
    }

    if (project.tasks.findByName("copyDependencyToMavenLocal") == null) project.tasks.register("copyDependencyToMavenLocal") {
        doLast {
            val repositoryPath = arrayOf(".m2", "repository").joinToString(File.separator)
            val dir = File(System.getProperty("user.home"), repositoryPath)
            copyDependency(project, dir)
        }
    }
}
