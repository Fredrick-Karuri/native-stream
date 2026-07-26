plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.29.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.29.0")
    implementation("com.google.protobuf:protobuf-java-util:4.29.0")
}

sourceSets {
    main {
        java.srcDirs("com")
        kotlin.srcDirs("com")
    }
}