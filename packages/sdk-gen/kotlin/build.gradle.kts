plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.29.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.29.0")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        java.srcDirs("com")
        kotlin.srcDirs("com")
    }
}