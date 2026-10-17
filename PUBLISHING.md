# Publishing

Releases follow the same Maven Central flow as `ffmpeg-ffm`.

1. Remove `-SNAPSHOT` from the parent and both child POM versions.
2. Run each platform's native build and `./mvnw clean install` on that platform.
3. Commit and push the release version, then publish a GitHub Release whose tag
   is exactly `v<project.version>`.
4. The release workflow rebuilds/tests all native classifiers and deploys one
   signed Maven Central bundle using the `release,all-natives` profiles.

Repository secrets required by the workflow are `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`.

Builds use the Maven Wrapper (`./mvnw`), which pins Maven 3.9.16. Do not
switch the release to the runner's `mvn`: under Maven 3.10 the
central-publishing plugin (0.11.0) stages resolver files such as
`_remote.repositories` and `maven-metadata-local.xml` into the bundle, and
Central rejects it with "Bundle has content that does NOT have a .pom file".

To inspect the signed bundle CI would upload, run the Release workflow
manually (optionally with a throwaway `version`). It rebuilds all native
classifiers, builds against an unreachable Central URL with placeholder
credentials, fails if the bundle contains local repository metadata, and
attaches `central-bundle.zip` as a workflow artifact; nothing is published.
