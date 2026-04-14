plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.21.4" /* [SC] DO NOT EDIT */

// In Stonecutter 0.7+, "chiseled tasks" are removed.
// The replacement is: register a wrapper task that depends on all shard tasks via
// stonecutter.tasks.named("taskName"), and use stonecutter tasks { order(...) }
// to enable Stonecutter's sequential lock-based serialization across shards.

tasks.register("chiseledBuild") {
    group = "stonecutter"
    description = "Builds all version shards"
    dependsOn(stonecutter.tasks.named("build"))
}

tasks.register("chiseledRunClient") {
    group = "stonecutter"
    description = "Runs the Minecraft client for each version shard sequentially"
    dependsOn(stonecutter.tasks.named("runClient"))
}

stonecutter tasks {
    order("build")
    order("runClient")
}
