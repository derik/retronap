# RetroNap

RetroNap is a Napster server implementation focused on compatibility with the legacy **Napster 2.0 Beta 6** client.

This project is **not** a general-purpose Napster/OpenNap server for multiple client generations.

[![Java CI with Gradle](https://github.com/derik/retronap/actions/workflows/gradle.yml/badge.svg)](https://github.com/derik/retronap/actions/workflows/gradle.yml)

## Client Compatibility

RetroNap currently targets only:

- `Napster 2.0 Beta 6`

Other Napster client versions are not supported right now and will not work with this server.
Support for additional versions may be implemented in the future.

Client download:

- https://www.oldversion.com/software/napster/napster-2-0-beta-6/

## What It Provides

- Login/authentication flow compatible with Napster 2.0 Beta 6
- MOTD, channels, private messaging, hotlist
- Share, browse, and search flows
- Download/upload coordination messages
- Persistent embedded H2 database (users, shares, channels, etc.)

## Requirements

- Java `25`
- Docker and Docker Compose (only for building and running the container image)

## Quick Start (Local)

From the repository root, start the server:

```bash
./gradlew bootRun
```

H2 creates `./data/retronap.mv.db` on first startup. Flyway initializes the schema and
Hibernate validates it. No database service or storage profile is needed.

Default ports:

- `8888` (RetroNap server)
- `8875` (metaserver)

Stop the server with `Ctrl+C`. Restarting it uses the same database file.

## Docker Setup

Run these commands from the repository root. Build the image from the current source
before starting Compose:

```bash
./gradlew bootBuildImage
mkdir -p data
```

The image runs as a non-root user. On Linux, make the data directory and any existing
database files writable by that user. Obtain its IDs from the image you just built:

```bash
RETRONAP_CONTAINER_UID=$(docker run --rm --entrypoint id derikjl/retronap:0.0.1-alpha -u)
RETRONAP_CONTAINER_GID=$(docker run --rm --entrypoint id derikjl/retronap:0.0.1-alpha -g)
sudo chown -R "$RETRONAP_CONTAINER_UID:$RETRONAP_CONTAINER_GID" data
```

If using Docker Desktop, ensure the repository directory is available for bind mounts
and writable from the container. Compose requires `data` to exist; it does not create
a root-owned directory automatically.

Start the application container:

```bash
docker compose -f docker/compose.yaml up -d
```

Compose uses the locally built image and exposes ports `8888` and `8875`. It mounts
`./data` from the repository at `/var/lib/retronap`, where H2 creates `retronap.mv.db`.
The file survives container removal and recreation. The H2 credential variables
listed below are also passed through to the container.

Stop the container:

```bash
docker compose -f docker/compose.yaml down
```

Local and Docker startup use the same file by default. Stop one before starting the
other, and adjust file ownership back to your local user if necessary.

## Hosts File Setup (Required for Napster Client)

To make the Napster 2.0 Beta 6 client connect to your RetroNap server, you need to edit the OS hosts file and map Napster hostnames to your RetroNap server IP.

Example entries (replace `192.168.1.50` with your RetroNap server IP):

```text
192.168.1.50 server.napster.com
192.168.1.50 napster.com
```

### Windows 98 SE example

Hosts file location:

- `C:\WINDOWS\HOSTS`

Steps:

1. Open `C:\WINDOWS\HOSTS` in a text editor.
2. Add the mapping lines shown above.
3. Save the file and start the client.

Important note for Windows 98 SE:
The Napster client may overwrite/revert hosts file changes when it starts. For workaround details, see:

- https://deriklima.com/projects/retronap/

### Windows XP example

Hosts file location:

- `C:\WINDOWS\system32\drivers\etc\hosts`

Steps:

1. Open the file as Administrator.
2. Add the mapping lines shown above.
3. Save and restart the Napster client.

## Local Development Setup

Run the server with Gradle:

```bash
./gradlew bootRun
```

Default RetroNap server settings come from `src/main/resources/application.yml`:

- server port: `8888`
- metaserver port: `8875`

## Database Configuration

H2 is the only supported database. Configure it with:

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `RETRONAP_FILE_DATABASE` | `./data/retronap` | Local database basename, without `.mv.db` |
| `RETRONAP_FILE_DATABASE_USERNAME` | `sa` | H2 database user |
| `RETRONAP_FILE_DATABASE_PASSWORD` | empty | H2 database password |

Relative paths are resolved from the process working directory. An absolute path is
useful when launching from different directories. The parent directory must be writable
by the application user. For example, after preparing the directory:

```bash
RETRONAP_FILE_DATABASE=/var/lib/retronap/retronap \
./gradlew bootRun
```

This creates `/var/lib/retronap/retronap.mv.db`. Credentials initialize a new database;
when reopening an existing file, supply its existing credentials. Changing the environment
variables does not change credentials stored in that file.

Only one application process may open a database file at a time. H2 runs embedded,
without a database server or automatic mixed mode. File databases created by the earlier
H2 configuration remain usable at the same path, with their migration history preserved.

### Backup and Restore

Stop the application before copying or replacing the database file. For the default
location, copy `data/retronap.mv.db` to your backup location. Keep its credentials with
your deployment configuration. To restore, stop the application, copy the backup back to
the configured basename plus `.mv.db`, and ensure the application user can read and write
the file and its parent directory before restarting.

Normal startup and container recreation retain stored records. Session cleanup still
removes a disconnecting user's shared-file records according to the protocol lifecycle.
Database files and H2 diagnostic files are ignored by Git.

## Building

Build and run tests:

```bash
./gradlew clean build
```

Most tests use isolated in-memory H2 databases with the production migrations from
`db/migration` and Hibernate schema validation. File-database tests use temporary
directories to verify persistence after reopening and migration compatibility. Tests
do not touch the local database or require Docker. To run only tests:

```bash
./gradlew test
```

Check Java formatting:

```bash
./gradlew spotlessCheck
```

Build container image with Spring Boot:

```bash
./gradlew bootBuildImage
```

Default image tag is configured in `build.gradle` as:

- `derikjl/retronap:0.0.1-alpha`

## On-Demand Load Tests

Simulate users against an already running native, JAR, or container server with
Python 3.11+ (no extra packages):

```bash
python3 scripts/loadtest/retronap_load.py --host 127.0.0.1 --port 8888 \
  --scenario mixed --users 5 --files 100 --duration 60 \
  --output build/loadtest/mixed.json
```

These workloads run only when explicitly invoked; they are not part of Gradle
`test`, `check`, `build`, or CI. They create persistent synthetic accounts by
default, so use a benchmark server/database. See [the load-testing guide](docs/load-testing.md)
for idle/share/search/browse/hotlist/chat scenarios, reusable accounts, repeated
cycles, slow readers, parameters, and memory-measurement examples. The guide also
includes an opt-in native benchmark runner that starts isolated servers, samples
Linux process memory, and generates an HTML report.

## Configuration Files

- `src/main/resources/application.yml`: main runtime config
- `src/main/resources/application-docker.yml`: docker profile overrides
- `src/main/resources/configs/motd.txt`: MOTD lines
- `src/main/resources/configs/channels.txt`: permanent channels and required level

## Protocol Documentation

- `docs/protocol-spec.md`
- `docs/protocol-conformance-spec.md`

These docs describe the implemented wire behavior and testable conformance expectations.
