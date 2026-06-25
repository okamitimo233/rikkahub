plugins {
    alias(libs.plugins.android.library)
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webUiBuildOutputDir = webUiDir.dir("build/client")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

val skipWebUiBuild = providers.gradleProperty("rikkahub.skipWebUiBuild")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui static output into build/client."

    workingDir = webUiDir.asFile
    commandLine("zsh", "-ic", "pnpm exec react-router build")

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))

    outputs.dir(webUiBuildOutputDir)
}

val copyWebUi = tasks.register<Exec>("copyWebUi") {
    group = "build"
    description = "Copy web-ui build output into the web module resources."

    dependsOn(buildWebUi)

    workingDir = webUiDir.asFile
    commandLine("zsh", "-ic", "pnpm exec tsx copy.ts")

    inputs.dir(webUiBuildOutputDir)
    inputs.file(webUiDir.file("copy.ts"))
    outputs.dir(webStaticResourcesDir)
}

android {
    namespace = "me.rerere.rikkahub.web"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

if (!skipWebUiBuild) {
    tasks.named("preBuild") {
        dependsOn(copyWebUi)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
