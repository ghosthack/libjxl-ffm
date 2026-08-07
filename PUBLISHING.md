# Publishing

Releases follow the same Maven Central flow as `ffmpeg-ffm`.

1. Remove `-SNAPSHOT` from the parent and both child POM versions.
2. Run each platform's native build and `mvn clean install` on that platform.
3. Commit and push the release version, then publish a GitHub Release whose tag
   is exactly `v<project.version>`.
4. The release workflow rebuilds/tests all native classifiers and deploys one
   signed Maven Central bundle using the `release,all-natives` profiles.

Repository secrets required by the workflow are `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`.
