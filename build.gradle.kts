allprojects {
  group = "co.makery.githubarchive.demo"
  version = "0.1.0"

  repositories {
    jcenter()
    mavenCentral()
  }
}

plugins {
  base
}

dependencies {
  subprojects.forEach {
    archives(it)
  }
}
