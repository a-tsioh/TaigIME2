object Settings {
  lazy val proguardCommons = Seq(
    "-ignorewarnings",
  //  "-keep class com.fortysevendeg.** { *; }",
    "-keep class macroid.** { *; }",
    "-keep class scala.Dynamic",
    "-keep class java.util.regex.** { *;}"
    )
}

/*
"""
    | -assumenosideeffects class android.util.Log {
    | public static *** d(...);
    | public static *** v(...);
    | }
    """.stripMargin
 */