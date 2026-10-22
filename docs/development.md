# Developing RetroNap

For downloading and running a release, start with the [README](../README.md).
The commands below run from the repository root.

## Requirements

- Java 25 JDK for Gradle development, tests, and JAR builds.
- GraalVM for Java 25 and a C toolchain for a local native build.
- Docker for container images or the portable Linux native builder below.
- Python 3.12+ for release smoke checks; Python 3.11+ for load tests.

Use the included Gradle wrapper; a separate Gradle installation is unnecessary.
On Windows, use `gradlew.bat` instead of `./gradlew`.

## Run, build, and test

Start the application from source:

```bash
./gradlew bootRun
```

This uses the same default ports and file database as a downloaded release. See
the [README](../README.md) for client setup and optional configuration.

Build the application and run its tests:

```bash
./gradlew clean build
```

Most tests use isolated in-memory H2 databases with the production
[Flyway migrations](../src/main/resources/db/migration/) and Hibernate schema
validation. File-database tests use temporary directories to check persistence
after reopening and migration compatibility. Tests do not touch the local
database or require Docker.

Run tests or check Java formatting separately:

```bash
./gradlew test
./gradlew spotlessCheck
```

Build and run the executable JAR:

```bash
./gradlew bootJar
java -jar build/libs/retronap-0.0.1-SNAPSHOT.jar
```

The default project version is `0.0.1-SNAPSHOT` in
[build.gradle](../build.gradle). Supplying `-PreleaseVersion=1.2.3` changes the
version used for the build and its JAR filename.

## Native builds and VPS compatibility

With GraalVM for Java 25 and a C toolchain installed:

```bash
./gradlew nativeCompile
```

The Linux executable is `build/native/nativeCompile/retronap`. Deploy any
companion `.so*` files from that directory alongside it. The target machine does
not need Java. For installing it as a service on an Ubuntu VPS, follow the
[systemd guide](systemd.md).

