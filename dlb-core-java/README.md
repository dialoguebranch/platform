# Dialogue Branch Core Java Library
DialogueBranch Core Library in Java.

## Using the Gradle Build Script

The library includes a Gradle Build Script (`build.gradle`) that can be used to compile, build, and run the library among other things. You don't need to install Gradle on your system to use this build script, as the repository provides a "Gradle Wrapper" (see https://docs.gradle.org/current/userguide/gradle_wrapper.html) which is an executable script that will download a pre-defined version of Gradle before executing any of the defined tasks in the build script. Using the Gradle Wrapper (`./gradlew` or `gradlew.bat`) is the recommended way of working with the Gradle build script.

Here is a list of common useful tasks:
- `./gradlew clean` - Cleans all generated output build files (deletes the `/build/` folder).
- `./gradlew build` - Compiles and builds everything.
- `./gradlew run -q --console=plain` - Runs the library's main class (CommandLineRunner). The `-q` tells Gradle to be "quiet", while `--console=plain` hides the Gradle `<=========----> 75% EXECUTING` progress bar. These additional parameters are needed to properly run the CommandLineRunner, which requires command line input.

Some more advanced tasks:
- `./gradlew javadoc` - Generate the Javadoc HTML pages in `/build/javadoc/`. This can be used to generate Javadoc from the latest source in order to update the official hosted docs that can be found at https://dialoguebranch.com/docs/dialogue-branch/dev/dlb-core-java/index.html
- `./gradlew wrapper --gradle-version latest` - Generates the Gradle Wrapper files, targeting the latest Gradle version. Replace "latest" with a specific version number to generate wrapper scripts for the indicated version. This can be used e.g. to upgrade the Gradle version. Note that the Gradle Wrapper files are part of the source code committed into Git. *NOTE:* If this task doesn't work, you can also manually change the value of `distributionUrl` in the `gradle-wrapper.properties` file.
- `./gradlew tasks` - Outputs the full list of available tasks supported by the build script, in case you're interested in exploring this.


