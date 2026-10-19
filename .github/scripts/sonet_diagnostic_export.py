"""Verify that the signed diagnostic APK exports a populated, bounded report."""
import json
import re
import runpy
import time

h = runpy.run_path('.github/scripts/onyx_smoke.py')
adb, snapshot, tap, scroll = (h[k] for k in ['adb', 'snapshot', 'tap_label', 'scroll_to_label'])
package, activity, out = (h[k] for k in ['PACKAGE', 'ACTIVITY', 'OUT'])
adb('shell', 'am', 'start', '-W', '-n', activity, '-a', 'android.intent.action.VIEW',
    '-d', 'https://music.youtube.com/search?q=The%20Weeknd')
for attempt in range(35):
    search = snapshot('30-diagnostic-search-' + str(attempt))
    texts = [n.get('text', '') for n in search.iter('node')]
    if any(t in ('Top result', 'Could not load this YouTube page.') for t in texts):
        break
    time.sleep(2)
else:
    raise AssertionError('Search never produced a result or actionable error')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
tap(snapshot('30-back'), 'home')
tap(snapshot('30-home'), 'more options')
tap(snapshot('30-menu'), 'settings')
settings = scroll(snapshot('31-settings'), 'export diagnostic', '31-find-export', max_swipes=8)
tap(settings, 'export diagnostic')
for attempt in range(15):
    picker = snapshot('32-export-picker-' + str(attempt))
    if any('documentsui' in n.get('package', '') for n in picker.iter('node')):
        break
    time.sleep(.5)
else:
    raise AssertionError('Diagnostic file picker did not open')
if any('show roots' in n.get('content-desc', '').lower() for n in picker.iter('node')):
    tap(picker, 'show roots')
    locations = snapshot('32-locations')
    tap(locations, 'downloads')
picker = snapshot('32-save-location')
filename = next((n.get('text', '') for n in picker.iter('node') if 'Sonet-diagnostico-' in n.get('text', '')), None)
assert filename and filename.endswith('.txt'), 'Missing diagnostic filename'
tap(picker, 'save')
time.sleep(3)
report = adb('exec-out', 'cat', '/sdcard/Download/' + filename).decode()
(out / 'exported-diagnostic.txt').write_text(report)
assert 'Sonet network diagnostic' in report
data = json.loads(report[report.index('{'):])
assert data['build'] and data['androidSdk'] >= 26
events = data['events']
assert 0 < len(events) <= 100, 'Diagnostic report is empty or unbounded'
assert any(e['operation'] == 'search' for e in events), 'Search requests were not captured'
assert all(e['elapsedMs'] >= 0 for e in events)
assert not re.search(r'https?://|SAPISIDHASH|Bearer\s|\"(?:cookie|token|query|visitorData)\"\s*:', report, re.I), 'Sensitive material in export'
print('PASS: signed diagnostic APK exported populated redacted JSON through Android DocumentsUI')
