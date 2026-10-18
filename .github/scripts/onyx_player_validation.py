"""Exercise the release player's UI using disposable emulator-only library rows."""
import atexit
import json
import subprocess
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
scroll_to_label = helpers['scroll_to_label']
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
    insert('song', {'id': video, 'title': 'Sonet UI check ' + str(i + 1), 'duration': 180,
                    'isVideo': 1, 'liked': 1, 'likedDate': 1770000000000, 'inLibrary': 1770000000000,
                    'thumbnailUrl': 'https://i.ytimg.com/vi/' + video + '/hqdefault.jpg'})
    insert('lyrics', {'id': video, 'lyrics': '\n'.join(f'[{i // 60:02d}:{i % 60:02d}.00]Local timing test line {i}' for i in range(180)), 'provider': 'Local validation'})
    insert('event', {'id': i + 1, 'songId': video, 'timestamp': 1770000000000 + i * 1000, 'playTime': 41000})
insert('artist', {'id': 'onyx-ui-check', 'name': 'Sonet Studio', 'lastUpdateTime': 1770000000000})
for i, video in enumerate(['dQw4w9WgXcQ', 'jNQXAC9IVRw']):
    insert('song_artist_map', {'songId': video, 'artistId': 'onyx-ui-check', 'position': 0})
insert('related_song_map', {'id': 1, 'songId': 'dQw4w9WgXcQ', 'relatedSongId': 'jNQXAC9IVRw'})
insert('related_song_map', {'id': 2, 'songId': 'jNQXAC9IVRw', 'relatedSongId': 'dQw4w9WgXcQ'})
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.close()
adb('push', str(out / 'fixture.db'), db_path)
adb('shell', 'chown', owner + ':' + owner, db_path)
adb('shell', 'chmod', '660', db_path)
adb('shell', 'restorecon', db_path)
adb('shell', 'rm', '-f', db_path + '-wal', db_path + '-shm')
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
adb('shell', 'am', 'start', '-W', '-n', activity)
time.sleep(3)
home = snapshot('07-seeded-home')
for attempt in range(8):
    if any(
        label in n.get('text', '').lower()
        for n in home.iter('node')
        for label in ('recently played', 'escuchado recientemente')
    ):
        break
    time.sleep(1)
    home = snapshot('07-recently-played-' + str(attempt))
else:
    raise AssertionError('Recently played did not show seeded listening history')
home = scroll_to_label(home, 'sonet ui check', '07-recent-title')
assert any('Sonet UI check' in n.get('text', '') for n in home.iter('node')), 'Recently played song title missing'
adb('shell', 'svc', 'wifi', 'enable')
adb('shell', 'svc', 'data', 'enable')
tap_label(home, 'library')
tap_label(scroll_to_label(snapshot('08-seeded-library'), 'liked', '08-find-liked'), 'liked')
tap_label(scroll_to_label(snapshot('09-liked-songs'), 'sonet ui check 1', '09-find-song'), 'sonet ui check 1')
time.sleep(3)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
tap_label(snapshot('10-mini-player'), 'sonet ui check 1')
player = snapshot('11-player')
assert any('Minimize player' in n.get('content-desc', '') for n in player.iter('node')), 'Expanded player header missing'
assert any('Next track' in n.get('content-desc', '') for n in player.iter('node')), 'Transport missing'
tap_label(player, 'repeat')
tap_label(snapshot('12-repeat'), 'sleep timer')
tap_label(snapshot('13-sleep-timer'), 'cancel')
tap_label(snapshot('13-timer-dismissed'), 'sleep timer')
tap_label(snapshot('13-timer-start'), 'ok')
active_timer = snapshot('13-timer-active')
assert any(re.match(r'^(29:[0-5]\d|30:00)$', n.get('text', '')) for n in active_timer.iter('node')), 'Active timer countdown missing'
tap_label(active_timer, 'sleep timer')
cleared_timer = snapshot('13-timer-cleared')
assert not any(re.match(r'^(29:[0-5]\d|30:00)$', n.get('text', '')) for n in cleared_timer.iter('node')), 'Timer did not clear'
# Exercise the 480dp landscape width that previously squeezed the transport.
adb('shell', 'wm', 'size', '1080x1260')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
time.sleep(3)
landscape = snapshot('14-player-landscape')
if not any('Previous track' in n.get('content-desc', '') for n in landscape.iter('node')):
    tap_label(landscape, 'sonet ui check 1')
    landscape = snapshot('14-player-landscape-expanded')
width, height = map(int, re.findall(r'\d+', adb('shell', 'wm', 'size').decode().splitlines()[-1]))
# Mini player exposes Next, so require Previous as well to prove expansion.
for control in ['Previous track', 'Next track']:
    nodes = [n for n in landscape.iter('node') if control in n.get('content-desc', '')]
    assert nodes, 'Expanded landscape transport missing: ' + control
    bounds = [int(v) for v in re.findall(r'\d+', nodes[0].get('bounds', ''))]
    assert len(bounds) == 4 and 0 <= bounds[0] < bounds[2] <= height and 0 <= bounds[1] < bounds[3] <= width, 'Landscape control outside viewport: ' + control
def visible_bounds(root, description):
    result = []
    for n in root.iter('node'):
        if description in n.get('content-desc', ''):
            bounds = [int(v) for v in re.findall(r'\d+', n.get('bounds', ''))]
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] - bounds[1] >= 40:
                result.append(bounds)
    return result

