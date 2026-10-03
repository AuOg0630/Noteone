// 依赖仓库。
//
// 默认只用官方源。国内网络下 dl.google.com 经常连不上（表现为
// "Could not GET ... > dl.google.com" 或 "不知道这样的主机"），此时把下面
// 注释里的阿里云镜像取消注释，并**放在 google() 之前**即可。
pluginManagement {
    repositories {
        // maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // maven { url = uri("https://maven.aliyun.com/repository/google") }
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // maven { url = uri("https://maven.aliyun.com/repository/google") }
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "noteone"
include(":app")
