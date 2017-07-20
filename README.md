# MTC!
![JDK-1.8+](https://upload.wikimedia.org/wikipedia/commons/7/75/Blue_JDK_1.8%2B_Shield_Badge.svg)
[![License: GPL v3](https://upload.wikimedia.org/wikipedia/commons/8/86/GPL_v3_Blue_Badge.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

MTC! is a tool that helps simplify and automate the file transfers from Wikipedia to Commons.

### Build and Run Instructions
1. Clone [wpkit](https://github.com/fastily/wpkit), `cd` into the directory, and run `./gradlew build publishToMavenLocal`
2. Clone this repository, and build with `./gradlew build`
3. Create a runnable jar by running `./gradlew mtc`
4. Run with `java -jar build/libs/MTC-<APP_VERSION_HERE>.jar`