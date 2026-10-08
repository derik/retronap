# On-demand user simulation

`scripts/loadtest/retronap_load.py` exercises an **already running** RetroNap
server over TCP. It works with the native executable, JAR, or container. It never
starts the server, reads its database, or changes its settings. Nothing in this
directory is connected to Gradle's `test`, `check`, or `build` tasks or CI.

Requires Python **3.11+**, with no additional packages. Run commands from the
repository root. `--port` is the Napster server port, not the metaserver port.

Use a benchmark server/database: account creation persists synthetic accounts,
and existing-account mode changes those accounts' session metadata. Shared files
are synthetic metadata; no MP3s are needed and no media bytes are transferred.
The server normally deletes shares on disconnect. The driver closes its sockets
but does not delete accounts or directly verify database cleanup. Use a fresh
benchmark database or restore the same stopped-server database snapshot for
comparable measurements. Avoid using accounts that are logged in elsewhere.

## Quick start

```bash
python3 scripts/loadtest/retronap_load.py --help

# Five authenticated users, holding their connections for one minute.
python3 scripts/loadtest/retronap_load.py \
  --host 127.0.0.1 --port 18888 \
  --scenario idle --users 5 --duration 60 \
  --output build/loadtest/idle.json
```

The default account prefix is unique to each invocation and appears in the
`start` event and JSON report. Each user is named `<prefix>000000`,
`<prefix>000001`, etc. The default password is `load-test-only`; set the
`RETRONAP_LOAD_PASSWORD` environment variable to override it. Passwords are not
included in the driver's progress or report. The server's own DEBUG packet logs
may contain login payloads, so use only disposable credentials.
Passwords must be 1-72 printable ASCII characters without double quotes.

The `--output` destination must not already exist, preventing accidental loss of
an earlier result. Its parent directories are created automatically. There is no
report file unless `--output` is given; stdout always includes a final summary.

## Workloads

| Scenario | Actions during the active phase |
| --- | --- |
| `idle` | Hold authenticated connections and drain incoming messages. |
| `shares` | Hold connections after advertising `--files` files per user; requires a positive file count. |
| `search` | Repeated filename searches; wait for all results and `202`. |
| `browse` | Browse the next simulated user; check the file count and `213` nickname. |
| `hotlist` | Add the next user with `208`, check `301`, remove with `303`, then await a channel-list response as a processing barrier. |
| `chat` | Join a run-specific channel, alternate public broadcasts and private messages, and verify delivery. |
| `mixed` | Rotate through search, browse, hotlist, public chat, and private messages. |

All scenarios log in first. `--files` defaults to zero and can be used with any
scenario. Files are advertised using `870` in batches, bounded both by
`--batch-size` and by encoded payload length. Each publisher browses itself
after sharing and verifies its file count before the active phase begins.

By default a search uses the run's prefix, which is included in every generated
filename. The driver checks that it receives `users × files` results. The current
protocol returns all matching files even when this exceeds `--max-results`;
that argument is **not** a server-memory or response-size limit. A custom
`--search-term` disables the exact result-count assertion, but still checks errors
and completion. Keep prefixes distinct from other online benchmark users when
using the default assertion. With zero files, the default search checks the
empty-result path.

These are workload checks, not exhaustive conformance tests. They check response
markers, selected ordering, counts, and message delivery, but do not validate
every field of every result. They do not simulate actual peer-to-peer downloads,
administrative commands, or deliberately malformed client requests.

## Memory measurement examples

