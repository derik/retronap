#!/usr/bin/env python3
"""Opt-in TCP workloads against an already running RetroNap server (Python 3.11+)."""

import argparse
import asyncio
from collections import Counter
from contextlib import contextmanager, suppress
from dataclasses import dataclass, field
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import signal
import struct
import sys
import time


class LoadError(Exception):
    """A connection, protocol, or workload assertion failed."""


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def frame(kind, payload=""):
    data = payload.encode("iso-8859-1")
    if len(data) > 65535:
        raise ValueError("Packet payload exceeds the 16-bit wire length")
    return struct.pack("<HH", len(data), kind) + data


def quoted(value):
    if any(c in value for c in '\"\r\n\x00'):
        raise ValueError("Protocol fields cannot contain quotes, newlines, or NUL")
    value.encode("iso-8859-1")
    return f'"{value}"'


@dataclass
class Operation:
    completed: int = 0
    failed: int = 0
    cancelled: int = 0
    total_ms: float = 0
    max_ms: float = 0


class Results:
    def __init__(self):
        self.started_at = timestamp()
        self.started = time.monotonic()
        self.operations = {}
        self.sent = Counter()
        self.received = Counter()
        self.sent_bytes = 0
        self.received_bytes = 0
        self.errors = []
        self.error_count = 0
        self.abort = asyncio.Event()
        self.connected = 0
        self.cycles_completed = 0
        self.phase = "starting"

    def event(self, event, **fields):
        print(json.dumps({"at": timestamp(), "event": event, **fields}), flush=True)

    def set_phase(self, phase, cycle=0):
        self.phase = phase
        self.event("phase", phase=phase, cycle=cycle, connected=self.connected)

    def fail(self, error):
        self.error_count += 1
        if len(self.errors) < 20:
            self.errors.append(str(error)[:400])
        self.abort.set()

    @contextmanager
    def measure(self, name):
        op = self.operations.setdefault(name, Operation())
        started = time.monotonic()
        try:
            yield
        except asyncio.CancelledError:
            op.cancelled += 1
            raise
        except Exception:
            op.failed += 1
            raise
        else:
            elapsed = (time.monotonic() - started) * 1000
            op.completed += 1
            op.total_ms += elapsed
            op.max_ms = max(op.max_ms, elapsed)

    def report(self, args, status):
        operations = {}
        for name, op in self.operations.items():
            operations[name] = {
                "completed": op.completed,
                "failed": op.failed,
                "cancelled": op.cancelled,
                "mean_ms": round(op.total_ms / op.completed, 3) if op.completed else None,
                "max_ms": round(op.max_ms, 3) if op.completed else None,
            }
        return {
            "status": status,
            "started_at": self.started_at,
            "finished_at": timestamp(),
            "elapsed_seconds": round(time.monotonic() - self.started, 3),
            "parameters": vars(args),
            "cycles_completed": self.cycles_completed,
            "operations": operations,
            "sent_packets": dict(self.sent),
            "received_packets": dict(self.received),
            "sent_bytes": self.sent_bytes,
            "received_bytes": self.received_bytes,
            "error_count": self.error_count,
            "errors": self.errors,
        }


@dataclass
class Pending:
    future: asyncio.Future
    end_types: tuple
    item_type: int | None = None
    required: tuple = ()
    predicate: object = None
    seen: set = field(default_factory=set)
    count: int = 0

    def accept(self, kind, payload):
        if self.future.done():
            return
        if kind == self.item_type:
            self.count += 1
        if kind in self.required:
            self.seen.add(kind)
        if kind in self.end_types and (self.predicate is None or self.predicate(payload)):
            missing = set(self.required) - self.seen
            if missing:
                self.future.set_exception(LoadError(f"Terminator {kind} preceded required {missing}"))
            else:
                self.future.set_result((kind, payload, self.count))


