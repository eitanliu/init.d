// apply(from = "copy_dependency.gradle.kts")
// apply from: "repo_maven.gradle"
fun classForName(className: String, classLoader: ClassLoader? = null): Class<*>? {
    return try {
        if (classLoader != null) {
            Class.forName(className, true, classLoader)
        } else Class.forName(className)
    } catch (e: Throwable) {
        null
    }
}

val classDefaultFileCollectionDependency: Class<*>? = classForName(
    "org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency"
)

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

allprojects {

    fun copyDependency(destDirectory: File) {
        val classAndroidAppPlugin: Class<*>? = classForName(
            "com.android.build.gradle.AppPlugin"
        )

        val classAndroidLibraryPlugin: Class<*>? = classForName(
            "com.android.build.gradle.LibraryPlugin"
        )

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
                            println("ignoreDependency_DefaultExternalModuleDependency: ${it.module}:${it.version}, $it")
                            return@filter false
                        }
                        if (it.toString().startsWith("DefaultMinimalDependencyVariant")) {
                            // println("ignoreDependency_DefaultMinimalDependencyVariant: ${it.module}:${it.version}")
                        }
                    }
                    // println("resolvedDependency: $it, ${it::class.java.allSuperClass}")
                    it is MinimalExternalModuleDependency
                }.forEach { artifact ->
                    val cacheDir = artifact.file.parentFile.parentFile
                    val cacheFiles = cacheDir.walk().filter { it.isFile }

                    val moduleVersionIdentifier = artifact.moduleVersion.id
                    val artifactPath = arrayOf(
                        *moduleVersionIdentifier.group.split(".").toTypedArray(),
                        moduleVersionIdentifier.name, moduleVersionIdentifier.version
                    )
                    val artifactDir = File(destDirectory, artifactPath.joinToString(File.separator))
                    if (!artifactDir.isDirectory) artifactDir.mkdirs()

                    cacheFiles.forEach {
                        val dest = File(artifactDir, it.name)
                        if (!dest.isFile) {
                            if (dest.exists()) dest.delete()
                            println("Copy_Artifact: $name ${artifact.moduleVersion} Files: ${cacheFiles.joinToString()}")
                            it.copyTo(dest)
                        } else {
                            println("Exists_Artifact: $name ${artifact.moduleVersion} Files: ${cacheFiles.joinToString()}")
                        }
                    }
                }
            }
        }
    }

    if (project.tasks.findByName("copyDependencyToBuild") == null) project.tasks.register("copyDependencyToBuild") {
        doLast {
            copyDependency(project.layout.buildDirectory.file("repository").get().asFile)
        }
    }


    if (project.tasks.findByName("copyDependencyToRootBuild") == null) project.tasks.register("copyDependencyToRootBuild") {
        doLast {
            copyDependency(rootProject.layout.buildDirectory.file("repository").get().asFile)
        }
    }

    if (project.tasks.findByName("copyDependencyToMavenLocal") == null) project.tasks.register("copyDependencyToMavenLocal") {
        doLast {
            val repositoryPath = arrayOf(".m2", "repository").joinToString(File.separator)
            copyDependency(File(System.getProperty("user.home"), repositoryPath))
        }
    }
}