```bash
# Per-connection overhead: baseline, connect gradually, then hold users online.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --scenario idle --users 100 --baseline 30 --ramp-up 30 \
  --duration 300 --cooldown 60 --output build/loadtest/users-100.json

# Catalogue overhead: 10,000 shared-file records held online.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --scenario shares --users 10 --files 1000 \
  --duration 300 --cooldown 60 --output build/loadtest/shares-10000.json

# Search allocations and response traffic: each user searches once per loop.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --scenario search --users 10 --files 100 --interval 2 \
  --duration 120 --output build/loadtest/search.json

# Repeated connect/share/interact/disconnect cycles, reusing the same accounts.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --scenario mixed --users 10 --files 100 --cycles 3 \
  --duration 60 --cooldown 30 --output build/loadtest/cycles.json

# Explicit slow-reader experiment. Give the entire response time to complete.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --scenario browse --users 2 --files 1000 --read-delay 0.01 \
  --timeout 60 --duration 60 --output build/loadtest/slow-readers.json
```

Run a process memory sampler separately against the **server PID**. For example,
in a second terminal with `sysstat` installed, substitute the server's PID:

```bash
pidstat -r -u -p SERVER_PID 1 > build/loadtest/server-memory.txt
```

The report measures client-observed operations and wire traffic, not server RAM.
Align its timestamped phase events with RSS/PSS/thread measurements. Record the
server commit, Java/native-image version, flags, database starting state and
container limits alongside the output. The load generator runs in a separate
process; measure its CPU too if it may be limiting the achieved load. For larger
runs, it can run on another machine using `--host`.

## Reproducing the native-memory benchmark

On Linux, two additional opt-in scripts reproduce the exploratory six-scenario
matrix and collect server memory separately from client memory. They require
Python 3.11+ and an existing native executable at
`build/native/nativeCompile/retronap`; they do not compile it.

```bash
python3 -B scripts/loadtest/collect_native_memory.py

# Replace the directory with the one printed by the collector.
python3 -B scripts/loadtest/summarize_native_memory.py \
  build/benchmarks/native-YYYYMMDD-HHMMSS
```

The collector starts and stops a fresh native server for each case, with its own
file-backed H2 database and dynamically selected server/metaserver ports. This
wrapper manages servers; the `retronap_load.py` client above still targets only
an already running server. Server/Java configuration environment variables are
cleared for each case, preserving compiled defaults for heap, logging, cache and
pool settings. No container limits or forced GC are applied.

The matrix takes about eight minutes on the original test host:

| Case | Users | Files per user | Active seconds per cycle |
| --- | ---: | ---: | ---: |
| Idle | 10 | 0 | 30 |
| Idle | 100 | 0 | 70 |
| Shares | 10 | 1,000 | 30 |
| Search | 10 | 100 | 30 |
| Mixed, three cycles | 10 | 100 | 15 |
| Browse with 5 ms delay per received frame | 2 | 1,000 | 20 |

Each case includes a 10-second baseline, 3-second warmup and 15-second cooldown
(10 seconds for mixed cycles). Edit `CASES` in `collect_native_memory.py` to
change this fixed matrix, or use the parameterized client directly for custom
workloads. The collector stops a case if server RSS exceeds 2 GiB, available
host RAM falls below 2 GiB, its server log exceeds 256 MiB, or it takes over
240 seconds. It exits nonzero on a failed or interrupted run.

RSS, PSS, private memory, swap, OS threads and process CPU usage are sampled from
Linux `/proc` every 0.5 seconds. Timestamped phase events identify baseline,
setup, activity and disconnect recovery. Results live in a new directory under
`build/benchmarks/`; the summary script creates `report.html` and `summary.json`.
Per-case directories contain exact commands, raw samples, workload reports,
server logs and the isolated database. These generated artifacts are ignored by
Git and removed by `gradlew clean`; the scripts remain in `scripts/loadtest/`.

These short host runs are useful for comparisons, but do not establish maximum
capacity, container memory requirements or absence of long-term leaks. Retained
RSS after disconnect may include heap capacity and allocator pages.

## Timing and concurrency

Each run has a `baseline` pause followed by one or more cycles:

1. `connecting`: stagger connection starts over `--ramp-up` seconds; at most
   `--setup-concurrency` logins execute concurrently. This includes account
   creation on the first cycle when using `--accounts create`.
2. `sharing`: advertise and verify each user's catalogue, using the same
   setup-concurrency limit. Skipped when `--files 0`.
