# RetroNap

A server for the classic **Napster 2.0 Beta 6** client, with accounts, chat
channels, private messages, hotlists, file sharing, search, and download/upload
coordination. Other Napster client versions are not currently supported.

[![Java CI with Gradle](https://github.com/derik/retronap/actions/workflows/gradle.yml/badge.svg)](https://github.com/derik/retronap/actions/workflows/gradle.yml)

## Quick start

Download a release, start the server, and point your Napster client at it.
RetroNap includes its own database and default configuration; you do not need
to build the project or install a database service.

### 1. Download the server

Open [GitHub Releases](https://github.com/derik/retronap/releases) and download
**one** of these assets from the release you want to use:

| Download | Use it when… | Requirements |
| --- | --- | --- |
| `retronap-<version>-linux-x64.tar.gz` | Running the native executable on Linux x64 (x86-64) | glibc 2.28 or newer; no Java installation needed |
| `retronap-<version>.jar` | Running with Java, including on other operating systems or CPU architectures | Java 25; check with `java -version` |

`<version>` is the release number, including any suffix such as `-alpha.1`.
Choose the packaged asset under **Assets**, rather than GitHub's source-code
archives. Run the server on a modern machine; the legacy Napster client can run
on a separate computer or in a VM.

### 2. Start the server

**Native executable:** extract the `.tar.gz` archive and open a terminal in the
extracted `retronap-<version>-linux-x64` directory, then run:

```bash
./retronap
```

Keep any companion `.so` libraries in that directory beside the executable.

**JAR:** save the downloaded JAR as `retronap.jar`, open a terminal in that
directory, then run:

```bash
java -jar retronap.jar
```

On first startup, RetroNap creates `data/retronap.mv.db` in your current
directory and initializes it automatically. The default TCP ports are **8888**
for the Napster server and **8875** for the metaserver, which tells clients where
to connect. Leave the terminal open while using the server. Stop with `Ctrl+C`;
starting it again from the same directory reuses your database.

### 3. Connect a Napster client

Install [Napster 2.0 Beta 6](https://www.oldversion.com/software/napster/napster-2-0-beta-6/)
on the client computer or VM.

**If the client is on another computer or in a VM**, stop the server and restart
it with the address that the client can reach. Replace `192.168.1.50` below with
your server's LAN or public IP address. Use the command for your download:

```bash
./retronap --retronap.metaserver.server-list=192.168.1.50:8888
```

```bash
java -jar retronap.jar --retronap.metaserver.server-list=192.168.1.50:8888
```

The default advertised address is `127.0.0.1:8888`, which only works when the
client and server run in the same operating system. Inside a VM, `127.0.0.1`
points to the VM itself.

On the **client computer**, edit its hosts file and add these two lines using
the same server IP:

```text
192.168.1.50 server.napster.com
192.168.1.50 napster.com
```

| Client OS | Hosts file |
| --- | --- |
| Windows 98 SE | `C:\WINDOWS\HOSTS` |
| Windows XP and later | `C:\WINDOWS\system32\drivers\etc\hosts` |

Save the file without a `.txt` extension, using administrator permissions where
required. If the client and server run in the same OS, use `127.0.0.1` in both
entries instead. Allow incoming **TCP 8875 and 8888** through the server's
firewall and any router or VPS firewall between it and the client.

Start or restart Napster and **register a new account on your server**. You can
then log in, join a channel, and share or search for files.

If the Windows 98 client reverts your hosts-file changes, see the
[project notes and workaround](https://deriklima.com/projects/retronap/).

## Optional configuration

The bundled settings are enough for a local run. For clients on other machines,
the advertised server address above is the main setting to change.

Both distributions accept command-line options such as
`--retronap.metaserver.server-list=192.168.1.50:8888`. For persistent deployment
settings, you can also set environment variables before starting the process:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `RETRONAP_METASERVER_SERVERLIST` | `127.0.0.1:8888` | Server address and port advertised to clients |
| `RETRONAP_SERVER_PORT` | `8888` | Napster server TCP port |
| `RETRONAP_METASERVER_PORT` | `8875` | Metaserver TCP port |
| `RETRONAP_FILE_DATABASE` | `./data/retronap` | Database path **without** the `.mv.db` extension |
| `RETRONAP_FILE_DATABASE_USERNAME` | `sa` | Embedded H2 database user |
| `RETRONAP_FILE_DATABASE_PASSWORD` | empty | Embedded H2 database password |

If you change the server port, update the port in the advertised address too.
Keep the default ports for the standard Napster client setup above.

Database credentials are separate from Napster accounts. They initialize a new
database; reopening an existing database requires its existing credentials.
Changing these variables does not change the credentials stored in the file.

## Data, upgrades, and backups

H2 is the only supported database. Its file lives at `data/retronap.mv.db` by
default, relative to the directory where you start the server. That directory
must be writable by the user running RetroNap. Only **one server process** can
open a database file at a time.

For a long-lived installation, set `RETRONAP_FILE_DATABASE` to an absolute path
so that switching release directories does not create an empty database. For
example, on Linux, after preparing a writable `/var/lib/retronap` directory:

```bash
RETRONAP_FILE_DATABASE=/var/lib/retronap/retronap ./retronap
```

Use the same variable with `java -jar retronap.jar` for the JAR distribution.
To upgrade, stop the server, back up its database, replace the JAR or the native
executable and its companion libraries, then restart using the same database
path and credentials.

### Backup and restore

Stop RetroNap before copying or replacing the database file. Back up
`data/retronap.mv.db` (or your configured path plus `.mv.db`) and retain its
credentials with your deployment configuration. To restore, copy the backup
back while the server is stopped, ensure the server user can read and write the
file and its parent directory, then restart.

Accounts and other stored records survive restarts. Shared-file listings are
removed when their owner disconnects and are supplied again by the client.

### Run as a service

For an Ubuntu VPS that should start RetroNap automatically and keep it running,
follow the [systemd setup guide](docs/systemd.md). It covers the native
executable, service user, database migration, logs, updates, and backups. The
service and environment templates are in [deploy/systemd](deploy/systemd);
download those separately from the native release archive.

### Verify a download

Releases include `SHA256SUMS`. With GNU `sha256sum` on Linux, place it beside
your downloaded asset and run this **before renaming the JAR**:

```bash
sha256sum --check --ignore-missing SHA256SUMS
```

Check that your chosen asset reports `OK`. `--ignore-missing` lets you verify
one distribution without downloading the other.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Napster cannot connect | Use Beta 6, check the hosts file on the client, allow TCP 8875/8888, and advertise an IP reachable from that client. A VM needs a reachable host address, not loopback. |
| `Address already in use` | Another process is using one of the ports. Stop the previous RetroNap instance or choose unused ports. |
| Database is locked or not writable | Stop other instances using that file and check ownership and permissions on the file and its parent directory. |
| Accounts appear to be missing after an upgrade | Check the working directory and `RETRONAP_FILE_DATABASE`; you may have started with a different database. |
| JAR reports an unsupported Java/class version | Check `java -version` and run with Java 25. |
| Native executable reports a CPU ISA or glibc error | Check the Linux x64/glibc requirements above. Use the JAR with Java 25 or see the [native build compatibility guide](docs/development.md#native-builds-and-vps-compatibility). |

## Docker

Docker is an optional **source-build** route. The release workflow publishes
the native archive and JAR; it does not publish a container image. From a source
checkout with JDK 25 and Docker installed and running:

```bash
./gradlew bootBuildImage
mkdir -p data
```

This builds `derikjl/retronap:0.0.1-alpha`, the image used by
[docker/compose.yaml](docker/compose.yaml). The image runs as a non-root user.
On Linux, make the data directory and any existing database files writable by
that user:

```bash
RETRONAP_CONTAINER_UID=$(docker run --rm --entrypoint id derikjl/retronap:0.0.1-alpha -u)
RETRONAP_CONTAINER_GID=$(docker run --rm --entrypoint id derikjl/retronap:0.0.1-alpha -g)
sudo chown -R "$RETRONAP_CONTAINER_UID:$RETRONAP_CONTAINER_GID" data
```

With Docker Desktop, ensure the checkout is available for bind mounts and
writable from the container. Compose requires `data` to exist.

For remote or VM clients, **edit `RETRONAP_METASERVER_SERVERLIST` in
`docker/compose.yaml`** to use the Docker host's reachable IP and port `8888`.
The file currently sets it to `127.0.0.1:8888`; exporting a variable in your
shell does not override that literal value. Then start the container:

```bash
docker compose -f docker/compose.yaml up -d
```

Follow [the client setup](#3-connect-a-napster-client) using that same IP.
Compose exposes both ports and stores the database in the checkout's `data/`
directory, mounted at `/var/lib/retronap`. It also passes through the database
username and password variables listed above.

```bash
docker compose -f docker/compose.yaml logs -f
docker compose -f docker/compose.yaml down
```

Stopping or removing the container keeps the database. Local source runs and
Compose share this file by default: stop one before starting the other, and
restore ownership to your local user if needed.

## For developers

Install **JDK 25**, then clone the repository and start from source:

```bash
git clone https://github.com/derik/retronap.git
cd retronap
./gradlew bootRun
```

The Gradle wrapper is included; on Windows, use `gradlew.bat` instead of
`./gradlew`. Client connection and database settings are the same as above.

| Task | Command |
| --- | --- |
| Build and run checks | `./gradlew clean build` |
| Run tests only | `./gradlew test` |
| Check Java formatting | `./gradlew spotlessCheck` |
| Build an executable JAR | `./gradlew bootJar` |
| Build a native executable (GraalVM for Java 25 and a C toolchain required) | `./gradlew nativeCompile` |

The default JAR is `build/libs/retronap-0.0.1-SNAPSHOT.jar`. Tests use isolated
H2 databases and do not require Docker or touch your local database.

- [Developer guide](docs/development.md): native build recipes and portability,
  configuration resources, logging, release tags, GitHub Actions, and smoke tests.
- [Load-testing guide](docs/load-testing.md): opt-in workloads and memory benchmarks.
- [Protocol specification](docs/protocol-spec.md) and
  [conformance specification](docs/protocol-conformance-spec.md): canonical
  references for implemented wire behavior and test expectations. Consult both
  before protocol work and update the documentation alongside any behavior change.
