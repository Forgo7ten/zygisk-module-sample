plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.zygisk"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DMODULE_NAME=example"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
