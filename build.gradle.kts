// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// Records each module's public API in <module>/api/<module>.api; `apiCheck` (part of `check`)
// fails when the code no longer matches the recorded file. Run `./gradlew apiDump` to update it.
apiValidation {
    ignoredProjects += "sample"
}

// Release builds swap `inspector` for `no-op`, so app code must compile against both. Their
// recorded public APIs must therefore be identical.
val checkNoOpApi by tasks.registering {
    group = "verification"
    description = "Fails if the no-op module's public API differs from the inspector module's."
    val inspectorApi = layout.projectDirectory.file("inspector/api/inspector.api")
    val noOpApi = layout.projectDirectory.file("no-op/api/no-op.api")
    inputs.files(inspectorApi, noOpApi)
    doLast {
        val expected = inspectorApi.asFile.readText()
        val actual = noOpApi.asFile.readText()
        if (expected != actual) {
            throw GradleException(
                "no-op public API differs from inspector's. Compare inspector/api/inspector.api " +
                    "with no-op/api/no-op.api and update no-op to match."
            )
        }
    }
}

tasks.register("check") {
    dependsOn(checkNoOpApi)
}