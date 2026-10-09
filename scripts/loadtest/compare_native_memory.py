"""Compare two completed native-memory matrices and write an HTML/JSON report."""

import argparse
import html
import json
from pathlib import Path
import statistics


def metrics(case):
    phases = case['phases']
    active = [p for p in phases if p['phase'] == 'active']
    baseline = next(p for p in phases if p['phase'] == 'baseline')
    cooldown = [p for p in phases if p['phase'] == 'cooldown']
    return {
        'baseline_rss_mib': baseline['tail_median_rss_mib'],
        'active_rss_mib': statistics.median(p['median_rss_mib'] for p in active),
        'peak_rss_mib': case['peak_rss_mib'],
        'after_disconnect_rss_mib': cooldown[-1]['tail_median_rss_mib'],
        'active_threads': max(p['max_threads'] for p in active),
        'server_log_bytes': case['server_log_bytes'],
        'operations': case['workload']['operations'],
    }


def compare(before, after):
    old_cases = {c['name']: c for c in before['cases']}
    new_cases = {c['name']: c for c in after['cases']}
    if old_cases.keys() != new_cases.keys() or not old_cases:
        raise ValueError('Runs must contain the same nonempty set of scenarios')
    rows = []
    for name, old in old_cases.items():
        new = new_cases[name]
        if old['parameters'] != new['parameters']:
            raise ValueError(f'{name}: workload parameters differ')
        for case in (old, new):
            if case['status'] != 'passed' or case['workload']['error_count']:
                raise ValueError(f'{name}: only successful runs without workload errors can be compared')
        a, b = metrics(old), metrics(new)
        rows.append({
            'case': name, 'before': a, 'after': b,
            'active_rss_change_mib': b['active_rss_mib'] - a['active_rss_mib'],
            'active_rss_change_percent': (b['active_rss_mib'] / a['active_rss_mib'] - 1) * 100,
        })
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path, help='baseline directory containing results.json')
    parser.add_argument('after', type=Path, help='candidate directory containing results.json')
    parser.add_argument('--output', type=Path, help='HTML destination; default: AFTER/comparison.html')
    args = parser.parse_args()
    before = json.loads((args.before / 'results.json').read_text())
    after = json.loads((args.after / 'results.json').read_text())
    try:
        rows = compare(before, after)
    except (KeyError, StopIteration, ValueError) as error:
        parser.error(str(error) or 'Incomplete phase data')
    output = args.output or args.after / 'comparison.html'
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'before_directory': str(args.before.resolve()),
              'after_directory': str(args.after.resolve()),
              'before_metadata': before['metadata'], 'after_metadata': after['metadata'],
              'cases': rows}
    output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    rendered = []
    for row in rows:
        a, b = row['before'], row['after']
        rendered.append(
            f'<tr><td>{html.escape(row["case"])}</td>'
            f'<td>{a["baseline_rss_mib"]:.1f} → {b["baseline_rss_mib"]:.1f}</td>'
            f'<td>{a["active_rss_mib"]:.1f} → {b["active_rss_mib"]:.1f}</td>'
            f'<td>{row["active_rss_change_percent"]:+.1f}%</td>'
            f'<td>{a["peak_rss_mib"]:.1f} → {b["peak_rss_mib"]:.1f}</td>'
            f'<td>{a["active_threads"]} → {b["active_threads"]}</td>'
            f'<td>{a["server_log_bytes"] / 1024:.1f} → {b["server_log_bytes"] / 1024:.1f}</td></tr>')
    operations = []
    for row in rows:
        for name, old_op in row['before']['operations'].items():
            new_op = row['after']['operations'].get(name)
            if not new_op or old_op['mean_ms'] is None or new_op['mean_ms'] is None:
                continue
            operations.append(
                f'<tr><td>{html.escape(row["case"])}</td><td>{html.escape(name)}</td>'
                f'<td>{old_op["completed"]} → {new_op["completed"]}</td>'
                f'<td>{old_op["mean_ms"]:.2f} → {new_op["mean_ms"]:.2f}</td></tr>')
    output.write_text('''<!doctype html><html lang="en"><meta charset="utf-8">
<title>RetroNap memory comparison</title><style>
body{font:16px/1.5 system-ui,sans-serif;max-width:1250px;margin:36px auto;padding:0 24px;color:#18232b}
table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:10px;text-align:right;border-bottom:1px solid #d8e2e7}
th:first-child,td:first-child{text-align:left}th{background:#eef4f7}.scroll{overflow-x:auto}code{overflow-wrap:anywhere}
</style><h1>RetroNap memory comparison</h1>
<p>Each cell shows before → after. Memory values are MiB; log sizes are KiB. All scenarios passed with zero workload errors.</p>
<div class="scroll"><table><tr><th>Scenario</th><th>Baseline RSS</th><th>Active RSS</th><th>Active change</th>
<th>Peak RSS</th><th>Peak active threads</th><th>Server log size</th></tr>'''
        + ''.join(rendered) + '''</table></div>
<p>Active RSS is the median during activity (median of cycle medians for mixed).
Peak RSS spans startup through cooldown, sampled every 0.5 seconds. Threads are OS threads.
The load-generator process is excluded from server memory.</p>
<p>These are exploratory host measurements, one matrix per executable, with identical workload parameters
and fresh databases. They are not replicated performance trials or container-capacity guarantees.
Changes are measured together; individual contributions are not isolated. Retained RSS alone does not establish a leak.
Operation counts are included because client pacing depends on response latency.</p>
<h2>Operation counts and latency</h2><div class="scroll"><table><tr><th>Scenario</th><th>Operation</th>
<th>Completed</th><th>Mean milliseconds</th></tr>'''
        + ''.join(operations) + '</table></div><h2>Recorded inputs</h2><pre><code>'
        + html.escape(json.dumps({k: v for k, v in report.items() if k != 'cases'}, indent=2))
        + '</code></pre></html>')
    print(json.dumps(rows, indent=2))
    print('Comparison:', output)


if __name__ == '__main__':
    main()
