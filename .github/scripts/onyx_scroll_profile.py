"""Measure a fixed local album grid; no network or music streaming in this probe."""
import json
import re
import sqlite3
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

label = sys.argv[1] if len(sys.argv) > 1 else 'sample'
out = Path('smoke/performance')
out.mkdir(parents=True, exist_ok=True)
package = 'com.onyx.music'
activity = package + '/com.metrolist.music.MainActivity'

def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout

def snapshot(name):
    (out / (label + '-' + name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'uiautomator', 'dump', '/sdcard/profile.xml')
    xml = adb('exec-out', 'cat', '/sdcard/profile.xml')
    (out / (label + '-' + name + '.xml')).write_bytes(xml)
    return ET.fromstring(xml)

def tap(root, label_text):
    matches = []
    for n in root.iter('node'):
        if label_text in (n.get('text', '') + ' ' + n.get('content-desc', '')).lower():
            bounds = [int(v) for v in re.findall(r'\d+', n.get('bounds', ''))]
            if len(bounds) == 4:
                matches.append(bounds)
    assert matches, 'Missing profile control: ' + label_text
    left, top, right, bottom = max(matches, key=lambda b: b[3])
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(2)

adb('root')
adb('wait-for-device')
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
adb('shell', 'am', 'force-stop', package)
db_path = '/data/user/0/' + package + '/databases/song.db'
owner = adb('shell', 'stat', '-c', '%u', db_path).decode().strip()
local = out / (label + '-fixture.db')
adb('pull', db_path, str(local))
adb('pull', db_path + '-wal', str(local) + '-wal', check=False)
db = sqlite3.connect(local)
columns = db.execute('PRAGMA table_info(album)').fetchall()
assert columns
for i in range(48):
    values = {'id': 'onyx-perf-album-' + str(i),
              'title': 'Onyx performance album with a deliberately long title to reproduce simultaneous scrolling text ' + str(i),
              'songCount': 1, 'duration': 180, 'bookmarkedAt': 1770000000000,
              'lastUpdateTime': 1770000000000,
              'thumbnailUrl': 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg'}
    row = {}
    for _, name, kind, required, default, _ in columns:
        if name in values:
            row[name] = values[name]
        elif required and default is None:
            row[name] = '' if 'TEXT' in kind else 0
    keys = ','.join('"' + k + '"' for k in row)
    db.execute('INSERT OR REPLACE INTO album (' + keys + ') VALUES (' + ','.join('?' for _ in row) + ')', list(row.values()))
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.close()
adb('push', str(local), db_path)
adb('shell', 'chown', owner + ':' + owner, db_path)
adb('shell', 'chmod', '660', db_path)
adb('shell', 'restorecon', db_path)
adb('shell', 'rm', '-f', db_path + '-wal', db_path + '-shm')
adb('shell', 'am', 'start', '-W', '-n', activity)
time.sleep(4)
tap(snapshot('home'), 'library')
tap(snapshot('library'), 'albums')
albums = snapshot('albums')
assert any('Onyx performance album' in n.get('text', '') for n in albums.iter('node')), 'Profile grid missing fixtures'
# The same artwork was cached by functional validation; remove network variability.
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
time.sleep(2)

def capture(name, action):
    adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    started = time.monotonic()
    action()
    raw = adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats').decode(errors='replace')
    (out / (label + '-' + name + '-gfxinfo.txt')).write_text(raw)
    match = re.search(r'Total frames rendered:\s*(\d+)', raw)
    assert match, 'Missing gfx frame count'
    return {'frames': int(match.group(1)), 'elapsed_seconds': round(time.monotonic() - started, 2),
            'p50_ms': re.search(r'50th percentile:\s*(\d+)ms', raw).group(1),
            'p90_ms': re.search(r'90th percentile:\s*(\d+)ms', raw).group(1)}

idle = capture('idle', lambda: time.sleep(8))
width, height = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size').decode()).groups())
def scroll():
    for i in range(12):
        top, bottom = int(height * .35), int(height * .75)
        start, end = (bottom, top) if i % 2 == 0 else (top, bottom)
        adb('shell', 'input', 'swipe', str(width // 2), str(start), str(width // 2), str(end), '350')
        time.sleep(.2)
scroll_result = capture('scroll', scroll)
snapshot('after-scroll')
metrics = {'label': label, 'idle': idle, 'scroll': scroll_result,
           'note': 'Software-rendered emulator; relative probe, not phone FPS.'}
(out / (label + '-metrics.json')).write_text(json.dumps(metrics, indent=2))
adb('shell', 'svc', 'wifi', 'enable')
adb('shell', 'svc', 'data', 'enable')
print('PROFILE:', json.dumps(metrics))
