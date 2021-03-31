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
            <Button target="_blank" href="https://scastie.scala-lang.org/tMUIAzcuTWqij1xbC9BYNA">Try It!</Button>
            <Button href={docUrl("theory/intro", language)}>Get started</Button>
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
            padding={['bottom', 'top']}
            id={props.id}
            background={props.background}>
            <GridBlock
                align={props.align}
                contents={props.children}
                layout={props.layout}
            />
        </Container>
    );

    const Hook = () => (
        <Block background="light" align="left">
            {[
                {
                    content:
                        "Cats STM is a library for writing composable in-memory transactions which will handling correct locking, optimistic concurrency and automatic retries for you.",
                    image: `${baseUrl}img/hook.png`,
                    imageAlign: 'right',
                }
            ]}
        </Block>
    );

    const Feature = feature => (
        <Block align="left">
            {[{
                content: feature.children,
                image: feature.image,
                imageAlign: feature.align,
                title: feature.title
            }]}
        </Block>
    );

    return (
      <div>
        <HomeSplash siteConfig={siteConfig} language={language} />
        <div className="mainContainer">
            <Hook />

            <Feature align="left" title="Transactional and Safe" image="img/transactional.png">
              Write `Txn` expressions in terms of mutable `TVar`s (analogous to `Ref` from Cats Effect). Run `Txn[A]` expressions transactionally to obtain an `IO[A]` (or `F[A]` for the `Async[F]` of your choice). The STM runtime will determine what locks need to be acquired and in what order to avoid deadlock.
            </Feature>
            <Feature align="right" title="Composable" image="img/composable.png">
               Trivially compose transactional expressions into larger ones without explicit locking or requiring any knowledge of the internals of the subexpressions and what locks they require.
            </Feature>
            <Feature align="left" title="Automatic retries" image="img/retry.png">
                Specify pre-conditions and the committing of a transaction will be automatically retried until the pre-conditions are satisifed.
            </Feature>
            <Feature align="right" title="Fine-grained, optimistic concurrency" image="img/optimistic.png">
                There are no global locks and per-`TVar` locks are acquired only when a transaction has succeeded and should be committed.
            </Feature>
        </div>
      </div>
    );
  }
}

module.exports = Index;
