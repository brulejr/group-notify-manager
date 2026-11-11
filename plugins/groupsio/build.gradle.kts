// The groupsio delivery plugin. Depends on the plugin API alone; everything else it needs,
// HTTP, JSON and mail, the application hands it through PluginContext.
plugins {
    `java-library`
}

base {
    archivesName = "notify-plugin-groupsio"
}

dependencies {
    api(project(":plugin-api"))
}

dependencies {
    testImplementation(testFixtures(project(":plugin-api")))
}
