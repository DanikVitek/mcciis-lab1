plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
//    alias(libs.plugins.koinCompiler)
}

kotlin {
    jvm("desktop")


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
//            implementation(dependencies.platform(libs.koin.bom))
//            implementation(libs.koin.core)
//            implementation(libs.koin.annotations)
//            implementation(libs.koin.compose)
//            implementation(libs.koin.core.viewmodel)
//            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
//            implementation(libs.koin.test)
        }
    }
}