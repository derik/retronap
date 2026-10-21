#!/usr/bin/env python3
"""Start a packaged RetroNap release and check its external protocol (Python 3.12+)."""

import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import tarfile
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_OPERATIONS = (
    "connect", "nickname_check", "register", "login", "share_catalogue", "search", "browse",
    "join_channel", "hotlist", "chat", "private",
)


def reserve_ports():
    with socket.socket() as server, socket.socket() as meta:
        server.bind(("127.0.0.1", 0))
        meta.bind(("127.0.0.1", 0))
        return server.getsockname()[1], meta.getsockname()[1]


def stop_server(process):
    # Kill the process group even if the parent exited unexpectedly.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def wait_until_ready(process, port, meta_port, timeout):
    deadline = time.monotonic() + timeout
    expected = f"127.0.0.1:{port}"
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Server exited during startup (exit {process.returncode})")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                pass
            with socket.create_connection(("127.0.0.1", meta_port), timeout=0.5) as meta:
                with meta.makefile("rb") as response:
                    actual = response.readline(1024).decode("ascii").strip()
            if actual != expected:
                raise RuntimeError(f"Metaserver returned {actual!r}; expected {expected!r}")
            return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError(f"Server and metaserver did not become ready within {timeout} seconds")


def prepare_command(distribution, artifacts, workspace):
    pattern = "retronap-*.jar" if distribution == "jar" else "retronap-*-linux-x64.tar.gz"
    matches = sorted(artifacts.glob(pattern))
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one {pattern} in {artifacts}; found {len(matches)}")
    artifact = matches[0]
    if distribution == "jar":
        return ["java", "-jar", str(artifact)], workspace

    extracted = workspace / "extracted"
    extracted.mkdir()
    with tarfile.open(artifact, "r:gz") as archive:
        archive.extractall(extracted, filter="data")
    binaries = list(extracted.glob("*/retronap"))
    if len(binaries) != 1 or not os.access(binaries[0], os.X_OK):
        raise RuntimeError("Native archive must contain one executable <directory>/retronap")
    # Run outside the checkout, with only the packaged companion libraries available.
    return [str(binaries[0])], binaries[0].parent


def smoke(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    for filename in ("workload.json", "server.log", "client.log", "summary.json"):
        (output / filename).unlink(missing_ok=True)
    report_path = output / "workload.json"
    summary = {"distribution": args.distribution, "status": "failed"}
    try:
        with tempfile.TemporaryDirectory(prefix=f"retronap-smoke-{args.distribution}-") as folder:
            workspace = Path(folder)
            command, working_directory = prepare_command(
                args.distribution, args.artifacts.resolve(), workspace)
            port, meta_port = reserve_ports()
            command += [
                f"--retronap.server.port={port}",
                f"--retronap.metaserver.port={meta_port}",
                f"--retronap.metaserver.server-list=127.0.0.1:{port}",
            ]
            # Avoid inheriting a developer's database, profiles, or Java agent options.
            env = {key: value for key, value in os.environ.items()
                   if not key.startswith(("SPRING_", "RETRONAP_", "JAVA_TOOL_OPTIONS",
                                          "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "LD_"))}
            env["RETRONAP_FILE_DATABASE"] = str(workspace / "database")
            env["RETRONAP_LOAD_PASSWORD"] = "release-smoke-only"
            client_command = [
                sys.executable, "-B", str(ROOT / "scripts/loadtest/retronap_load.py"),
                "--host", "127.0.0.1", "--port", str(port),
                "--scenario", "mixed", "--users", "2", "--files", "3",
                "--duration", "5", "--interval", "0.1", "--cycles", "2",
                "--ramp-up", "0", "--warmup", "0", "--cooldown", "1",
                "--timeout", "10", "--prefix", "releasesmoke",
                "--output", str(report_path),
            ]
            summary.update(server_command=command, client_command=client_command,
                           server_port=port, metaserver_port=meta_port)
            with (output / "server.log").open("w") as server_log:
                process = subprocess.Popen(command, cwd=working_directory, env=env,
                                           stdout=server_log, stderr=subprocess.STDOUT,
                                           start_new_session=True)
                try:
                    wait_until_ready(process, port, meta_port, args.startup_timeout)
                    with (output / "client.log").open("w") as client_log:
                        subprocess.run(client_command, cwd=workspace, env=env,
                                       stdout=client_log, stderr=subprocess.STDOUT,
                                       timeout=90, check=True)
                    report = json.loads(report_path.read_text())
                    if report["status"] != "passed" or report["cycles_completed"] != 2:
                        raise RuntimeError("Protocol workload did not complete both cycles")
                    for name in REQUIRED_OPERATIONS:
                        operation = report["operations"].get(name, {})
                        if not operation.get("completed") or operation.get("failed"):
                            raise RuntimeError(f"Required operation {name} did not pass")
                    if process.poll() is not None:
                        raise RuntimeError(f"Server exited during workload (exit {process.returncode})")
                    if not (workspace / "database.mv.db").is_file():
                        raise RuntimeError("Application did not create its isolated file database")
                    summary["status"] = "passed"
                finally:
                    stop_server(process)
    except Exception as error:
        summary.update(status="failed", error=str(error))
        print(f"{args.distribution} smoke test failed: {error}", file=sys.stderr)
        for filename in ("server.log", "client.log"):
            path = output / filename
            if path.exists():
                print(f"Last lines of {path}:\n" + "\n".join(
                    path.read_text(errors="replace").splitlines()[-40:]), file=sys.stderr)
    finally:
        (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(f"{args.distribution} smoke test: {summary['status']}; logs: {output}", flush=True)
    return 0 if summary["status"] == "passed" else 1


def positive_seconds(value):
    seconds = float(value)
    if not 0 < seconds < float("inf"):
        raise argparse.ArgumentTypeError("must be a finite positive number")
    return seconds


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--distribution", choices=("jar", "native"), required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--startup-timeout", type=positive_seconds, default=60)
    return smoke(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
