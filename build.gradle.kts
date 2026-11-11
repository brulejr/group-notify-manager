import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

allprojects {
    group = "io.brulejr"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// The plugin API and the plugins are plain Kotlin libraries: no Spring, but the same
// toolchain, and Spring Boot's dependency versions so their tests run on the same JUnit.
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.spring.dependency-management")

    the<DependencyManagementExtension>().imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }

    the<JavaPluginExtension>().toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    dependencies {
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":plugin-api"))
    // The bundled plugins, found through ServiceLoader rather than referenced from code.
    runtimeOnly(project(":plugins:discord"))
    runtimeOnly(project(":plugins:groupsio"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Authelia: forward-auth headers by default, or OIDC sign-in; see SecurityConfig.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // An optional SMTP connection, offered to plugins that send mail as a Mailer (none of the
    // bundled ones do).
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // The console: server-rendered pages, made interactive with htmx.
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    // Lets pages name a WebJar's files without its version: /webjars/htmx.org/dist/htmx.min.js.
    implementation("org.webjars:webjars-locator-lite")
    implementation(libs.webjars.bootstrap)
    implementation(libs.webjars.bootstrap.icons)
    implementation(libs.webjars.htmx)
    // The editors' dist bundles carry their own dependencies; the npm ones they declare,
    // with open version ranges, are never loaded and would only slow resolution.
    implementation(libs.webjars.easymde) { isTransitive = false }
    implementation(libs.webjars.quill) { isTransitive = false }
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.jsoup)
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Flyway without Spring Boot's auto-configuration, which would migrate at startup
    // even with every store in memory; DatabaseMigrator runs it only when needed.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // The plugin API's fake context and sample message.
    testImplementation(testFixtures(project(":plugin-api")))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // A throwaway PostgreSQL for the SQL-backed stores; skipped without Docker.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The container image copies this one jar, so it has a fixed name and no plain-jar
// sibling to choose between.
tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "group-notify-manager.jar"
    // Development settings, with their credentials, stay with the source tree.
    exclude("application-local.yml")
    // PropertiesLauncher rather than JarLauncher, so `loader.path` can add plugin jars
    // from a directory at startup; see docs/plugins.md.
    manifest {
        attributes("Main-Class" to "org.springframework.boot.loader.launch.PropertiesLauncher")
    }
}