3. `joining_channel`: chat/mixed users join a unique room, sequentially to finish
   room creation and consume each initial member list.
4. `warmup`: keep clients online and drain replies for `--warmup` seconds.
5. `active`: run the selected workload for `--duration` seconds. This duration
   excludes setup, warmup and cooldown. An already started action is allowed to
   finish, bounded by request timeouts. No new action starts after the deadline.
6. `disconnecting`: close every client socket, including after a partial setup
   failure or interruption.
7. `cooldown`: wait `--cooldown` seconds for server cleanup and memory observation,
   including after the final cycle. This pause is not proof that server cleanup
   has finished; increase it if cleanup needs more time before the next cycle.

Each user runs actions sequentially; users operate concurrently. `--interval`
is the pause **after** each action, not an exact rate limit. Latency lowers the
achieved rate. `--interval 0` runs as fast as responses permit. `--timeout` bounds
connection establishment and complete request/response exchanges, including all
search/browse rows. It is not reset by each arriving packet.

Every connection has a continuous receiver. Results and asynchronous MOTD,
statistics, presence and broadcast messages are counted and discarded rather
than saved in unbounded client-side queues. Only aggregate latency statistics
and up to 20 errors are retained. The optional `--read-delay` applies only during
the active phase and delays reading each frame. Kernel/asyncio buffers must fill
before that delay creates backpressure at the server, so a small catalogue may
not produce server queue growth. Normal measurements should keep this at zero.

## Reusing benchmark accounts

```bash
# First invocation: creates bench000000 ... bench000009.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --accounts create --prefix bench --users 10 --scenario idle --duration 30

# Later invocation: authenticate the same accounts using the same password.
python3 scripts/loadtest/retronap_load.py --port 18888 \
  --accounts existing --prefix bench --users 10 \
  --scenario search --files 100 --duration 60
```

Create mode checks nickname availability before registration and fails if a name
already exists. Existing mode requires an explicit prefix and never creates
accounts. In a multi-cycle run, only the first cycle creates accounts; later
cycles log into them with `2`. To investigate growth across **distinct** users
(such as per-user hotlist state), run multiple invocations with fresh prefixes.
Hotlist removals are confirmed indirectly by a subsequent command completing;
an interrupted or failed action may leave a persisted hotlist entry. Accounts and
any such entries remain in the benchmark database until it is reset externally.

## Results and exit codes

Stdout is JSON Lines: `start`, timestamped `phase` changes, periodic `progress`,
then `summary`. Redirect it to a `.jsonl` file to preserve the phase timeline.
`--progress-interval` controls progress frequency. The optional JSON report
contains parameters, completion status, completed cycles, operation successes,
failures/cancellations, mean/maximum successful latency, packet counts by type,
wire-byte totals and bounded error details. Registration/setup operations are
reported separately from active actions. `share_catalogue` latency includes its
verification browse, which is also reported as a browse operation.

- Exit `0`: selected workload completed and its assertions passed.
- Exit `1`: connection/protocol/assertion/timeout failure or report I/O failure.
- Exit `2`: invalid command-line parameters.
- Exit `130`: interrupted with Ctrl+C or SIGTERM; sockets close and a partial
  report is written when possible.

Unexpected EOF, truncated frames, and server errors `0`, `404`, or `302` fail the
run even during idle/warmup periods. One failure cancels the remaining workload
and closes all clients. A successful run does not certify the absence of a
memory leak; compare repeated cycles and server-side memory measurements.

## Checking the load-test client itself

The client has a separate opt-in check suite using disposable loopback TCP peers:

```bash
python3 -B -m unittest discover -s scripts/loadtest -p 'test_*.py' -v
```

These checks cover fragmented framing, wire-size limits, interleaved asynchronous
events, login refusal, duplicate names, missing terminators, truncated responses,
file-count assertions, multi-cycle login, slow readers and cancellation cleanup.
They need neither a RetroNap server nor its database, and are also excluded from
normal Gradle builds.
