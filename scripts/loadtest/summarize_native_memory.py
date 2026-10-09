import html
import json
from datetime import datetime
from pathlib import Path
import statistics
import sys

folder = Path(sys.argv[1]).resolve()
data = json.loads((folder / 'results.json').read_text())
local_started = datetime.fromisoformat(data['metadata']['started_at']).astimezone()
rows = []
cycle_rows = []
for case in data['cases']:
    baseline = next((p for p in case['phases'] if p['phase'] == 'baseline'), {})
    active = [p for p in case['phases'] if p['phase'] == 'active']
    cooldown = [p for p in case['phases'] if p['phase'] == 'cooldown']
    row = {
        'case': case['name'], 'status': case['status'],
        'users': case['parameters']['users'], 'files_per_user': case['parameters']['files'],
        'baseline_rss_mib': baseline.get('tail_median_rss_mib'),
        'active_rss_mib': statistics.median(p['median_rss_mib'] for p in active) if active else None,
        'active_pss_mib': statistics.median(p['tail_median_pss_mib'] for p in active) if active else None,
        'peak_rss_mib': case['peak_rss_mib'],
        'after_disconnect_rss_mib': cooldown[-1]['tail_median_rss_mib'] if cooldown else None,
        'active_threads': max(p['max_threads'] for p in active) if active else None,
        'baseline_threads': baseline.get('median_threads'),
        'after_disconnect_threads': cooldown[-1]['median_threads'] if cooldown else None,
        'error_count': case['workload'].get('error_count'),
        'client_peak_rss_mib': case['client_peak_rss_mib'],
    }
    rows.append(row)
    if len(active) > 1:
        for phase in active:
            after = next((p for p in cooldown if p['cycle'] == phase['cycle']), {})
            cycle_rows.append({'cycle': phase['cycle'], 'active_rss_mib': phase['median_rss_mib'],
                               'after_disconnect_rss_mib': after.get('tail_median_rss_mib'),
                               'active_threads': phase['max_threads'],
                               'after_disconnect_threads': after.get('median_threads')})

(folder / 'summary.json').write_text(json.dumps({'cases': rows, 'mixed_cycles': cycle_rows}, indent=2) + '\n')


def value(item):
    if item is None:
        return '—'
    if isinstance(item, float):
        return f'{item:.1f}'
    return html.escape(str(item))


def table(headers, records):
    return ('<table><thead><tr>' + ''.join(f'<th>{html.escape(label)}</th>' for _, label in headers)
            + '</tr></thead><tbody>'
            + ''.join('<tr>' + ''.join(f'<td>{value(record.get(key))}</td>' for key, _ in headers)
                      + '</tr>' for record in records) + '</tbody></table>')


headers = [('case', 'Workload'), ('users', 'Users'), ('files_per_user', 'Files / user'),
           ('baseline_rss_mib', 'Baseline RSS'), ('active_rss_mib', 'Active RSS'),
           ('peak_rss_mib', 'Peak RSS'), ('after_disconnect_rss_mib', 'After disconnect'),
           ('active_threads', 'Peak active threads'), ('status', 'Result')]
