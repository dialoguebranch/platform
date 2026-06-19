# Dialogue Branch Core Java Library
Dialogue Branch Core Library in Java.

## Using the Gradle Build Script

The library includes a Gradle Build Script (`build.gradle`) that can be used to compile, build, and
run the library among other things. You don't need to install Gradle on your system to use this
build script, as the repository provides a "Gradle Wrapper" (see
https://docs.gradle.org/current/userguide/gradle_wrapper.html) which is an executable script that
will download a pre-defined version of Gradle before executing any of the defined tasks in the build
script. Using the Gradle Wrapper (`./gradlew` or `gradlew.bat`) is the recommended way of working
with the Gradle build script.

Here is a list of common useful tasks:
- `./gradlew clean` - Cleans all generated output build files (deletes the `/build/` folder).
- `./gradlew build` - Compiles and builds everything.
- `./gradlew run -q --console=plain` - Runs the library's main class (CommandLineRunner). The `-q`
  tells Gradle to be "quiet", while `--console=plain` hides the Gradle `<=========----> 75%
  EXECUTING` progress bar. These additional parameters are needed to properly run the
  CommandLineRunner, which requires command line input.
- `./gradlew test` - Runs all unit tests. You can run a single test class with
  `./gradlew test --tests "com.dialoguebranch.ClassName"`, or a single method with
  `./gradlew test --tests "com.dialoguebranch.ClassName.methodName"`. The HTML test report is
  written to `build/reports/tests/test/index.html`.

Some more advanced tasks:
- `./gradlew javadoc` - Generate the Javadoc HTML pages in `/build/reports/javadoc/`. This can be
  used to generate Javadoc from the latest source in order to update the official hosted docs that
  can be found at https://dialoguebranch.com/docs/dialogue-branch/dev/dlb-core-java/index.html
- `./gradlew wrapper --gradle-version latest` - Generates the Gradle Wrapper files, targeting the
  latest Gradle version. Replace "latest" with a specific version number to generate wrapper scripts
  for the indicated version. This can be used e.g. to upgrade the Gradle version. Note that the
  Gradle Wrapper files are part of the source code committed into Git. *NOTE:* If this task doesn't
  work, you can also manually change the value of `distributionUrl` in the
  `gradle-wrapper.properties` file.
- `./gradlew tasks` - Outputs the full list of available tasks supported by the build script, in
  case you're interested in exploring this.

## Publishing to Maven Central

The library is published to Maven Central under the coordinates
`com.dialoguebranch:dlb-core-java`. Publishing uses the
[NMCP plugin](https://gradleup.com/nmcp/) (New Maven Central Publishing) via the Sonatype Central
Portal API.

### Prerequisites

Before publishing, you need the following configured in your local `gradle.properties` file
(found at `packages/core/gradle.properties`, which is excluded from version control):

**GPG signing credentials** — Maven Central requires all artifacts to be signed. If you do not yet
have a GPG key, generate one with:
```bash
gpg --gen-key
```
Then export your secret keyring:
```bash
gpg --export-secret-keys <YOUR_KEY_ID> > ~/.gnupg/secring.gpg
```

Add the following to `gradle.properties`:
```properties
# The last 8 characters of your GPG key ID
signing.keyId=<LAST_8_CHARS_OF_KEY_ID>

# Your GPG passphrase (leave empty if none was set)
signing.password=<YOUR_GPG_PASSPHRASE>

# Absolute path to your exported GPG keyring file
signing.secretKeyRingFile=/Users/<your-username>/.gnupg/secring.gpg
```

**Sonatype Central Portal token** — Log in to [central.sonatype.com](https://central.sonatype.com),
go to your profile → **View Account** → **Generate User Token**, and add the generated credentials
to `gradle.properties`:
```properties
# Token username generated at central.sonatype.com
centralPortal.username=<TOKEN_USERNAME>

# Token password generated at central.sonatype.com
centralPortal.password=<TOKEN_PASSWORD>
```

### Publishing

Before publishing, update the `version` in `build.gradle` to the new release version, then run:

```bash
./gradlew publishToMaven
```

This will build the library (including sources and Javadoc JARs), sign all artifacts, upload them
to the Sonatype Central Portal, and automatically release them to Maven Central.

It may take a few hours for the new version to be indexed by Maven Central mirrors such as
[MVN Repository](https://mvnrepository.com/artifact/com.dialoguebranch/dlb-core-java). The
authoritative place to confirm a release is live is:
[central.sonatype.com/artifact/com.dialoguebranch/dlb-core-java](https://central.sonatype.com/artifact/com.dialoguebranch/dlb-core-java)

To test the published artifact locally before releasing, install it to your local Maven repository
first:
```bash
./gradlew publishToMavenLocal
```
