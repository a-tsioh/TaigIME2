// import android.PromptPasswordsSigningConfig

name := "TaigIME2"

version := "0.1.1"

scalaVersion := "2.11.8"

organization := "fr.magistry"

organizationName := "Pierre Magistry"

organizationHomepage := Some(new URL("http://magistry.fr/"))

// resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += Resolver.mavenLocal

android.Plugin.androidBuild

platformTarget in Android := "android-23"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.slf4j" % "slf4j-nop" % "1.7.21",
//  "com.github.tony19" % "logback-android-classic" % "1.1.1-6",
//  "com.github.tony19" % "logback-android-core" % "1.1.1-6",
  aar("com.android.support" % "appcompat-v7" % "23.4.0"),
  "fr.magistry" %% "taigiutils" % "0.1-SNAPSHOT",
  "fr.magistry" %% "nlplib" % "1.0-SNAPSHOT",
  "com.readystatesoftware.sqliteasset" % "sqliteassethelper" % "2.0.1"
  //"com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
)

run <<= run in Android

proguardScala in Android := true

useProguard in Android := true

proguardOptions in Android ++= Settings.proguardCommons // ++ Settings.proguardAkka

packagingOptions in Android := PackagingOptions(Seq( "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE","LICENSE-2.0.txt",  "META-INF/NOTICE.txt"))

packageRelease <<= (packageRelease in Android) //.dependsOn(setDebugTask(false))

/*
apkSigningConfig in Android := Option(
  PromptPasswordsSigningConfig(
    keystore = new File("/home/pierre/android-studio/taigime_key.keystore"),
    alias = "taigimekey"))
*/

proguardOptions in Android ++= Seq(
  "-ignorewarnings",
  "-keep class scala.Dynamic",
 "-assumenosideeffects class android.util.Log {" +
  "public static *** d(...);" +
  "public static *** v(...);}"
)


javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions += "-target:jvm-1.7"

proguardScala := true

dexMulti := true

dexMaxHeap in Android := "8192m"