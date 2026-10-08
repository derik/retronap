"""Reproduce the exploratory native-memory matrix; results live under build/."""

import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import socket
import statistics
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
BINARY = ROOT / 'build/native/nativeCompile/retronap'
OUT = ROOT / 'build/benchmarks' / datetime.now().strftime('native-%Y%m%d-%H%M%S')
OUT.mkdir(parents=True)
TICKS = os.sysconf('SC_CLK_TCK')
STOP = threading.Event()
CASES = [
    ('idle-10', dict(scenario='idle', users=10, files=0, duration=30, ramp_up=2)),
    ('idle-100', dict(scenario='idle', users=100, files=0, duration=70, ramp_up=5)),
    ('shares-10000', dict(scenario='shares', users=10, files=1000, duration=30, ramp_up=2)),
    ('search-1000', dict(scenario='search', users=10, files=100, duration=30, ramp_up=2, interval=2)),
    ('mixed-3-cycles', dict(scenario='mixed', users=10, files=100, duration=15, ramp_up=2, cycles=3, cooldown=10)),
    ('slow-browse', dict(scenario='browse', users=2, files=1000, duration=20, ramp_up=1, read_delay=.005)),
]


def now():
    return datetime.now(timezone.utc).isoformat(timespec='milliseconds')


def numeric_fields(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        key, _, value = line.partition(':')
        parts = value.split()
        if parts and parts[0].isdigit():
            values[key] = int(parts[0])
    return values


def process_sample(pid):
    status = numeric_fields(f'/proc/{pid}/status')
    memory = numeric_fields(f'/proc/{pid}/smaps_rollup')
    stat = Path(f'/proc/{pid}/stat').read_text().rsplit(') ', 1)[1].split()
    return {
        'rss_mib': memory['Rss'] / 1024,
        'pss_mib': memory['Pss'] / 1024,
        'private_mib': (memory.get('Private_Clean', 0) + memory.get('Private_Dirty', 0)) / 1024,
        'swap_mib': memory.get('Swap', 0) / 1024,
        'rss_hwm_mib': status.get('VmHWM', 0) / 1024,
        'threads': status['Threads'],
        'cpu_seconds': (int(stat[11]) + int(stat[12])) / TICKS,
    }


def stop_process(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def reserve_ports():
    sockets = [socket.socket(), socket.socket()]
    try:
        for s in sockets:
            s.bind(('127.0.0.1', 0))
        return [s.getsockname()[1] for s in sockets]
    finally:
        for s in sockets:
            s.close()


def execute_case(name, overrides):
    folder = OUT / name
    folder.mkdir()
    port, meta_port = reserve_ports()
    parameters = dict(baseline=10, warmup=3, cooldown=15, timeout=30,
                      interval=1, setup_concurrency=5, progress_interval=10)
    parameters.update(overrides)
    server_command = [str(BINARY), f'--retronap.server.port={port}',
                      f'--retronap.metaserver.port={meta_port}',
                      f'--retronap.metaserver.server-list=127.0.0.1:{port}']
    client_command = [sys.executable, '-B', str(ROOT / 'scripts/loadtest/retronap_load.py'),
                      '--host', '127.0.0.1', '--port', str(port), '--prefix', 'memory',
                      '--output', str(folder / 'workload.json')]
    for key, value in parameters.items():
        client_command += ['--' + key.replace('_', '-'), str(value)]
    env = {key: value for key, value in os.environ.items()
           if not key.startswith(('SPRING_', 'RETRONAP_', 'JAVA_', 'JDK_', '_JAVA', 'GRAALVM_'))}
    env['RETRONAP_FILE_DATABASE'] = str(folder / 'database')
    (folder / 'commands.json').write_text(json.dumps({
        'server': server_command, 'client': client_command,
        'database': env['RETRONAP_FILE_DATABASE']}, indent=2))
    client = server = None
    sampler = None
    sampler_stop = threading.Event()
    state = {'phase': 'startup', 'cycle': 0, 'connected': 0, 'guard': None, 'client_pid': None}
    samples = []
    started = time.monotonic()

    def sample():
        previous = {}
        with (folder / 'samples.jsonl').open('w') as output:
            while not sampler_stop.is_set():
                sampled_at = time.monotonic()
                row = {'at': now(), 'elapsed_seconds': sampled_at - started,
                       'phase': state['phase'], 'cycle': state['cycle'],
                       'connected': state['connected']}
                for label, pid in [('server', server.pid), ('client', state['client_pid'])]:
                    if pid is None:
                        continue
                    try:
                        values = process_sample(pid)
                        old = previous.get(label)
                        values['cpu_percent'] = ((values['cpu_seconds'] - old[1]) /
                                                 (sampled_at - old[0]) * 100) if old else 0
                        previous[label] = (sampled_at, values['cpu_seconds'])
                        row[label] = values
                    except (OSError, KeyError, ProcessLookupError):
                        continue
                if 'server' in row:
                    samples.append(row)
                    output.write(json.dumps(row) + '\n')
                    output.flush()
                    available = numeric_fields('/proc/meminfo').get('MemAvailable', 0)
                    if row['server']['rss_mib'] > 2048 or available < 2 * 1024 * 1024:
                        state['guard'] = 'memory guard reached: server RSS >2 GiB or host available RAM <2 GiB'
                    if (folder / 'server.log').stat().st_size > 256 * 1024 * 1024:
                        state['guard'] = 'server log exceeded 256 MiB'
                    if sampled_at - started > 240:
                        state['guard'] = 'case exceeded 240 seconds'
                    if state['guard'] or STOP.is_set():
                        if client is not None and client.poll() is None:
                            client.terminate()
                        if server.poll() is None:
                            server.terminate()
                        break
                sampler_stop.wait(.5)

    print(json.dumps({'event': 'case_start', 'case': name, 'at': now()}), flush=True)
    failure = None
    try:
        with (folder / 'server.log').open('w') as server_log, (folder / 'client.stderr').open('w') as client_error:
            server = subprocess.Popen(server_command, cwd=ROOT, env=env,
                                      stdout=server_log, stderr=subprocess.STDOUT)
            sampler = threading.Thread(target=sample, daemon=True)
            sampler.start()
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                if server.poll() is not None:
                    raise RuntimeError(f'server exited {server.returncode} during startup')
                if 'Starting Napster Server' in (folder / 'server.log').read_text(errors='replace'):
                    time.sleep(.3)
                    break
                time.sleep(.1)
            else:
                raise RuntimeError('startup timed out')
            client = subprocess.Popen(client_command, cwd=ROOT, env=env, text=True,
                                      stdout=subprocess.PIPE, stderr=client_error, bufsize=1)
            state['client_pid'] = client.pid
            with (folder / 'events.jsonl').open('w') as events:
                for line in client.stdout:
                    events.write(line)
                    events.flush()
                    event = json.loads(line)
                    if event['event'] == 'phase':
                        state.update({key: event[key] for key in ('phase', 'cycle', 'connected')})
                    elif event['event'] == 'progress':
                        state['connected'] = event['connected']
                    if event['event'] == 'phase' and event['phase'] in ('active', 'cooldown'):
                        print(json.dumps({'event': 'phase', 'case': name, **event}), flush=True)
            client.wait(timeout=10)
            if client.returncode:
                failure = f'workload exited {client.returncode}'
    except Exception as error:
        failure = str(error)
    finally:
        sampler_stop.set()
        if sampler:
            sampler.join(timeout=3)
        stop_process(client)
        stop_process(server)
    if state['guard']:
        failure = state['guard']
    workload = json.loads((folder / 'workload.json').read_text()) if (folder / 'workload.json').exists() else {}
    groups = {}
    for row in samples:
        groups.setdefault((row['phase'], row['cycle']), []).append(row)
    phases = []
    for (phase, cycle), rows in groups.items():
        values = [r['server'] for r in rows]
        tail = [r['server'] for r in rows if r['elapsed_seconds'] >= rows[-1]['elapsed_seconds'] - 5]
        phases.append({
            'phase': phase, 'cycle': cycle, 'samples': len(rows),
            'start_seconds': round(rows[0]['elapsed_seconds'], 3),
            'end_seconds': round(rows[-1]['elapsed_seconds'], 3),
            'median_rss_mib': statistics.median(v['rss_mib'] for v in values),
            'peak_rss_mib': max(v['rss_mib'] for v in values),
            'tail_median_rss_mib': statistics.median(v['rss_mib'] for v in tail),
            'tail_median_pss_mib': statistics.median(v['pss_mib'] for v in tail),
            'median_threads': statistics.median(v['threads'] for v in values),
            'max_threads': max(v['threads'] for v in values),
            'mean_cpu_percent': statistics.mean(v['cpu_percent'] for v in values),
            'peak_swap_mib': max(v['swap_mib'] for v in values),
        })
    report = {'name': name, 'status': 'failed' if failure else workload.get('status', 'unknown'),
              'failure': failure, 'parameters': parameters, 'phases': phases, 'workload': workload,
              'peak_rss_mib': max((r['server']['rss_mib'] for r in samples), default=0),
              'rss_hwm_mib': max((r['server']['rss_hwm_mib'] for r in samples), default=0),
              'client_peak_rss_mib': max((r.get('client', {}).get('rss_mib', 0) for r in samples), default=0),
              'server_log_bytes': (folder / 'server.log').stat().st_size,
              'server_shutdown_code': server.returncode if server else None}
    (folder / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'event': 'case_complete', 'case': name, 'status': report['status'],
                      'peak_rss_mib': round(report['peak_rss_mib'], 1),
                      'active': [p for p in phases if p['phase'] == 'active'],
                      'errors': workload.get('errors', [])}), flush=True)
    return report


