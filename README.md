# MTC!
[![Build Status](https://travis-ci.org/fastily/mtc.svg?branch=master)](https://travis-ci.org/fastily/mtc)
![JDK-11+](https://upload.wikimedia.org/wikipedia/commons/e/ef/Blue_JDK_11%2B_Shield_Badge.svg)
[![License: GPL v3](https://upload.wikimedia.org/wikipedia/commons/8/86/GPL_v3_Blue_Badge.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

MTC! is a tool that helps simplify and automate the file imports from Wikipedia to Commons.

#### Build
First, clone [jwiki](https://github.com/fastily/jwiki), build, and publish it to your local maven repository:
```bash
git clone 'https://github.com/fastily/jwiki.git' && \
cd jwiki && \
./gradlew -x test publishToMavenLocal
```

Next, clone [wp-toolbox](https://github.com/fastily/wp-toolbox), build, and publish it to your local maven repository:
```bash
git clone 'https://github.com/fastily/wp-toolbox.git' && \
cd wp-toolbox && \
./gradlew -x test publishToMavenLocal
```

Then, clone and build this project with:
```bash
git clone 'https://github.com/fastily/mtc.git' && \
cd mtc && \
./gradlew build mtc-ui:doDist
```

#### Run
```bash
./gradlew mtc-ui:run
```