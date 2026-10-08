"""On-demand harness checks; uses only a disposable in-process TCP peer."""

import asyncio
from contextlib import redirect_stdout, redirect_stderr, suppress
import io
import json
import os
from pathlib import Path
import re
import struct
import tempfile
import unittest
from unittest.mock import patch

import retronap_load as load


class FakeServer:
    """Independent wire peer for fragmentation, asynchronous events and failures."""

    def __init__(self, *, login_error=False, finish_search=True, idle_error=False,
                 truncate_search=False, wrong_browse=False):
        self.login_error = login_error
        self.finish_search = finish_search
        self.idle_error = idle_error
        self.truncate_search = truncate_search
        self.wrong_browse = wrong_browse
        self.accounts = set()
        self.online = {}
        self.tasks = set()
        self.commands = []
        self.writers = set()
        self.write_locks = {}

    async def start(self):
        self.server = await asyncio.start_server(self.handle, "127.0.0.1", 0)
        self.port = self.server.sockets[0].getsockname()[1]
        return self

    async def stop(self):
        self.server.close()
        await self.server.wait_closed()
        for writer in list(self.writers):
            writer.close()
        await asyncio.gather(*list(self.tasks), return_exceptions=True)

    async def emit(self, writer, kind, payload=""):
        data = payload.encode("latin-1")
        packet = struct.pack("<HH", len(data), kind) + data
        # Fragment packets without interleaving bytes from concurrent senders.
        async with self.write_locks[writer]:
            writer.write(packet[:1])
            await writer.drain()
            await asyncio.sleep(0)
            writer.write(packet[1:])
            await writer.drain()

    async def handle(self, reader, writer):
        task = asyncio.current_task()
        self.tasks.add(task)
        self.writers.add(writer)
        self.write_locks[writer] = asyncio.Lock()
        nick = None
        state = {"writer": writer, "files": 0, "channel": None}
        try:
            while True:
                size, kind = struct.unpack("<HH", await reader.readexactly(4))
                payload = (await reader.readexactly(size)).decode("latin-1")
                self.commands.append((kind, payload))
                if kind == 7:
                    await self.emit(writer, 9 if payload in self.accounts else 8)
                elif kind in (2, 6):
                    nick = payload.split()[0]
                    if self.login_error:
                        await self.emit(writer, 0, "Login rejected")
                        continue
                    self.accounts.add(nick)
                    self.online[nick] = state
                    await self.emit(writer, 3, nick + "@load.test")
                    await self.emit(writer, 621, "Welcome")
                    await self.emit(writer, 621, "Second MOTD line")
                    if self.idle_error:
                        await asyncio.sleep(0.02)
                        await self.emit(writer, 404, "asynchronous failure")
                    else:
                        await self.emit(writer, 214, "1 0 0")
                elif kind == 870:
                    state["files"] += (len(re.findall(r'"[^"]*"|\S+', payload)) - 1) // 6
                elif kind == 211:
                    count = self.online[payload]["files"] + int(self.wrong_browse)
                    for _ in range(count):
                        await self.emit(writer, 212, f'{payload} "track.mp3" hash 4096 192 44100 180')
                    await self.emit(writer, 214, "1 0 0")
                    await self.emit(writer, 213, payload)
                elif kind == 200:
                    if self.truncate_search:
                        writer.write(struct.pack("<HH", 10, 201) + b"abc")
                        await writer.drain()
                        return
                    for _ in range(sum(s["files"] for s in self.online.values())):
                        await self.emit(writer, 201, '"track.mp3" hash 4096 192 44100 180 peer 0 6')
                    await self.emit(writer, 214, "1 0 0")
                    if self.finish_search:
                        await self.emit(writer, 202)
                elif kind == 400:
                    state["channel"] = payload
                    await self.emit(writer, 406, payload + " peer 0 6")
                    await self.emit(writer, 405, payload)
                    await self.emit(writer, 410, payload + " topic")
                    await self.emit(writer, 409, payload)
                elif kind == 402:
                    channel, text = payload.split(" ", 1)
                    # Echo suffices to check the harness's channel acknowledgement.
                    await self.emit(writer, 403, f"{channel} {nick} {text}")
                elif kind == 205:
                    target, text = payload.split(" ", 1)
                    await self.emit(self.online[target]["writer"], 205, f"{nick} {text}")
                elif kind == 208:
                    await self.emit(writer, 301, payload)
                    await self.emit(writer, 209, payload + " 6")
                elif kind == 827:
                    await self.emit(writer, 828, "General 0 0 1 0 topic")
                    await self.emit(writer, 827)
        except (asyncio.IncompleteReadError, ConnectionError):
            pass
        finally:
            if nick is not None:
                self.online.pop(nick, None)
            writer.close()
            with suppress(ConnectionError):
                await writer.wait_closed()
            self.writers.discard(writer)
            self.write_locks.pop(writer, None)
            self.tasks.discard(task)