def main():
    signal.signal(signal.SIGTERM, lambda *_: STOP.set())
    signal.signal(signal.SIGINT, lambda *_: STOP.set())
    metadata = {
        'started_at': now(), 'platform': platform.platform(), 'logical_cpus': os.cpu_count(),
        'cpu_affinity': sorted(os.sched_getaffinity(0)), 'meminfo_start_kib': numeric_fields('/proc/meminfo'),
        'git_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'binary_path': str(BINARY), 'binary_bytes': BINARY.stat().st_size,
        'binary_modified_at': datetime.fromtimestamp(BINARY.stat().st_mtime, timezone.utc).isoformat(),
        'binary_sha256': hashlib.file_digest(BINARY.open('rb'), 'sha256').hexdigest(),
        'load_client_sha256': hashlib.file_digest((ROOT / 'scripts/loadtest/retronap_load.py').open('rb'), 'sha256').hexdigest(),
        'methodology': 'Existing native binary; one fresh process and file-backed H2 database per case; default heap/GC/logging/cache/pool settings; metaserver enabled on separate port. No container or CPU limit. Process smaps_rollup RSS/PSS sampled every 0.5 s; no forced GC. One exploratory run per case, not replicated trials. Client runs in a separate process on the same host. Cooldown is 15 s except mixed cycles (10 s).',
    }
    (OUT / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps({'event': 'output', 'directory': str(OUT)}), flush=True)
    results = []
    for name, parameters in CASES:
        if STOP.is_set():
            break
        results.append(execute_case(name, parameters))
        (OUT / 'results.json').write_text(json.dumps({'metadata': metadata, 'cases': results}, indent=2) + '\n')
    print(json.dumps({'event': 'finished', 'directory': str(OUT),
                      'passed': sum(r['status'] == 'passed' for r in results), 'cases': len(results)}), flush=True)
    if STOP.is_set():
        return 130
    return 0 if all(r['status'] == 'passed' for r in results) else 1


if __name__ == '__main__':
    sys.exit(main())
