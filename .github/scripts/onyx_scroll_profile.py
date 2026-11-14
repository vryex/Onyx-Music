"""Measure the local album grid and horizontal Home shelf without streaming."""
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
pin_columns = db.execute('PRAGMA table_info(speed_dial_item)').fetchall()
for i in range(24):
    values = {'id': 'onyx-profile-pin-' + str(i), 'type': 'ALBUM',
              'title': 'Fixed profile album ' + str(i), 'createDate': 1770000000000,
              'thumbnailUrl': 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg'}
    row = {}
    for _, name, kind, required, default, _ in pin_columns:
        if name in values:
            row[name] = values[name]
        elif required and default is None:
            row[name] = '' if 'TEXT' in kind else 0
    keys = ','.join('"' + k + '"' for k in row)
    db.execute('INSERT OR REPLACE INTO speed_dial_item (' + keys + ') VALUES (' + ','.join('?' for _ in row) + ')', list(row.values()))
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.close()
adb('push', str(local), db_path)
adb('shell', 'chown', owner + ':' + owner, db_path)
adb('shell', 'chmod', '660', db_path)
adb('shell', 'restorecon', db_path)
adb('shell', 'rm', '-f', db_path + '-wal', db_path + '-shm')
adb('shell', 'am', 'start', '-W', '-n', activity, '-f', '0x10008000')
time.sleep(4)
tap(snapshot('home'), 'library')
library = snapshot('library')
def selected_label(root, text):
    return any((n.get('selected') == 'true' or n.get('checked') == 'true') and any(
        text in (c.get('text', '').strip().lower(), c.get('content-desc', '').strip().lower()) for c in n.iter('node')
    ) for n in root.iter('node'))
if not selected_label(library, 'albums'):
    tap(library, 'albums')
albums = snapshot('albums')
assert selected_label(albums, 'albums') and selected_label(albums, 'liked'), 'Profile must stay in the liked-albums-only screen'
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
    ui_times = []
    for block in raw.split('---PROFILEDATA---'):
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        if not lines or not lines[0].startswith('Flags,'):
            continue
        header = lines[0].split(',')
        for line in lines[1:]:
            fields = line.split(',')
            try:
                elapsed = (int(fields[header.index('SyncQueued')]) - int(fields[header.index('HandleInputStart')])) / 1_000_000
                if int(fields[0]) == 0 and 0 < elapsed < 1000:
                    ui_times.append(elapsed)
            except (ValueError, IndexError):
                continue
    ui_times.sort()
    return {'frames': int(match.group(1)), 'elapsed_seconds': round(time.monotonic() - started, 2),
            'ui_work_p90_ms': round(ui_times[int((len(ui_times) - 1) * .9)], 2) if ui_times else None,
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
grid = snapshot('after-scroll')
def nav_point(text):
    candidates = []
    for n in grid.iter('node'):
        if text in [n.get('text', '').lower(), n.get('content-desc', '').lower()]:
            b = [int(v) for v in re.findall(r'\d+', n.get('bounds', ''))]
            if len(b) == 4:
                candidates.append(b)
    assert candidates, 'Missing navigation target: ' + text
    b = max(candidates, key=lambda b: b[3])
    return str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)
home_point, library_point = nav_point('home'), nav_point('library')
def navigate():
    for _ in range(4):
        for point in [home_point, library_point]:
            adb('shell', 'input', 'tap', *point)
            time.sleep(.75)
navigation = capture('navigation', navigate)
adb('shell', 'input', 'tap', *home_point)
time.sleep(2)
home = snapshot('home-navigation')
assert selected_label(home, 'home'), 'Home navigation did not complete'
def fixture_titles(root):
    return sorted({n.get('text', '') for n in root.iter('node') if 'Fixed profile album' in n.get('text', '')})

if label == 'candidate':
    # Quick access is now a horizontal shelf. Find its real seeded cards before
    # measuring, then send gestures through that shelf rather than the page column.
    for attempt in range(9):
        if fixture_titles(home):
            break
        assert attempt < 8, 'Home profile must expose seeded pinned albums'
        adb('shell', 'input', 'swipe', str(width // 2), str(int(height * .74)),
            str(width // 2), str(int(height * .40)), '350')
        time.sleep(.5)
        home = snapshot('find-pinned-home-' + str(attempt))
    fixture_nodes = [n for n in home.iter('node') if 'Fixed profile album' in n.get('text', '')]
    shelf_bounds = [int(v) for v in re.findall(r'\d+', fixture_nodes[0].get('bounds', ''))]
    assert len(shelf_bounds) == 4 and shelf_bounds[3] > shelf_bounds[1], 'Pinned shelf has no touch bounds'
    shelf_y = (shelf_bounds[1] + shelf_bounds[3]) // 2
    if shelf_y > height * .65:
        adb('shell', 'input', 'swipe', str(width // 2), str(int(height * .72)),
            str(width // 2), str(int(height * .48)), '350')
        time.sleep(.5)
        home = snapshot('pinned-home-centered')
        fixture_nodes = [n for n in home.iter('node') if 'Fixed profile album' in n.get('text', '')]
        assert fixture_nodes, 'Pinned shelf disappeared while centering'
        shelf_bounds = [int(v) for v in re.findall(r'\d+', fixture_nodes[0].get('bounds', ''))]
        shelf_y = (shelf_bounds[1] + shelf_bounds[3]) // 2
    snapshot('pinned-home')
    home_idle = capture('home-idle', lambda: time.sleep(8))
    before_titles = fixture_titles(home)
    adb('shell', 'input', 'swipe', str(int(width * .86)), str(shelf_y),
        str(int(width * .16)), str(shelf_y), '350')
    time.sleep(.5)
    shifted = snapshot('pinned-shelf-shifted')
    assert fixture_titles(shifted) and fixture_titles(shifted) != before_titles, 'Horizontal gesture did not move seeded pinned shelf'

    def scroll_home_shelf():
        for i in range(12):
            start, end = (int(width * .16), int(width * .86)) if i % 2 == 0 else (int(width * .86), int(width * .16))
            adb('shell', 'input', 'swipe', str(start), str(shelf_y), str(end), str(shelf_y), '350')
            time.sleep(.2)

    home_scroll = capture('home-scroll', scroll_home_shelf)
else:
    adb('shell', 'input', 'swipe', str(width // 2), str(int(height * .75)), str(width // 2), str(int(height * .40)), '350')
    time.sleep(.75)
    home = snapshot('pinned-home')
    assert fixture_titles(home), 'Baseline Home must expose seeded pinned albums'
    home_idle = capture('home-idle', lambda: time.sleep(8))
    home_scroll = capture('home-scroll', scroll)
snapshot('home-after-scroll')
metrics = {'label': label, 'idle': idle, 'scroll': scroll_result, 'navigation': navigation, 'home_idle': home_idle, 'home_scroll': home_scroll,
           'note': 'Software-rendered emulator, not phone FPS. Album gestures match; Home baseline vertical and candidate horizontal shelf gestures differ.'}
(out / (label + '-metrics.json')).write_text(json.dumps(metrics, indent=2))
adb('shell', 'svc', 'wifi', 'enable')
adb('shell', 'svc', 'data', 'enable')
print('PROFILE:', json.dumps(metrics))