class Client:
    def __init__(self, nickname, args, results):
        self.nickname = nickname
        self.args = args
        self.results = results
        self.reader = None
        self.writer = None
        self.receiver = None
        self.pending = []
        self.closing = False
        self.failure = None
        self.read_delay = 0

    async def connect(self):
        with self.results.measure("connect"):
            try:
                async with asyncio.timeout(self.args.timeout):
                    self.reader, self.writer = await asyncio.open_connection(
                        self.args.host, self.args.port, limit=65536
                    )
            except (OSError, TimeoutError) as error:
                reason = str(error) or "connection timed out"
                raise LoadError(
                    f"{self.nickname}: connect to {self.args.host}:{self.args.port}: {reason}"
                ) from error
        self.results.connected += 1
        self.receiver = asyncio.create_task(self.read_loop())

    async def read_loop(self):
        try:
            while True:
                if self.read_delay:
                    await asyncio.sleep(self.read_delay)
                header = await self.reader.readexactly(4)
                length, kind = struct.unpack("<HH", header)
                payload = (await self.reader.readexactly(length)).decode("iso-8859-1")
                self.results.received[kind] += 1
                self.results.received_bytes += length + 4
                if kind in (0, 404, 302):
                    raise LoadError(f"server error {kind}: {payload[:200]}")
                for pending in self.pending:
                    pending.accept(kind, payload)
                # MOTD, stats, presence and broadcasts are counted, then discarded.
                # Never accumulate response payloads or unsolicited notifications.
        except (OSError, asyncio.IncompleteReadError, LoadError) as error:
            if not self.closing:
                self.failure = LoadError(f"{self.nickname}: {error}")
                self.results.fail(self.failure)
                for pending in self.pending:
                    if not pending.future.done():
                        pending.future.set_exception(self.failure)

    async def send(self, kind, payload=""):
        if self.failure:
            raise self.failure
        data = frame(kind, payload)
        try:
            async with asyncio.timeout(self.args.timeout):
                self.writer.write(data)
                await self.writer.drain()
        except TimeoutError as error:
            raise LoadError(f"{self.nickname}: timed out sending {kind}") from error
        self.results.sent[kind] += 1
        self.results.sent_bytes += len(data)

    async def request(self, kind, payload, end_types, *, item_type=None,
                      required=(), predicate=None, sender=None):
        if self.failure:
            raise self.failure
        pending = Pending(asyncio.get_running_loop().create_future(), end_types,
                          item_type, required, predicate)
        self.pending.append(pending)
        try:
            async with asyncio.timeout(self.args.timeout):
                await (sender or self).send(kind, payload)
                return await pending.future
        except TimeoutError as error:
            raise LoadError(f"{self.nickname}: timed out sending {kind}/waiting for {end_types}") from error
        finally:
            self.pending.remove(pending)
            if not pending.future.done():
                pending.future.cancel()
            elif not pending.future.cancelled():
                pending.future.exception()  # Also retrieve errors if sending failed first.

    async def login(self, password, create):
        if create:
            with self.results.measure("nickname_check"):
                kind, _, _ = await self.request(7, self.nickname, (8, 9))
                if kind == 9:
                    raise LoadError(f"{self.nickname} already exists; use --accounts existing or a new prefix")
        payload = f'{self.nickname} {quoted(password)} 6699 "v2.0 BETA 6" 6'
        if create:
            payload += f" {self.nickname}@load.test 0"
        with self.results.measure("register" if create else "login"):
            await self.request(6 if create else 2, payload, (621,), required=(3,))

    async def browse(self, target, expected):
        with self.results.measure("browse"):
            _, _, count = await self.request(211, target, (213,), item_type=212,
                                              predicate=lambda p: p == target)
            if count != expected:
                raise LoadError(f"{self.nickname}: browse {target}: expected {expected} files, got {count}")

    async def share(self, cycle):
        with self.results.measure("share_catalogue"):
            for payload in share_batches(self.nickname, self.args, cycle):
                await self.send(870, payload)
            # 870 has no acknowledgement. This same-connection browse is ordered
            # after all inserts, so failures cannot be mistaken for successful setup.
            await self.browse(self.nickname, self.args.files)

    async def close(self):
        self.closing = True
        if self.writer is not None:
            self.writer.close()
            with suppress(OSError, TimeoutError):
                async with asyncio.timeout(2):
                    await self.writer.wait_closed()
            self.results.connected -= 1
            self.writer = None
        if self.receiver is not None:
            self.receiver.cancel()
            await asyncio.gather(self.receiver, return_exceptions=True)


def share_batches(nickname, args, cycle):
    directory = quoted(f"C:\\LoadTest\\{nickname}\\cycle-{cycle}\\")
    parts = [directory]
    size = len(directory)
    for index in range(args.files):
        filename = f"{args.prefix}-track-{index:06d}.mp3"
        checksum = hashlib.md5(f"{nickname}:{index}".encode(), usedforsecurity=False).hexdigest()
        entry = f'{quoted(filename)} {checksum} 4096 192 44100 180'
        if len(parts) > 1 and (len(parts) - 1 >= args.batch_size or size + 1 + len(entry) > 60000):
            yield " ".join(parts)
            parts, size = [directory], len(directory)
        parts.append(entry)
        size += 1 + len(entry)
    if len(parts) > 1:
        yield " ".join(parts)


async def together(coroutines):
    """Cancel siblings on failure; never leave a partial setup/workload running."""
    tasks = [asyncio.create_task(c) for c in coroutines]
    try:
        return await asyncio.gather(*tasks)
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)


