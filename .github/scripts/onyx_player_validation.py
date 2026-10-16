"""Exercise the release player's UI using disposable emulator-only library rows."""
import atexit
import re
import runpy
import sqlite3
import subprocess
import time
from pathlib import Path

helpers = runpy.run_path('.github/scripts/onyx_smoke.py')
adb = helpers['adb']
snapshot = helpers['snapshot']
base_tap_label = helpers['tap_label']
package = helpers['PACKAGE']
activity = helpers['ACTIVITY']
out = helpers['OUT']

def tap_label(root, label):
    label = label.lower()
    for attempt in range(8):
        if any(label in (n.get('text', '') + ' ' + n.get('content-desc', '')).lower() for n in root.iter('node')):
            return base_tap_label(root, label)
        time.sleep(1)
        root = snapshot('wait-' + re.sub(r'[^a-z0-9]+', '-', label) + '-' + str(attempt))
    raise AssertionError('Missing visible control after accessibility settled: ' + label)

def save_runtime_log():
    (out / 'player-logcat.txt').write_bytes(adb('logcat', '-d', check=False))
atexit.register(save_runtime_log)

adb('root')
adb('wait-for-device')
adb('shell', 'am', 'force-stop', package)
db_path = '/data/user/0/' + package + '/databases/song.db'
owner = adb('shell', 'stat', '-c', '%u', db_path).decode().strip()
adb('pull', db_path, str(out / 'fixture.db'))
# SQLite may keep the freshly created schema in its write-ahead log.
adb('pull', db_path + '-wal', str(out / 'fixture.db-wal'), check=False)
db = sqlite3.connect(out / 'fixture.db')

def insert(table, values):
    columns = db.execute('PRAGMA table_info(' + table + ')').fetchall()
    assert columns, 'Missing fixture table: ' + table
    row = {}
    for _, name, kind, required, default, _ in columns:
        if name in values:
            row[name] = values[name]
        elif required and default is None:
            row[name] = '' if 'TEXT' in kind else 0
    keys = ','.join('"' + name + '"' for name in row)
    db.execute('INSERT OR REPLACE INTO ' + table + ' (' + keys + ') VALUES (' + ','.join('?' for _ in row) + ')', list(row.values()))

for i, video in enumerate(['dQw4w9WgXcQ', 'jNQXAC9IVRw']):
    insert('song', {'id': video, 'title': 'Onyx UI check ' + str(i + 1), 'duration': 180,
                    'liked': 1, 'likedDate': 1770000000000, 'inLibrary': 1770000000000,
                    'thumbnailUrl': 'https://i.ytimg.com/vi/' + video + '/hqdefault.jpg'})
insert('artist', {'id': 'onyx-ui-check', 'name': 'Onyx Studio', 'lastUpdateTime': 1770000000000})
for i, video in enumerate(['dQw4w9WgXcQ', 'jNQXAC9IVRw']):
    insert('song_artist_map', {'songId': video, 'artistId': 'onyx-ui-check', 'position': 0})
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.close()
adb('push', str(out / 'fixture.db'), db_path)
adb('shell', 'chown', owner + ':' + owner, db_path)
adb('shell', 'chmod', '660', db_path)
adb('shell', 'restorecon', db_path)
adb('shell', 'rm', '-f', db_path + '-wal', db_path + '-shm')
adb('shell', 'am', 'start', '-W', '-n', activity)
time.sleep(3)
tap_label(snapshot('07-seeded-home'), 'library')
tap_label(snapshot('08-seeded-library'), 'liked')
tap_label(snapshot('09-liked-songs'), 'onyx ui check 1')
time.sleep(3)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
tap_label(snapshot('10-mini-player'), 'onyx ui check 1')
player = snapshot('11-player')
assert any('Minimize player' in n.get('content-desc', '') for n in player.iter('node')), 'Expanded player header missing'
assert any('Next track' in n.get('content-desc', '') for n in player.iter('node')), 'Transport missing'
tap_label(player, 'repeat')
tap_label(snapshot('12-repeat'), 'sleep timer')
tap_label(snapshot('13-sleep-timer'), 'cancel')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
time.sleep(3)
landscape = snapshot('14-player-landscape')
if not any('Next track' in n.get('content-desc', '') for n in landscape.iter('node')):
    tap_label(landscape, 'onyx ui check 1')
    landscape = snapshot('14-player-landscape-expanded')
assert any('Next track' in n.get('content-desc', '') for n in landscape.iter('node')), 'Landscape transport missing'
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
time.sleep(2)
portrait = snapshot('15-player-portrait')
if not any('Minimize player' in n.get('content-desc', '') for n in portrait.iter('node')):
    tap_label(portrait, 'onyx ui check 1')
    portrait = snapshot('15-player-portrait-expanded')
tap_label(portrait, 'lyrics')
tap_label(snapshot('16-lyrics'), 'lyrics tools')
snapshot('17-lyrics-tools')
log = adb('logcat', '-d').decode(errors='replace')
(out / 'player-logcat.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log or 'Process: ' + package not in log, 'App crash; inspect player-logcat'
assert adb('shell', 'pidof', package).strip(), 'App stopped'
(out / 'gfxinfo.txt').write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package))
print('PASS: expanded/mini player, repeat, sleep timer, rotation and lyrics tools exercised')
