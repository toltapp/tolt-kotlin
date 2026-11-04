# Releasing

Releases go to Maven Central as `com.tolt:tolt-android`. Publishing a GitHub release runs `.github/workflows/release.yml`, which uploads a signed build to the Central Portal.

## One-time setup

1. Sign in at [central.sonatype.com](https://central.sonatype.com), add the namespace `com.tolt`, and add the verification key it shows as a DNS TXT record on `tolt.com`. Verify the namespace once the record resolves.
2. Under **View Account**, generate a user token. Its username and password are the Central credentials (not your login).
3. Create a signing key and publish its public half:

   ```bash
   gpg --quick-gen-key "Tolt <EMAIL>" rsa4096 sign 3y
   gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
   ```

4. Add the repository secrets. `gh secret set` prompts for each value:

   ```bash
   gh secret set MAVEN_CENTRAL_USERNAME --repo toltapp/tolt-kotlin
   gh secret set MAVEN_CENTRAL_PASSWORD --repo toltapp/tolt-kotlin
   gpg --export-secret-keys --armor KEY_ID | gh secret set SIGNING_KEY --repo toltapp/tolt-kotlin
   gh secret set SIGNING_KEY_PASSWORD --repo toltapp/tolt-kotlin
   ```

## Each release

1. Set `VERSION_NAME` in `gradle.properties` and the version in `README.md`, and merge.
2. Create a release whose tag is `v` plus that version:

   ```bash
   gh release create v0.1.0 --repo toltapp/tolt-kotlin --generate-notes
   ```

3. When the workflow finishes, open **Deployments** in the Central Portal and publish the validated deployment. It shows up on Maven Central within about 30 minutes.

To try a build in an app before releasing, run `./gradlew :tolt:publishToMavenLocal` and add `mavenLocal()` to the app's repositories.
