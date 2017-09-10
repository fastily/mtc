# MTC!
[![Build Status](https://travis-ci.org/fastily/mtc.svg?branch=master)](https://travis-ci.org/fastily/mtc)
![JDK-1.8+](https://upload.wikimedia.org/wikipedia/commons/7/75/Blue_JDK_1.8%2B_Shield_Badge.svg)
[![License: GPL v3](https://upload.wikimedia.org/wikipedia/commons/8/86/GPL_v3_Blue_Badge.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

MTC! is a tool that helps simplify and automate the file transfers from Wikipedia to Commons.

#### Build
First, clone [wpkit](https://github.com/fastily/wpkit) and publish it to your local maven repository:
```bash
./gradlew build publishToMavenLocal
```

Next, clone and build this project with:
```bash
./gradlew build mtc
```

#### Run
```bash
java -jar build/libs/MTC-<APP_VERSION_HERE>.jar
```