Both `nativeCompile` and `bootBuildImage` pass `-march=compatibility` to Native
Image. GraalVM otherwise defaults to `x86-64-v3` on x64; the compatibility target
reduces the CPU instructions required by the generated code. See the
[GraalVM machine-target documentation](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/#optimizing-for-specific-machines).

If a VPS reports `./retronap: CPU ISA level is lower than required`, the
executable requires CPU features that the VPS does not expose. On Linux x64,
inspect the binary's declared requirements with:

```bash
readelf -n build/native/nativeCompile/retronap
```

Check the `x86 ISA needed` entry if present. The C toolchain can impose a higher
requirement even with `-march=compatibility`: for example, CachyOS startup
objects and libraries built for `x86-64-v4` can make the resulting executable
require that level. The GraalVM flag does not lower the requirements of those
prebuilt objects or libraries. Absence of an ISA entry does not prove
portability.

For a standalone VPS binary, build on the VPS or in a VM/container with a
baseline C toolchain, such as the same Debian/Ubuntu release as the VPS. Use the
same CPU architecture and a glibc version no newer than the target provides.
Deploy the resulting executable and its companion libraries together.

### Build a portable Linux x64 binary with Docker

The [native builder](../docker/native-builder.Dockerfile) uses the official
[GraalVM Community container image](https://www.graalvm.org/jdk25/docs/getting-started/container-images/)
with Oracle Linux 8's C toolchain. This avoids depending on the host's C
toolchain, including on a CachyOS workstation. Docker must be installed and
running.

```bash
docker build --platform linux/amd64 -f docker/native-builder.Dockerfile -t retronap-native-builder docker

docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  --entrypoint /bin/sh \
  -e GRADLE_USER_HOME=/workspace/.gradle-home/container \
  -v "$PWD:/workspace" -w /workspace \
  retronap-native-builder \
  -c './gradlew --no-daemon --project-cache-dir .gradle/container nativeCompile --rerun-tasks'
```

The builder installs `findutils` to provide the `xargs` command required by the
Gradle wrapper. It runs Gradle as your host user so build outputs retain your
ownership. If your Docker installation requires it, prefix both commands with
`sudo`.

`--rerun-tasks` regenerates the native executable and build inputs instead of
reusing a previous workstation build. Gradle caches its distribution and
dependencies separately for this container. The output remains in
`build/native/nativeCompile/`; deploy the newly built executable and companion
`.so*` files together. The target must be Linux x64 with glibc 2.28 or newer, as
provided by Ubuntu 20.04 and newer releases.

For a container deployment, `./gradlew bootBuildImage` uses its own Paketo
builder toolchain. See [Docker setup](../README.md#docker) for building the image,
preparing persistent storage, and starting Compose.

## Configuration and diagnostics

Runtime defaults and bundled content live in:

| File | Purpose |
| --- | --- |
| [application.yml](../src/main/resources/application.yml) | Main runtime defaults |
| [application-docker.yml](../src/main/resources/application-docker.yml) | Docker profile overrides |
| [motd.txt](../src/main/resources/configs/motd.txt) | Message-of-the-day lines |
| [channels.txt](../src/main/resources/configs/channels.txt) | Permanent channels and required levels |

Normal runs use `INFO` application logging with Hibernate SQL printing disabled,
including the Docker profile. Enable packet-level diagnostics when needed:

```bash
java -jar build/libs/retronap-0.0.1-SNAPSHOT.jar \
  --logging.level.com.deriklima.retronap=DEBUG --spring.jpa.show-sql=true
```

The same options work with the native executable.

## Publishing releases

The [Release workflow](../.github/workflows/release.yml) runs on version-tag
pushes. It builds and tests the JAR, compiles the Linux x64 executable using the
Docker native builder, and smoke-tests both packaged distributions before
publishing a GitHub Release with generated release notes.

Each release contains:

- `retronap-<version>.jar`: executable Spring Boot JAR requiring Java 25.
- `retronap-<version>-linux-x64.tar.gz`: native executable, companion `.so*`
  libraries if generated, and license inside a matching top-level directory.
  Requires Linux x64 with glibc 2.28 or newer; Java is unnecessary.
- `SHA256SUMS`: SHA-256 checksums for both downloads.

The workflow passes the tag's version to Gradle through `-PreleaseVersion`; no
edit to the default version in `build.gradle` is needed. Use `vMAJOR.MINOR.PATCH`
for a stable release. Tags with a suffix such as `-alpha.1` or `-rc.1` produce
GitHub prereleases. The built-in `GITHUB_TOKEN` is sufficient; no additional
release secret is needed.

Commit and push all intended changes first, then tag that commit. For example,
replacing this example with a new version:

```bash
git tag -a v0.0.1-alpha.1 -m "RetroNap 0.0.1-alpha.1"
git push origin v0.0.1-alpha.1
```

Monitor the **Release** run in GitHub Actions. Separate JAR and native smoke jobs
verify checksums and launch each packaged application outside the checkout with
a fresh temporary database and dynamically selected ports. They check the
server and metaserver, then run two short mixed-workload cycles with two users
and three files per user. Registration, subsequent login, sharing, search,
browse, hotlist, channel chat, and private-message delivery must succeed.

Both smoke jobs must pass before publication. Logs and JSON reports are retained
as `smoke-jar` and `smoke-native` Actions artifacts for seven days, including on
failure. Use a new version tag for each release; the workflow does not overwrite
existing releases. It does not publish container images, whose tag is configured
separately in `build.gradle` and `docker/compose.yaml`.

### Build and test without publishing

Open **Actions → Release → Run workflow**, select a branch, and enter a version
without the `v` prefix (default: `0.0.1-smoke`). The workflow must be on the
default branch for the **Run workflow** button to appear.

Manual runs build and test both distributions and retain the downloadable
`release-files` Actions artifact for seven days. They never create a GitHub
Release; publishing happens only on version-tag pushes.

### Run release smoke checks locally

Download and extract the complete `release-files` Actions artifact into
`release/` in the repository. Use Python 3.12+ and Java 25 for the JAR check; the
native check needs a compatible Linux x64 machine.

```bash
cd release
sha256sum --check SHA256SUMS
cd ..
python3 scripts/ci/smoke-release.py --distribution jar \
  --artifacts release --output build/release-smoke/jar
python3 scripts/ci/smoke-release.py --distribution native \
  --artifacts release --output build/release-smoke/native
```

Each invocation stops its server and deletes its temporary database when done.
These checks cover startup and a bounded protocol workload; also test with the
real Napster client and on the target VPS when relevant. The implementation is
in [smoke-release.py](../scripts/ci/smoke-release.py).

## Load testing

Simulate users against an already running native, JAR, or container server with
Python 3.11+ and no extra packages:

```bash
python3 scripts/loadtest/retronap_load.py --host 127.0.0.1 --port 8888 \
  --scenario mixed --users 5 --files 100 --duration 60 \
  --output build/loadtest/mixed.json
```

These workloads are separate from Gradle `test`, `check`, and `build`. The
Release workflow runs the small isolated smoke check above; other workloads run
only when explicitly invoked. Load tests create persistent synthetic accounts
by default, so use a benchmark server and database.

The [load-testing guide](load-testing.md) covers idle, share, search, browse,
hotlist, and chat scenarios; reusable accounts; repeated cycles; slow readers;
parameters; and memory measurement. It also includes an opt-in native benchmark
runner that starts isolated servers, samples Linux process memory, and generates
an HTML report.

## Protocol reference

Read the canonical [protocol specification](protocol-spec.md) and
[conformance specification](protocol-conformance-spec.md) before changing
protocol behavior. Update those documents in the same change set when changing
wire formats, validation, sequencing, handlers, state transitions, error/status
semantics, or conformance criteria, as required by [AGENTS.md](../AGENTS.md).