for attempt in range(5):
    if visible_bounds(landscape, 'Sleep timer'):
        break
    assert attempt < 4, 'Landscape timer must remain accessible by scrolling its controls'
    adb('shell', 'input', 'swipe', str(int(height * .8)), str(int(width * .5)),
        str(int(height * .8)), str(int(width * .25)), '350')
    time.sleep(.5)
    landscape = snapshot('14-landscape-utilities-' + str(attempt))
for timer in visible_bounds(landscape, 'Sleep timer'):
    for menu in visible_bounds(landscape, 'More options'):
        assert max(timer[0], menu[0]) >= min(timer[2], menu[2]) or max(timer[1], menu[1]) >= min(timer[3], menu[3]), 'Landscape timer overlaps player menu'
snapshot('14-landscape-utilities-verified')
adb('shell', 'wm', 'size', 'reset')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
time.sleep(2)
portrait = snapshot('15-player-portrait')
if not any('Minimize player' in n.get('content-desc', '') for n in portrait.iter('node')):
    tap_label(portrait, 'sonet ui check 1')
    portrait = snapshot('15-player-portrait-expanded')
tap_label(portrait, 'lyrics')
lyrics_screen = snapshot('16-lyrics')

def control_point(root, description):
    nodes = [n for n in root.iter('node') if n.get('content-desc', '').lower() == description]
    assert nodes, 'Missing transition control: ' + description
    b = [int(v) for v in re.findall(r'\d+', nodes[0].get('bounds', ''))]
    return str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)

def check_metadata_row(root):
    titles = [n for n in root.iter('node') if n.get('text') == 'Sonet UI check 1']
    # Home behind the sheet can expose the same title; inspect the player's row.
    title = min(titles, key=lambda n: abs(
        sum(int(v) for v in re.findall(r'\d+', n.get('bounds', ''))[1::2]) / 2
        - int(control_point(root, 'fullscreen lyrics')[1])))
    title_bounds = [int(v) for v in re.findall(r'\d+', title.get('bounds', ''))]
    button_y = int(control_point(root, 'fullscreen lyrics')[1])
    assert title_bounds[1] - 40 <= button_y <= title_bounds[3] + 80, 'Title detached from metadata row'

def check_visible_lyrics(root):
    visible = []
    for node in root.iter('node'):
        if 'Local timing test line' not in node.get('text', ''):
            continue
        b = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
        if len(b) == 4 and b[2] - b[0] > 100 and b[3] > b[1]:
            visible.append(b)
    assert visible, 'Loaded lyrics have no visible width/height'

check_visible_lyrics(lyrics_screen)
fullscreen_normal = control_point(lyrics_screen, 'fullscreen lyrics')
toggle_lyrics = control_point(lyrics_screen, 'lyrics')
check_metadata_row(lyrics_screen)
tap_label(lyrics_screen, 'fullscreen lyrics')
fullscreen_screen = snapshot('16-fullscreen')
check_metadata_row(fullscreen_screen)
check_visible_lyrics(fullscreen_screen)
fullscreen_expanded = control_point(fullscreen_screen, 'fullscreen lyrics')
tap_label(fullscreen_screen, 'fullscreen lyrics')

# Measure repeated mode changes with loaded local lyrics; no network/stream timing.
# Keep raw frame data and a recording so jank isn't hidden by a successful build.
recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '12', '/sdcard/sonet-transitions.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
for _ in range(5):
    for point in [toggle_lyrics, toggle_lyrics, fullscreen_normal, fullscreen_expanded]:
        adb('shell', 'input', 'tap', *point)
        time.sleep(.45)
raw = adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats').decode(errors='replace')
(out / 'lyrics-transition-gfxinfo.txt').write_text(raw)
metrics = {name: re.search(pattern, raw).group(1) if re.search(pattern, raw) else None for name, pattern in {
    'frames': r'Total frames rendered:\s*(\d+)',
    'janky_frames': r'Janky frames:\s*([^\n]+)',
    'p90_ms': r'90th percentile:\s*(\d+)ms',
}.items()}
print('TRANSITION PROFILE (software emulator, not phone FPS):', json.dumps(metrics))
recording.wait(timeout=15)
adb('pull', '/sdcard/sonet-transitions.mp4', str(out / 'lyrics-transitions.mp4'), check=False)
settled = snapshot('16-after-transition-stress')
check_metadata_row(settled)
check_visible_lyrics(settled)
tap_label(settled, 'lyrics tools')
snapshot('17-lyrics-tools')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
tap_label(snapshot('18-return-player'), 'lyrics')
video_header = snapshot('18-video-toggle')
tap_label(video_header, 'video')
video_result = None
for attempt in range(26):
    video = snapshot('19-video-' + str(attempt))
    descriptions = [n.get('content-desc', '') for n in video.iter('node')]
    native_player_ready = any(n.get('text') == 'YouTube Video Player' for n in video.iter('node'))
    if native_player_ready or any('Video playing' in d or 'Video ready' in d for d in descriptions):
        video_result = 'Embedded video initialized'
        break
    if not any(d == 'Video' for d in descriptions) and any('Minimize player' in d for d in descriptions):
        video_result = 'YouTube unavailable: cover fallback preserved audio player'
        break
    time.sleep(1)
assert video_result is not None, 'Video did not initialize or return to the cover after timeout'
(out / 'video-result.txt').write_text(video_result)
print('VIDEO:', video_result)

log = adb('logcat', '-d').decode(errors='replace')
(out / 'player-logcat.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log or 'Process: ' + package not in log, 'App crash; inspect player-logcat'
assert adb('shell', 'pidof', package).strip(), 'App stopped'
(out / 'gfxinfo.txt').write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package))
print('PASS: expanded/mini player, repeat, sleep timer, rotation and lyrics tools exercised')
