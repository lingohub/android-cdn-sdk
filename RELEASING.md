# Releasing

The SDK is published to Maven Central as `com.lingohub:android-cdn-sdk` through the
[Central Publisher Portal](https://central.sonatype.com), using the
[vanniktech maven publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).

## One-time setup (repository secrets)

The publish workflow needs these GitHub Actions secrets:

| Secret | Content |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Username of a *user token* generated at central.sonatype.com (Account → Generate User Token). Not the account login. |
| `MAVEN_CENTRAL_PASSWORD` | Password half of the same user token. |
| `MAVEN_CENTRAL_SIGNING_KEY` | The GPG private key, ASCII-armored **plain text** (`gpg --armor --export-secret-keys <id>`), including the `BEGIN/END PGP PRIVATE KEY BLOCK` lines. Not base64-wrapped. |
| `MAVEN_CENTRAL_SIGNING_PASSWORD` | Passphrase of that key. |

The public key must be available on a keyserver Central checks
(`keyserver.ubuntu.com` or `keys.openpgp.org`):

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

If the signing key is a subkey, additionally set
`ORG_GRADLE_PROJECT_signingInMemoryKeyId` (last 8 characters of the subkey id)
in the workflow env.

## Validating the pipeline (optional)

Run the *Publish to Maven Central* workflow manually from the Actions tab
(workflow_dispatch). This builds, signs, and uploads the deployment to the
Central Portal **without releasing it** — review it at
[central.sonatype.com](https://central.sonatype.com) under *Deployments*, then
publish or drop it there.

## Cutting a release

1. Make sure `main` is green.
2. Bump the version and push the tag:

   ```bash
   ./bump-version.sh minor   # or: patch | major
   ```

   This updates `version.properties`, commits, tags `vX.Y.Z`, and pushes.
3. Create a GitHub release from that tag (the tag alone does **not** publish).
   Publishing starts when the release is *published*.
4. The `Publish to Maven Central` workflow builds, signs, uploads, and releases
   the deployment automatically. Artifacts are usually resolvable within ~30
   minutes; search indexing on central.sonatype.com takes longer.

## Verifying locally

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/com/lingohub/android-cdn-sdk/
```

Check the generated POM for correct coordinates, dependencies, license, and SCM
information before cutting a release. Signing is skipped for local publishing
when no key is configured.
