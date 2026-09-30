package me.danikvitek.lab1

class DesktopPlatform : Platform {
    override val name: String = "${System.getProperty("os.name")} (Java ${System.getProperty("java.version")})"
}

actual fun getPlatform(): Platform = DesktopPlatform()