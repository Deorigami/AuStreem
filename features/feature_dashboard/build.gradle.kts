plugins {
    id("plugin.feature")
}

kotlin.sourceSets.commonMain {
    dependencies {
        implementation(project(":cores:core_feature"))
        implementation(project(":cores:core_service"))
    }
}

android.namespace = "app.tktn.feature_dashboard"