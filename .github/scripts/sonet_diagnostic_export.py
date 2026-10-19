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
for attempt in range(6):
    navigation = snapshot('30-back-' + str(attempt))
    if any(n.get('text', '').lower() == 'home' for n in navigation.iter('node')):
        break
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1)
else:
    raise AssertionError('Could not return from nested pages to bottom navigation')
tap(navigation, 'home')
tap(snapshot('30-home'), 'more options')
tap(snapshot('30-menu'), 'settings')
settings = scroll(snapshot('31-settings'), 'export diagnostic', '31-find-export', max_swipes=8)
probe = scroll(settings, 'run connection test', '31-find-connection-test', max_swipes=8)
tap(probe, 'run connection test')
for attempt in range(50):
    state = snapshot('31-probe-running-' + str(attempt))
    if any('Connection test complete' in n.get('text', '') for n in state.iter('node')):
        break
    time.sleep(2)
else:
    raise AssertionError('Controlled connection test did not complete')
settings = scroll(state, 'export diagnostic', '31-find-export-after-probe', max_swipes=8)
tap(settings, 'export diagnostic')
for attempt in range(15):
    picker = snapshot('32-export-picker-' + str(attempt))
    if any('documentsui' in n.get('package', '') for n in picker.iter('node')):
        break
    time.sleep(.5)
else:
    raise AssertionError('Diagnostic file picker did not open')
def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)

if not any(n.get('resource-id', '').endswith('/breadcrumb_text') and n.get('text') == 'Downloads'
           for n in picker.iter('node')):
    tap(picker, 'show roots')
    locations = snapshot('32-locations')
    downloads = next(n for n in locations.iter('node')
                     if n.get('resource-id') == 'android:id/title' and n.get('text') == 'Downloads')
    tap_node(downloads)
picker = snapshot('32-folder-selected')
if any(n.get('text') == 'Save to' for n in picker.iter('node')):
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1)
picker = snapshot('32-save-location')
filename = next((n.get('text', '') for n in picker.iter('node') if 'Sonet-diagnostico-' in n.get('text', '')), None)
assert filename and filename.endswith('.txt'), 'Missing diagnostic filename'
save = next(n for n in picker.iter('node') if n.get('resource-id') == 'android:id/button1' and n.get('enabled') == 'true')
tap_node(save)
for attempt in range(20):
    if adb('shell', 'ls', '/sdcard/Download/' + filename, check=False).strip():
        break
    time.sleep(.5)
else:
    snapshot('33-export-not-created')
    raise AssertionError('Save did not create the diagnostic file')
report = adb('exec-out', 'cat', '/sdcard/Download/' + filename).decode()
(out / 'exported-diagnostic.txt').write_text(report)
assert 'Sonet network diagnostic' in report
data = json.loads(report[report.index('{'):])
assert data['build'] and data['androidSdk'] >= 26
probe = data['connectionProbe']
assert len(probe) == 8, 'Controlled matrix did not export all eight cases'
assert {p['transport'] for p in probe} == {'AUTO', 'HTTP1'}
assert {p['visitorMode'] for p in probe} == {'CURRENT', 'NONE'}
assert all(p['marker'] in {'NONE','UNKNOWN','AUTOMATION_CHALLENGE','CONSENT','GOOGLE_PERMISSION','ACCESS_DENIED','PERMISSION_DENIED','UNAUTHENTICATED','RESOURCE_EXHAUSTED','INVALID_ARGUMENT','FAILED_PRECONDITION'} for p in probe)
events = data['events']
assert 0 < len(events) <= 100, 'Diagnostic report is empty or unbounded'
assert any(e['operation'] == 'search' for e in events), 'Search requests were not captured'
assert all(e['elapsedMs'] >= 0 for e in events)
assert not re.search(r'https?://|SAPISIDHASH|Bearer\s|\"(?:cookie|token|query|visitorData)\"\s*:', report, re.I), 'Sensitive material in export'
print('PASS: signed diagnostic APK exported populated redacted JSON through Android DocumentsUI')
