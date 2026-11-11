rootProject.name = "group-notify-manager"

// The contract every delivery plugin implements, with no dependency on Spring, so a plugin
// can be built and tested outside this repository.
include("plugin-api")

// The delivery plugins shipped with the application. Each is its own jar, found at runtime
// through java.util.ServiceLoader exactly as an external plugin dropped into plugins/ is.
include("plugins:discord", "plugins:groupsio")
