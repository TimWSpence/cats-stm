/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require("react");

const CompLibrary = require("../../core/CompLibrary.js");

const MarkdownBlock = CompLibrary.MarkdownBlock;
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
  render() {
    const { siteConfig, language = "" } = this.props;
    const { baseUrl, docsUrl } = siteConfig;
    const docsPart = `${docsUrl ? `${docsUrl}/` : ""}`;
    const langPart = `${language ? `${language}/` : ""}`;
    const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

    const SplashContainer = props => (
      <div className="homeContainer">
        <div className="homeSplashFade">
          <div className="wrapper homeWrapper">{props.children}</div>
        </div>
      </div>
    );

    const Logo = (props) => (
      <div className="projectLogo">
         <img src={props.img_src} alt="Project Logo" />
       </div>
     );

    const ProjectTitle = () => (
      <h2 className="projectTitle">
        <span>
          {siteConfig.title}
        </span>
        <small>{siteConfig.tagline}</small>
      </h2>
    );

    const PromoSection = props => (
      <div className="section promoSection">
        <div className="promoRow">
          <div className="pluginRowBlock">{props.children}</div>
        </div>
      </div>
    );

    const Button = props => (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={props.href} target={props.target}>
          {props.children}
        </a>
      </div>
    );

    return (
      <SplashContainer>
        <div className="inner">
          <Logo img_src={`${baseUrl}img/logo.png`} />
          <ProjectTitle siteConfig={siteConfig} />
          <PromoSection>
            <Button href={siteConfig.apiUrl}>API Docs</Button>
            <Button href={docUrl("theory/intro", language)}>Documentation</Button>
            <Button href={siteConfig.repoUrl}>View on GitHub</Button>
          </PromoSection>
        </div>
      </SplashContainer>
    );
  }
}

class Index extends React.Component {
  render() {
    const { config: siteConfig, language = "" } = this.props;
    const { baseUrl } = siteConfig;

    const Block = props => (
      <Container
        padding={["bottom", "top"]}
        id={props.id}
        background={props.background}
      >
        <GridBlock
          align="center"
          contents={props.children}
          layout={props.layout}
        />
      </Container>
    );

    const index = `
[![Build Status](https://github.com/TimWSpence/cats-stm/workflows/Continuous%20Integration/badge.svg)](https://github.com/TimWSpence/cats-stm/actions?query=workflow%3A%22Continuous+Integration%22)
[![Join the chat at https://gitter.im/cats-stm/community](https://badges.gitter.im/cats-stm/community.svg)](https://gitter.im/cats-stm/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

An implementation of Software Transactional Memory for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).

For more information, see the [documentation](https://timwspence.github.io/cats-stm/).


### Usage

\`libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.10.1"\`

The core abstraction is the \`TVar\` (transactional var), which exposes operations in the
\`Txn\` monad. Once constructed, \`Txn\` actions can be atomically evaluated in the \`IO\`
monad.

Here is a contrived example of what this looks like in practice. We use the
\`check\` combinator to retry transferring money from Tim and Steve until we have
enough money in Tim's account:

\`\`\`scala
import scala.concurrent.duration._

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  val stm = STM.runtime[IO].unsafeRunSync()
  import stm._

  override def run: IO[Unit] =
    for {
      accountForTim   <- stm.commit(TVar.of[Long](100))
      accountForSteve <- stm.commit(TVar.of[Long](0))
      _               <- printBalances(accountForTim, accountForSteve)
      _               <- giveTimMoreMoney(accountForTim).start
      _               <- transfer(accountForTim, accountForSteve)
      _               <- printBalances(accountForTim, accountForSteve)
    } yield ()

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    stm.commit {
      for {
        balance <- accountForTim.get
        _       <- stm.check(balance > 100)
        _       <- accountForTim.modify(_ - 100)
        _       <- accountForSteve.modify(_ + 100)
      } yield ()
    }

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] =
    for {
      _ <- IO.sleep(5000.millis)
      _ <- stm.commit(accountForTim.modify(_ + 1))
    } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      (amountForTim, amountForSteve) <- stm.commit(for {
        t <- accountForTim.get
        s <- accountForSteve.get
      } yield (t, s))
      _ <- IO(println(s"Tim: $amountForTim"))
      _ <- IO(println(s"Steve: $amountForSteve"))
    } yield ()

}
\`\`\`

### Documentation

The documentation is built using [docusaurus](https://docusaurus.io/). You can
generate it via \`nix-shell --run "sbt docs/docusaurusCreateSite"\` . You can then
view it via \`nix-shell --run "cd website && npm start"\`.

### Credits

This software was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and the [stm package](http://hackage.haskell.org/package/stm).

Many thanks to [@impurepics](https://twitter.com/impurepics) for the awesome logo!

## Tool Sponsorship

<img width="185px" height="44px" align="right" src="https://www.yourkit.com/images/yklogo.png"/>Development of Cats STM is generously supported in part by [YourKit](https://www.yourkit.com) through the use of their excellent Java profiler.
`.trim();

    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
          <div className="index">
            <MarkdownBlock>{index}</MarkdownBlock>
          </div>
        </div>
      </div>
    );
  }
}

module.exports = Index;
