plugins {
  application
  kotlin("jvm") version "1.1.50"
}

application {
  mainClassName = "adapter.AdapterServer"
}

dependencies {
  compile(project(":shared"))
  compile(kotlin("stdlib", "1.1.50"))
  compile("ch.qos.logback:logback-classic:1.2.3")
  compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.1")
  compile("com.typesafe.akka:akka-http_2.12:10.0.10")
  compile("com.typesafe.akka:akka-slf4j_2.12:2.5.4")
  compile("com.typesafe.akka:akka-stream_2.12:2.5.4")
}

tasks.getByPath("run").configure(closureOf<JavaExec> {
  standardInput = System.`in`
})
