"""Read-only anonymous Music queries in the existing signed Sonet release."""
import json
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path('network-probe')
OUT.mkdir(exist_ok=True)
PACKAGE = 'com.onyx.music'
ACTIVITY = PACKAGE + '/com.metrolist.music.MainActivity'


def adb(*args, check=True):
    return subprocess.run(['adb', *args], capture_output=True, check=check, timeout=45).stdout


def capture(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/network-window.xml')
    xml = adb('exec-out', 'cat', '/sdcard/network-window.xml')
    (OUT / (name + '.xml')).write_bytes(xml)
    root = ET.fromstring(xml)
    return list(dict.fromkeys(n.get('text') for n in root.iter('node') if n.get('text')))


apk = next(Path('dist').rglob('Sonet.apk'))
adb('install', '-r', str(apk))
adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS', check=False)
adb('logcat', '-c')
results = []
try:
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    time.sleep(12)
    results.append({'page': 'home', 'texts': capture('home')})
    for index, query in enumerate(['The Weeknd', 'Mia Boyka', 'Cult Member']):
        url = 'https://music.youtube.com/search?q=' + urllib.parse.quote(query)
        started = time.monotonic()
        adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', url, '-n', ACTIVITY)
        time.sleep(18)
        texts = capture('search-' + str(index))
        results.append({'page': 'search', 'query': query, 'elapsedSeconds': round(time.monotonic() - started, 1), 'texts': texts})
        assert adb('shell', 'pidof', PACKAGE).strip(), 'Sonet exited during search'
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d',
        'https://music.youtube.com/channel/UC0WP5P-ufpRfjbNrmOWwLBQ', '-n', ACTIVITY)
    time.sleep(18)
    results.append({'page': 'artist', 'texts': capture('artist')})
finally:
    (OUT / 'app-results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))
    pid = adb('shell', 'pidof', PACKAGE, check=False).decode().strip()
    if pid:
        log = adb('logcat', '-d', '--pid=' + pid).decode(errors='replace')
        (OUT / 'app-logcat.txt').write_text(log)
        for line in log.splitlines():
            if any(s in line for s in ['InnerTubeHttpException', 'HTTP 403', 'HTTP 400', 'HTTP 429', 'SocketTimeoutException']):
                print(line[:800])
    print(json.dumps(results, ensure_ascii=False))
assert len(results) == 5, 'Incomplete UI probe'
print('APP_PROBE_COMPLETE: results may contain network errors; completion does not mean Music succeeded')
