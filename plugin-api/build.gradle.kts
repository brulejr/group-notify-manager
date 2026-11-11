// The delivery plugin contract. Deliberately free of dependencies beyond the Kotlin
// standard library and the JDK, so a plugin built against it drags nothing into the
// application and the application can change its own libraries without breaking plugins.
plugins {
    `java-library`
    // A fake PluginContext, mailer and HTTP server, for testing a plugin without the
    // application: testImplementation(testFixtures(project(":plugin-api"))).
    `java-test-fixtures`
}

base {
    archivesName = "notify-plugin-api"
}

dependencies {
    // JSON for the fake context and for tests reading what a plugin sent; the API itself has none.
    testFixturesApi("tools.jackson.core:jackson-databind")
}
