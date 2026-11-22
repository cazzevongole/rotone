// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RoTone"
include(":app")
