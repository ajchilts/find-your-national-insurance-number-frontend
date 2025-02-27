resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc"          %  "sbt-auto-build"         % "3.13.0")
addSbtPlugin("uk.gov.hmrc"          %  "sbt-distributables"     % "2.2.0")
addSbtPlugin("com.typesafe.play"    %  "sbt-plugin"             % "2.8.19")
addSbtPlugin("org.scalastyle"       %% "scalastyle-sbt-plugin"  % "1.0.0")
addSbtPlugin("org.scoverage"        %  "sbt-scoverage"          % "1.9.3")
addSbtPlugin("io.github.irundaia"   %  "sbt-sassify"            % "1.5.2")
addSbtPlugin("net.ground5hark.sbt"  %  "sbt-concat"             % "0.2.0")
addSbtPlugin("com.typesafe.sbt"     %  "sbt-uglify"             % "2.0.0")
addSbtPlugin("com.typesafe.sbt"     %  "sbt-digest"             % "1.1.4")
