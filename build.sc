import mill._, scalalib._

object taohe extends ScalaModule {
  override def scalaVersion = "2.13.14"

  override def scalacOptions = Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    // "-Xfatal-warnings",
    "-language:reflectiveCalls"
  )

  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.5.0"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.5.0"
  )

}
