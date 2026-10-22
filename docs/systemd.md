# Ubuntu VPS: native executable as a systemd service

This setup uses the native executable and companion libraries built for the VPS.
Docker and a Java runtime are not required on the VPS to run it.

| Location | Contents |
| --- | --- |
| `/opt/retronap/` | Root-owned executable and companion `.so` libraries |
| `/var/lib/retronap/` | Persistent database, owned by the `retronap` service user |
| `/etc/retronap/retronap.env` | Environment settings, readable by root |
| `/etc/systemd/system/retronap.service` | Service definition |
| systemd journal | Application logs |

The provided [unit](../deploy/systemd/retronap.service) runs as a dedicated user,
restarts on failure, and keeps its persistent state directory writable while the
rest of the filesystem is read-only to the service. systemd's
[`StateDirectory=` documentation](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html#StateDirectory=)
describes directory creation, ownership, and persistence.

## 1. Prepare the upload directory and stop the old process

Upload `deploy/systemd/retronap.service` and
`deploy/systemd/retronap.env.example` into the same VPS directory as the native
executable and its `.so` files. The following commands run **on the VPS**. Set this
to the actual upload directory:

```bash
RETRONAP_UPLOAD_DIR=/absolute/path/to/uploaded/files
```

Stop the manually started RetroNap process with Ctrl+C, or send SIGTERM to its
PID if it runs in the background. Wait for it to exit before copying the database.
Only one process may open the H2 database file at a time.

## 2. Create the user and directories

Skip `useradd` if the `retronap` account already exists:

```bash
sudo useradd --system --user-group --home-dir /var/lib/retronap \
  --no-create-home --shell /usr/sbin/nologin retronap
sudo install -d -o root -g root -m 0755 /opt/retronap /etc/retronap
sudo install -d -o retronap -g retronap -m 0750 /var/lib/retronap
```

Install the executable and its companion libraries:

```bash
sudo install -o root -g root -m 0755 "$RETRONAP_UPLOAD_DIR/retronap" /opt/retronap/retronap
for retronap_library in "$RETRONAP_UPLOAD_DIR"/*.so*; do
  [ -f "$retronap_library" ] || continue
  sudo install -o root -g root -m 0644 "$retronap_library" /opt/retronap/
done
```

Keep executable files separate from the writable data directory. The service does
not need write access to `/opt/retronap`.

## 3. Preserve the existing database

If you already have a database, locate the actual `retronap.mv.db` created by the
manual run. With the default configuration, it is under `data/` in the directory
from which you started the process. While the application is stopped:

```bash
RETRONAP_OLD_DATABASE=/absolute/path/to/old/data/retronap.mv.db
sudo cp --no-clobber "$RETRONAP_OLD_DATABASE" /var/lib/retronap/retronap.mv.db
sudo chown retronap:retronap /var/lib/retronap/retronap.mv.db
sudo chmod 0600 /var/lib/retronap/retronap.mv.db
```

The copy preserves the original file and does not overwrite an existing destination.
If the destination already exists, confirm it is the database you intend to use
before starting. For a fresh database, skip this step; the application creates it
on first startup.

## 4. Install and edit the service settings

For the first installation:

```bash
sudo install -o root -g root -m 0600 "$RETRONAP_UPLOAD_DIR/retronap.env.example" /etc/retronap/retronap.env
sudo install -o root -g root -m 0644 "$RETRONAP_UPLOAD_DIR/retronap.service" /etc/systemd/system/retronap.service
sudo nano /etc/retronap/retronap.env
```

Keep the database path as `/var/lib/retronap/retronap`, **without** `.mv.db`.
If migrating a database, set its existing username and password; changing these
settings does not change credentials stored inside the database. If they were
never customized, the defaults are `sa` and an empty password.

For clients on other machines, uncomment `RETRONAP_METASERVER_SERVERLIST` and
replace the example IP with the VPS address reachable by those clients, keeping
`:8888` for the default server port. Do not leave the example IP in place.

The environment file uses `NAME=value` assignments without `export`. Quote values
containing special characters according to
[systemd's environment-file syntax](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html#EnvironmentFile=).

## 5. Validate and start

```bash
sudo systemd-analyze verify /etc/systemd/system/retronap.service
sudo systemctl daemon-reload
sudo systemctl enable --now retronap.service
sudo systemctl status retronap.service --no-pager
sudo journalctl -u retronap.service -n 100 --no-pager
```

`enable --now` starts the service and enables startup after reboot. Check that
the listeners are present:

```bash
sudo ss -ltnp '( sport = :8888 or sport = :8875 )'
```

If UFW is already active, allow the default TCP ports:

```bash
sudo ufw allow 8888/tcp
sudo ufw allow 8875/tcp
```

Allow them in the VPS provider's firewall too, if one is configured.

## Operations, updates, and backups

```bash
sudo journalctl -u retronap.service -f
sudo systemctl restart retronap.service
sudo systemctl stop retronap.service
sudo systemctl start retronap.service
```

After changing the environment file, restart the service. After editing the unit,
run `systemctl daemon-reload` before restarting.

For an executable update, stop the service, install the new executable and its
companion libraries under `/opt/retronap`, then start it. Keep
`/etc/retronap/retronap.env` and `/var/lib/retronap` in place.

For a consistent backup, stop the service before copying the database:

```bash
sudo systemctl stop retronap.service
sudo install -d -o root -g root -m 0700 /var/backups/retronap
sudo install -o root -g root -m 0600 /var/lib/retronap/retronap.mv.db \
  "/var/backups/retronap/retronap-$(date -u +%Y%m%dT%H%M%SZ).mv.db"
sudo systemctl start retronap.service
```

Retain the matching database credentials with your backups. See the
[database backup and restore notes](../README.md#backup-and-restore).
