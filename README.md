# RetroNap

RetroNap is a Napster server implementation focused on compatibility with the legacy **Napster 2.0 Beta 6** client.

This project is **not** a general-purpose Napster/OpenNap server for multiple client generations.

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
- PostgreSQL-backed persistence (users, shares, channels, etc.)

## Requirements

- Java `25`
- Docker and Docker Compose (for containerized setup)
- PostgreSQL `16` (if running outside Docker)

## Quick Start (Full Docker Compose Stack)

This starts both PostgreSQL and RetroNap server containers:

```bash
docker compose -f docker/compose.yaml up -d
```

Exposed ports:

- `8888` (RetroNap server)
- `8875` (metaserver)

Stop:

```bash
docker compose -f docker/compose.yaml down
```

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

### 1. Start PostgreSQL with Compose

Use the root Compose file to start only the database:

```bash
docker compose up -d
```

By default this exposes Postgres on `localhost:5432`.

### 2. Run the server with Gradle

```bash
./gradlew bootRun
```

Default RetroNap server settings come from `src/main/resources/application.yml`:

- server port: `8888`
- metaserver port: `8875`

## Local DB Config Note

`application.yml` points to:

- `jdbc:postgresql://localhost:5433/retronap`
- username: `user`
- password: `password`

The root `compose.yaml` uses:

- `localhost:5432`
- username: `retronap`
- password: `password`

So for local development, either:

1. Override Spring datasource settings with environment variables, or
2. Update `application.yml` for your local DB values.

Example override:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/retronap \
SPRING_DATASOURCE_USERNAME=retronap \
SPRING_DATASOURCE_PASSWORD=password \
./gradlew bootRun
```

## Building

Build and run tests:

```bash
./gradlew clean build
```

Build container image with Spring Boot:

```bash
./gradlew bootBuildImage
```

Default image tag is configured in `build.gradle` as:

- `derikjl/retronap:0.0.1-alpha`

## Configuration Files

- `src/main/resources/application.yml`: main runtime config
- `src/main/resources/application-docker.yml`: docker profile overrides
- `src/main/resources/configs/motd.txt`: MOTD lines
- `src/main/resources/configs/channels.txt`: permanent channels and required level

## Protocol Documentation

- `docs/protocol-spec.md`
- `docs/protocol-conformance-spec.md`

These docs describe the implemented wire behavior and testable conformance expectations.
