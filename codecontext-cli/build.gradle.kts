plugins {
    application
}

application {
    mainClass.set("com.codecontext.cli.CodeContextLauncher")
    applicationName = "codecontext"
}

dependencies {
    implementation(project(":codecontext-core"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.startScripts {
    doLast {
        val winScript = windowsScript
        if (winScript.exists()) {
            val javaCheck = """
@rem Check standard JDK 21 location first if current JAVA_HOME is incompatible (NFR-S3.1-01)
if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\java.exe"
    goto execute
)
"""
            var text = winScript.readText().lines().map { line ->
                if (line.startsWith("set CLASSPATH=")) {
                    "set CLASSPATH=%APP_HOME%\\lib\\*"
                } else {
                    line
                }
            }.joinToString("\r\n")

            if (text.contains("if defined JAVA_HOME goto findJavaFromJavaHome")) {
                text = text.replace("if defined JAVA_HOME goto findJavaFromJavaHome", javaCheck + "\r\nif defined JAVA_HOME goto findJavaFromJavaHome")
            }
            winScript.writeText(text)
        }
    }
}
