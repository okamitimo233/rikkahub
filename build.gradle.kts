// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.android.build.api.dsl.CommonExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

subprojects {
    afterEvaluate {
        val androidExtension = extensions.findByType(CommonExtension::class.java)
        androidExtension?.buildToolsVersion = "37.0.0"
    }
}
