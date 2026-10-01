dependencies {
    implementation("com.github.javaparser:javaparser-core:3.25.8")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