class InputTests(unittest.TestCase):
    def test_wire_encoding_and_limit(self):
        self.assertEqual(load.frame(870, "caf\xe9"), b"\x04\x00\x66\x03caf\xe9")
        self.assertEqual(load.frame(202), b"\x00\x00\xca\x00")
        with self.assertRaises(ValueError):
            load.frame(200, "a" * 65536)
        with self.assertRaises(UnicodeError):
            load.frame(200, "\u2603")

    def test_share_batches_obey_wire_limit_and_do_not_drop_files(self):
        args, _ = load.parse_args(["--prefix", "test", "--files", "3000", "--batch-size", "100000"])
        count = packets = 0
        for payload in load.share_batches("test000000", args, 1):
            self.assertLessEqual(len(load.frame(870, payload)), 60004)
            count += (len(re.findall(r'"[^"]*"|\S+', payload)) - 1) // 6
            packets += 1
        self.assertEqual(count, 3000)
        self.assertGreater(packets, 1)

    def test_invalid_parameters_fail_before_connecting(self):
        for parameters in (["--users", "0"], ["--files", "-1"], ["--duration", "nan"],
                           ["--interval", "inf"], ["--port", "65536"], ["--timeout", "0"],
                           ["--accounts", "existing"], ["--prefix", "too-long-prefix"],
                           ["--scenario", "shares"], ["--search-term", 'bad"term']):
            with self.subTest(parameters=parameters), redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                load.parse_args(parameters)

    def test_password_is_not_in_report_parameters(self):
        with patch.dict(os.environ, {"RETRONAP_LOAD_PASSWORD": "private-test-password"}):
            args, password = load.parse_args([])
        self.assertEqual(password, "private-test-password")
        self.assertNotIn(password, json.dumps(vars(args)))


class ClientTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.servers = []

    async def asyncTearDown(self):
        for server in self.servers:
            await server.stop()

    async def server(self, **options):
        server = await FakeServer(**options).start()
        self.servers.append(server)
        return server

    async def execute(self, server, *extra):
        args, password = load.parse_args([
            "--port", str(server.port), "--prefix", "test", "--users", "3",
            "--ramp-up", "0", "--warmup", "0", "--cooldown", "0.03",
            "--duration", "0.15", "--interval", "0.01", "--timeout", "1",
            "--progress-interval", "100", *extra])
        output = io.StringIO()
        with redirect_stdout(output):
            code = await load.run(args, password)
        events = [json.loads(line) for line in output.getvalue().splitlines()]
        return code, events[-1], events

    async def test_mixed_workload_handles_fragments_async_events_and_relogin(self):
        server = await self.server()
        code, report, events = await self.execute(server, "--scenario", "mixed", "--files", "2", "--cycles", "2")
        self.assertEqual(code, 0, report)
        self.assertEqual(report["cycles_completed"], 2)
        for operation in ("register", "login", "share_catalogue", "search", "browse", "hotlist", "chat", "private"):
            self.assertGreater(report["operations"][operation]["completed"], 0, operation)
        self.assertEqual(sum(kind == 6 for kind, _ in server.commands), 3)
        self.assertEqual(sum(kind == 2 for kind, _ in server.commands), 3)
        self.assertEqual(sum(kind == 208 for kind, _ in server.commands), sum(kind == 303 for kind, _ in server.commands))
        self.assertEqual(events[-2]["connected"], 0)
        self.assertFalse(server.online)

    async def test_search_allows_results_above_max_results(self):
        server = await self.server()
        code, report, _ = await self.execute(server, "--scenario", "search", "--files", "3", "--max-results", "1")
        self.assertEqual(code, 0, report)
        self.assertGreater(report["received_packets"]["201"], 1)

    async def test_rejected_login_fails_and_closes_partial_setup(self):
        server = await self.server(login_error=True)
        code, report, _ = await self.execute(server)
        self.assertEqual(code, 1)
        self.assertEqual(report["status"], "failed")
        self.assertIn("Login rejected", " ".join(report["errors"]))
        await asyncio.sleep(0.03)
        self.assertFalse(server.writers)

    async def test_connection_failure_has_target_and_nonzero_status(self):
        server = await self.server()
        server.server.close()
        await server.server.wait_closed()
        code, report, _ = await self.execute(server, "--users", "1")
        self.assertEqual(code, 1)
        self.assertIn(f"connect to 127.0.0.1:{server.port}", " ".join(report["errors"]))

    async def test_error_while_idle_is_not_silently_ignored(self):
        server = await self.server(idle_error=True)
        code, report, _ = await self.execute(server, "--duration", "10")
        self.assertEqual(code, 1)
        self.assertLess(report["elapsed_seconds"], 2)
        self.assertIn("asynchronous failure", " ".join(report["errors"]))

    async def test_missing_terminator_times_out(self):
        server = await self.server(finish_search=False)
        code, report, _ = await self.execute(server, "--scenario", "search", "--timeout", "0.1")
        self.assertEqual(code, 1)
        self.assertIn("timed out", " ".join(report["errors"]))

    async def test_truncated_packet_fails(self):
        server = await self.server(truncate_search=True)
        code, report, _ = await self.execute(server, "--scenario", "search")
        self.assertEqual(code, 1)
        self.assertGreater(report["error_count"], 0)

    async def test_browse_asserts_share_count(self):
        server = await self.server(wrong_browse=True)
        code, report, _ = await self.execute(server, "--scenario", "shares", "--files", "2")
        self.assertEqual(code, 1)
        self.assertIn("expected 2 files, got 3", " ".join(report["errors"]))

    async def test_existing_account_collision_is_not_registered_again(self):
        server = await self.server()
        server.accounts.add("test000000")
        code, report, _ = await self.execute(server, "--users", "1")
        self.assertEqual(code, 1)
        self.assertIn("already exists", " ".join(report["errors"]))
        self.assertFalse(any(kind == 6 for kind, _ in server.commands))

    async def test_slow_reading_is_opt_in_and_report_is_written(self):
        server = await self.server()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            code, report, _ = await self.execute(server, "--scenario", "search", "--files", "2",
                                                 "--read-delay", "0.002", "--output", str(path))
            self.assertEqual(code, 0, report)
            self.assertEqual(json.loads(path.read_text())["status"], "passed")

    async def test_cancellation_disconnects_and_reports_interrupted(self):
        server = await self.server()
        args, password = load.parse_args(["--port", str(server.port), "--users", "1", "--ramp-up", "0",
                                          "--warmup", "0", "--duration", "100"])
        output = io.StringIO()
        with redirect_stdout(output):
            task = asyncio.create_task(load.run(args, password))
            async with asyncio.timeout(2):
                while not server.online:
                    await asyncio.sleep(0.01)
            task.cancel()
            self.assertEqual(await task, 130)
        await asyncio.sleep(0.03)
        self.assertFalse(server.writers)
        self.assertEqual(json.loads(output.getvalue().splitlines()[-1])["status"], "interrupted")


if __name__ == "__main__":
    unittest.main()
