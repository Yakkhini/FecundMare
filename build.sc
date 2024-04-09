import mill._, scalalib._

object build extends ScalaModule {
  override def millSourcePath = os.pwd
  override def scalaVersion = "2.13.12"

  override def scalacOptions = Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    // "-Xfatal-warnings",
    "-language:reflectiveCalls"
  )

  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.2.0"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.2.0"
  )

}
