// The sms delivery plugin. Depends on the plugin API alone; everything else it needs,
// HTTP, JSON and mail, the application hands it through PluginContext.
plugins {
    `java-library`
}

base {
    archivesName = "notify-plugin-sms"
}

dependencies {
    api(project(":plugin-api"))
}

dependencies {
    testImplementation(testFixtures(project(":plugin-api")))
}