async def action(name, client, target, args, channel, sequence):
    if name == "browse":
        await client.browse(target.nickname, args.files)
        return
    with client.results.measure(name):
        if name == "search":
            term = args.search_term if args.search_term is not None else args.prefix
            _, _, count = await client.request(
                200, f"FILENAME CONTAINS {quoted(term)} MAX_RESULTS {args.max_results}",
                (202,), item_type=201)
            # Current protocol returns all matches, even above MAX_RESULTS.
            if args.search_term is None and count != args.users * args.files:
                raise LoadError(f"{client.nickname}: search expected {args.users * args.files} results, got {count}")
        elif name == "hotlist":
            await client.request(208, target.nickname, (301,), predicate=lambda p: p == target.nickname)
            await client.send(303, target.nickname)
            # The removal has no ack; channel-list completion is a processing barrier.
            await client.request(827, "", (827,))
        elif name == "chat":
            message = f"load-{client.nickname}-{sequence}"
            await client.request(402, f"{channel} {message}", (403,),
                                 predicate=lambda p: p == f"{channel} {client.nickname} {message}")
        elif name == "private":
            message = f"load-{client.nickname}-{sequence}"
            await target.request(205, f"{target.nickname} {message}", (205,), sender=client,
                                 predicate=lambda p: p == f"{client.nickname} {message}")


async def workload(client, target, index, args, channel, deadline):
    actions = {
        "idle": (), "shares": (), "search": ("search",), "browse": ("browse",),
        "hotlist": ("hotlist",), "chat": ("chat", "private"),
        "mixed": ("search", "browse", "hotlist", "chat", "private"),
    }[args.scenario]
    if not actions:
        await asyncio.sleep(max(0, deadline - time.monotonic()))
        return
    sequence = 0
    while time.monotonic() < deadline:
        await action(actions[(index + sequence) % len(actions)], client, target, args, channel, sequence)
        sequence += 1
        await asyncio.sleep(min(args.interval, max(0, deadline - time.monotonic())))


async def run_cycles(args, results, password):
    results.set_phase("baseline")
    await asyncio.sleep(args.baseline)
    for cycle in range(1, args.cycles + 1):
        clients = [Client(f"{args.prefix}{i:06d}", args, results) for i in range(args.users)]
        gate = asyncio.Semaphore(args.setup_concurrency)
        channel = f"load-{args.prefix}"

        async def setup(index, client):
            await asyncio.sleep(args.ramp_up * index / max(1, args.users - 1))
            async with gate:
                await client.connect()
                await client.login(password, args.accounts == "create" and cycle == 1)

        async def share(client):
            async with gate:
                await client.share(cycle)

        try:
            results.set_phase("connecting", cycle)
            await together(setup(i, c) for i, c in enumerate(clients))
            if args.files:
                results.set_phase("sharing", cycle)
                await together(share(c) for c in clients)
            if args.scenario in ("chat", "mixed"):
                results.set_phase("joining_channel", cycle)
                # Finish creating the room before joining the remaining clients.
                for client in clients:
                    with results.measure("join_channel"):
                        await client.request(400, channel, (409,), required=(405, 410),
                                             predicate=lambda p: p == channel)
            results.set_phase("warmup", cycle)
            await asyncio.sleep(args.warmup)
            for client in clients:
                client.read_delay = args.read_delay
            results.set_phase("active", cycle)
            deadline = time.monotonic() + args.duration
            await together(workload(c, clients[(i + 1) % len(clients)], i, args, channel, deadline)
                           for i, c in enumerate(clients))
        finally:
            results.set_phase("disconnecting", cycle)
            for client in clients:
                client.closing = True
            await together(c.close() for c in clients)
        results.set_phase("cooldown", cycle)
        await asyncio.sleep(args.cooldown)
        results.cycles_completed += 1


async def progress(args, results):
    while True:
        await asyncio.sleep(args.progress_interval)
        results.event("progress", phase=results.phase, connected=results.connected,
                      sent=sum(results.sent.values()), received=sum(results.received.values()),
                      completed=sum(op.completed for op in results.operations.values()))