parts = [f'''<!doctype html><html lang="en"><meta charset="utf-8">
<title>RetroNap native memory baseline</title>
<style>body{{font:16px/1.55 system-ui,sans-serif;max-width:1200px;margin:36px auto;padding:0 24px;color:#18232b}}
h1{{font-size:30px}}h2{{margin-top:32px;font-size:22px}}table{{border-collapse:collapse;width:100%;font-size:14px;margin:20px 0}}
th,td{{padding:10px;text-align:right;border-bottom:1px solid #d8e2e7}}th:first-child,td:first-child{{text-align:left}}
th{{background:#eef4f7}}code{{font-size:13px;overflow-wrap:anywhere}}a{{color:#126593}}
.note{{background:#f3f6f8;border-left:4px solid #7798ab;padding:12px 18px}}.scroll{{overflow-x:auto}}</style>
<h1>RetroNap native memory baseline</h1>
<p>Collected {local_started.strftime('%Y-%m-%d %H:%M:%S %Z')}. All memory values below are MiB.</p>
<div class="note">Exploratory measurements: one run per scenario, on the desktop host with default runtime settings.
These are not results for the 512 MiB / 1 CPU Docker deployment.</div>
<div class="scroll">{table(headers, rows)}</div>
<p>Baseline and post-disconnect columns use the median of the last five seconds of their respective phases.
Active RSS is the median during activity; for the three-cycle case it is the median of the three cycle medians.
Peak RSS is the maximum sample across startup, setup, activity and cooldown, sampled every 0.5 seconds.
Peak active threads counts OS threads, not virtual threads.</p>''']
if cycle_rows:
    parts += ['<h2>Mixed workload: repeated cycles</h2>', table([
        ('cycle', 'Cycle'), ('active_rss_mib', 'Active RSS'),
        ('after_disconnect_rss_mib', 'After disconnect'), ('active_threads', 'Peak active threads'),
        ('after_disconnect_threads', 'Median cooldown threads')], cycle_rows)]
parts += ['<h2>Method and limits</h2>', '<p>' + html.escape(data['metadata']['methodology']) + '</p>',
          '''<p>RSS includes resident heap, stacks, runtime data and mapped code. PSS and private memory are also
          recorded in the raw samples. Retained RSS after disconnect is not by itself proof of a leak: a
          runtime can retain heap capacity and allocator pages. No live-object or heap attribution was performed.</p>''',
          '''<p>The 100-user idle phase lasts 70 seconds and includes periodic statistics. Other active phases last
          15–30 seconds. Startup/account creation/sharing are measured but reported separately from active latency.
          Slow reading is a bounded browse experiment; this run does not prove the maximum size of server queues.</p>''',
          '''<p>The executable's compiled logging defaults were used. Fresh accounts remain only in the isolated
          database for each case. Each test server was stopped after its case. The load client ran on the same machine
          but was measured as a separate process. Existing desktop activity can affect timings.</p>''',
          '<p>Source HEAD: <code>' + html.escape(data['metadata']['git_commit']) + '</code><br>'
          'Source working tree modified: ' + str(data['metadata'].get('git_worktree_dirty', 'not recorded')) + '<br>'
          'Existing native binary SHA-256: <code>' + data['metadata']['binary_sha256'] + '</code><br>'
          'Binary modified: ' + data['metadata']['binary_modified_at'] + '<br>'
          'Host: ' + html.escape(data['metadata']['platform']) + ', '
          + str(data['metadata']['logical_cpus']) + ' logical CPUs.</p>',
          '<h2>Workload results and raw data</h2>']
for case in data['cases']:
    name = case['name']
    parts.append(f'<h3>{html.escape(name)}</h3><p><a href="{name}/workload.json">Workload report</a> · '
                 f'<a href="{name}/samples.jsonl">Memory samples</a> · '
                 f'<a href="{name}/events.jsonl">Phase timeline</a> · '
                 f'<a href="{name}/commands.json">Exact commands</a></p>')
    ops = [{'operation': name, **stats} for name, stats in case['workload'].get('operations', {}).items()]
    parts.append(table([('operation', 'Operation'), ('completed', 'Completed'), ('failed', 'Failed'),
                        ('cancelled', 'Cancelled'), ('mean_ms', 'Mean ms'), ('max_ms', 'Max ms')], ops))
parts.append('<p><a href="results.json">Full results</a> · <a href="metadata.json">Host and artifact metadata</a> · '
             '<a href="summary.json">Compact summary</a></p></html>')
(folder / 'report.html').write_text('\n'.join(parts))
print(json.dumps({'cases': rows, 'mixed_cycles': cycle_rows}, indent=2))
print('Report:', folder / 'report.html')
