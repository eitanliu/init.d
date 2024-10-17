val constRandomString = file("project/const_random_string.gradle.kts")

allprojects {
    println("<<< Project allprojects $project >>>")
}

gradle.beforeProject {
    println("<<< Project beforeProject $project has been configured >>>")
    println("$project apply (from = \"$constRandomString\")")
    project.apply(from = constRandomString)
}

gradle.afterProject {
    // 每个项目配置完成后执行
    println("<<< Project afterProject ${project.name} has been configured >>>")
}

gradle.projectsEvaluated {
    // 所有项目配置完成后执行
    println("<<< All projects have been evaluated >>>")
}