async def run(args, password):
    results = Results()
    current = asyncio.current_task()
    loop = asyncio.get_running_loop()
    if sys.platform != "win32":
        loop.add_signal_handler(signal.SIGTERM, current.cancel)
    results.event("start", parameters=vars(args))
    tasks = [asyncio.create_task(run_cycles(args, results, password)),
             asyncio.create_task(results.abort.wait()), asyncio.create_task(progress(args, results))]
    status, exit_code = "passed", 0
    try:
        done, _ = await asyncio.wait(tasks[:2], return_when=asyncio.FIRST_COMPLETED)
        if tasks[1] in done:
            raise LoadError(results.errors[0])
        await tasks[0]
        if results.abort.is_set():
            raise LoadError(results.errors[0])
    except asyncio.CancelledError:
        status, exit_code = "interrupted", 130
    except Exception as error:
        if not results.abort.is_set():
            results.fail(error)
        status, exit_code = "failed", 1
    finally:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        if sys.platform != "win32":
            loop.remove_signal_handler(signal.SIGTERM)
        report = results.report(args, status)
        if args.output:
            Path(args.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        results.event("summary", **report)
    return exit_code


def positive_int(value):
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return parsed


def nonnegative(value):
    parsed = float(value)
    if not math.isfinite(parsed) or parsed < 0:
        raise argparse.ArgumentTypeError("must be finite and nonnegative")
    return parsed


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1", help="running RetroNap server")
    parser.add_argument("--port", type=positive_int, default=8888, help="Napster TCP port, not metaserver")
    parser.add_argument("--scenario", choices=("idle", "shares", "search", "browse", "hotlist", "chat", "mixed"), default="idle")
    parser.add_argument("--users", type=positive_int, default=5)
    parser.add_argument("--files", type=int, default=0, help="synthetic files per user, published before the workload")
    parser.add_argument("--batch-size", type=positive_int, default=100, help="maximum files per 870 packet; also bounded by wire size")
    parser.add_argument("--duration", type=nonnegative, default=60, help="active seconds per cycle, excluding setup/warmup/cooldown")
    parser.add_argument("--interval", type=nonnegative, default=1, help="seconds after each action per user (0 runs continuously)")
    parser.add_argument("--ramp-up", type=nonnegative, default=5, help="spread connection starts over these seconds")
    parser.add_argument("--setup-concurrency", type=positive_int, default=5, help="maximum concurrent login or share setups")
    parser.add_argument("--warmup", type=nonnegative, default=5, help="connected settling time before each active phase")
    parser.add_argument("--baseline", type=nonnegative, default=0, help="wait before making any connections")
    parser.add_argument("--cooldown", type=nonnegative, default=5, help="wait after disconnections, including the last cycle")
    parser.add_argument("--cycles", type=positive_int, default=1, help="repeat with the same accounts; later cycles use normal login")
    parser.add_argument("--timeout", type=nonnegative, default=30, help="seconds for each connection/send/complete response")
    parser.add_argument("--read-delay", type=nonnegative, default=0, help="deliberate seconds between received frames during the active phase")
    parser.add_argument("--max-results", type=positive_int, default=100, help="sent in search requests; current server does not enforce it")
    parser.add_argument("--search-term", help="default searches for this run's prefix and checks the full result count")
    parser.add_argument("--accounts", choices=("create", "existing"), default="create", help="create synthetic accounts, or log into prefix000000 etc.")
    parser.add_argument("--prefix", default=None, help="1-14 ASCII letters/digits/underscores; default is a unique lt prefix")
    parser.add_argument("--password-env", default="RETRONAP_LOAD_PASSWORD", help="environment variable for account password; fallback: load-test-only")
    parser.add_argument("--progress-interval", type=nonnegative, default=5, help="seconds between progress events")
    parser.add_argument("--output", help="optional JSON summary file; progress and final summary always go to stdout")
    args = parser.parse_args(argv)
    if args.accounts == "existing" and not args.prefix:
        parser.error("--accounts existing requires --prefix")
    args.prefix = args.prefix or "lt" + secrets.token_hex(4)
    if not re.fullmatch(r"[A-Za-z0-9_]{1,14}", args.prefix):
        parser.error("--prefix must contain 1-14 ASCII letters, digits, or underscores")
    if args.users > 999999 or args.port > 65535 or args.max_results > 100:
        parser.error("maximum users=999999, port=65535, max-results=100")
    if args.files < 0 or args.timeout <= 0 or args.progress_interval <= 0:
        parser.error("files must be nonnegative; timeout and progress-interval must be positive")
    if args.scenario == "shares" and not args.files:
        parser.error("--scenario shares requires --files greater than zero")
    password = os.environ.get(args.password_env, "load-test-only")
    try:
        if not password or len(password) > 72 or not password.isascii() or not password.isprintable():
            raise ValueError("password must contain 1-72 printable ASCII characters")
        quoted(password)
        if args.search_term is not None:
            frame(200, f"FILENAME CONTAINS {quoted(args.search_term)} MAX_RESULTS {args.max_results}")
    except (ValueError, UnicodeError) as error:
        parser.error(str(error))
    return args, password


def main(argv=None):
    args, password = parse_args(argv)
    # Verify the report destination before creating accounts or connecting.
    if args.output:
        path = Path(args.output)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("x", encoding="utf-8") as output:
            output.write(json.dumps({"status": "starting", "parameters": vars(args)}) + "\n")
    return asyncio.run(run(args, password))


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, KeyboardInterrupt) as error:
        print(f"load test: {error}", file=sys.stderr)
        sys.exit(130 if isinstance(error, KeyboardInterrupt) else 1)
