"""Exercise the release player's UI using disposable emulator-only library rows."""
import sys
import atexit
import json
import subprocess
import re
import runpy
import sqlite3
import subprocess
import time
from PIL import Image, ImageStat
from pathlib import Path

page_actions_only = '--page-actions-only' in sys.argv
video_only = '--video-only' in sys.argv or page_actions_only
helpers = runpy.run_path('.github/scripts/onyx_smoke.py', init_globals={'VIDEO_PROBE_ONLY': video_only})
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

def csv_picker_snapshot(prefix):
    # DocumentsUI can take several seconds to replace the app on a cold emulator.
    for attempt in range(20):
        root = snapshot(prefix + '-' + str(attempt))
        if any('documentsui' in n.get('package', '') for n in root.iter('node')):
            return root
        time.sleep(.5)
    raise AssertionError('CSV document picker did not open')


def validate_spotify_background(root):
    # Disposable local CSV only: no real Spotify account is connected by CI.
    fixture = out / 'sonet-background-test.csv'
    fixture.write_text('Track Name,Artist Name(s),Track Duration (ms)\n' + ''.join(
        f'Sonet background queue {i},Sonet validation artist,213000\n' for i in range(50)), encoding='utf-8')
    adb('push', str(fixture), '/sdcard/Download/sonet-background-test.csv')
    adb('shell', 'pm', 'grant', package, 'android.permission.POST_NOTIFICATIONS')
    root = scroll_to_label(root, 'choose csv file', '25-find-picker')
    tap_label(root, 'choose csv file')
    time.sleep(1)
    picker = csv_picker_snapshot('25-background-picker')
    if not any('sonet-background-test.csv' in n.get('text', '') for n in picker.iter('node')):
        tap_label(picker, 'show roots')
        time.sleep(1)
        tap_label(snapshot('25-background-locations'), 'downloads')
        picker = snapshot('25-background-downloads')
    tap_label(picker, 'sonet-background-test.csv')
    preview = scroll_to_label(snapshot('25-background-preview'), 'import songs', '25-background-import')
    tap_label(preview, 'import songs')
    service_name = 'SpotifyImportForegroundService'
    def service_state():
        return adb('shell', 'dumpsys', 'activity', 'services', package).decode(errors='replace')
    for attempt in range(25):
        services = service_state()
        blocks = [block for block in services.split('ServiceRecord') if service_name in block]
        if any('isForeground=true' in block for block in blocks):
            break
        time.sleep(.2)
    else:
        raise AssertionError('Spotify import never became a foreground service')
    (out / '25-import-service.txt').write_text(services)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(.5)
    background = service_state()
    assert service_name in background, 'Import service stopped when leaving the app'
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    time.sleep(1)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    notifications = adb('shell', 'dumpsys', 'notification', '--noredact').decode(errors='replace')
    (out / '25-import-notification.txt').write_text(notifications)
    assert 'spotify_import' in notifications, 'Spotify import notification channel missing'
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    tray = snapshot('25-background-notification')
    if any('cancel' == n.get('text', '').lower() for n in tray.iter('node')):
        tap_label(tray, 'cancel')
    else:
        adb('shell', 'am', 'startservice', '-n', package + '/com.metrolist.music.spotify.SpotifyImportForegroundService',
            '-a', 'com.metrolist.music.spotify.CANCEL_IMPORT')
    adb('shell', 'cmd', 'statusbar', 'collapse')
    for attempt in range(30):
        services = service_state()
        if not any('isForeground=true' in block for block in services.split('ServiceRecord') if service_name in block):
            break
        time.sleep(.2)
    else:
        raise AssertionError('Cancelled import retained its foreground service')
    # A new process must restore pending work and notification navigation directly opens its page.
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', activity, '--ez', 'open_spotify_import', 'true')
    time.sleep(3)
    restored = snapshot('25-background-restored')
    assert any(n.get('resource-id', '') == 'sonet_spotify_import' for n in restored.iter('node')), 'Notification target did not open import screen'
    restored = scroll_to_label(restored, 'retry pending songs', '25-restored-retry')
    assert any(n.get('resource-id', '') in ('sonet_spotify_resume', 'sonet_spotify_import_button') and n.get('enabled') == 'true' for n in restored.iter('node')), 'Pending import was not restored after process restart'
    print('PASS: Spotify foreground import, notification, app backgrounding, cancellation, and pending checkpoint restore')


def validate_page_actions():
    # Page actions must disappear with the player and return on the underlying page.
    tap_label(snapshot('22-fit-player'), 'minimize player')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap_label(snapshot('22-library'), 'home')
    home_actions = snapshot('22-home-actions')
    assert any(n.get('resource-id', '') == 'sonet_page_actions' for n in home_actions.iter('node')), 'Home floating actions missing with collapsed player'
    mini = max((n for n in home_actions.iter('node') if 'sonet ui check 1' in (n.get('text', '') + n.get('content-desc', '')).lower()),
               key=lambda n: int(re.findall(r'\d+', n.get('bounds', ''))[3]))
    mx1, my1, mx2, my2 = map(int, re.findall(r'\d+', mini.get('bounds', '')))
    size = list(map(int, re.findall(r'\d+', adb('shell', 'wm', 'size').decode())))[-2:]
    drag = subprocess.Popen(['adb', 'shell', 'input', 'swipe', str((mx1 + mx2) // 2), str((my1 + my2) // 2),
                             str(size[0] // 2), str(size[1] // 4), '4000'])
    time.sleep(1.2)
    snapshot('22-dragging-player')
    drag.wait(timeout=10)
    time.sleep(2)
    expanded_actions = snapshot('22-expanded-actions')
    assert not any(n.get('resource-id', '') == 'sonet_page_actions' for n in expanded_actions.iter('node')), 'Page actions remain accessible over expanded player'
    tap_label(expanded_actions, 'minimize player')
    assert any(n.get('resource-id', '') == 'sonet_page_actions' for n in snapshot('22-returned-actions').iter('node')), 'Page actions did not return after collapse'

    # Only a disposable local artist is toggled; no real channel subscription is changed.
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d',
        'https://music.youtube.com/channel/onyx-ui-check', '-n', activity)
    time.sleep(5)
    subscribed = snapshot('23-local-artist-subscribed')
    button = next((n for n in subscribed.iter('node') if n.get('resource-id', '') == 'sonet_artist_subscription'), None)
    assert button is not None, 'Local artist subscription button missing'
    assert any('subscribed' in n.get('text', '').lower() for n in button.iter('node')), 'Fixture artist not subscribed'
    bounds = list(map(int, re.findall(r'\d+', button.get('bounds', ''))))
    x1, y1, x2, y2 = bounds
    # Sample the empty left side inside the pill, away from text and border.
    patch = (x1 + 18, (y1 + y2) // 2 - 3, x1 + 24, (y1 + y2) // 2 + 3)
    subscribed_color = ImageStat.Stat(Image.open(out / '23-local-artist-subscribed.png').convert('RGB').crop(patch)).mean
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)
    snapshot('23-local-artist-unsubscribed')
    unsubscribed_color = ImageStat.Stat(Image.open(out / '23-local-artist-unsubscribed.png').convert('RGB').crop(patch)).mean
    assert max(abs(a - b) for a, b in zip(subscribed_color, unsubscribed_color)) > 10, 'Subscribed artist did not gain its banner fill'
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap_label(snapshot('24-home-return'), 'library')
    library = snapshot('24-spotify-entry')
    entry = next((n for n in library.iter('node') if n.get('resource-id', '') == 'sonet_spotify_entry'), None)
    assert entry is not None, 'Library Spotify import entry missing'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', entry.get('bounds', '')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)
    spotify = snapshot('24-spotify-setup')
    assert any(n.get('resource-id', '') == 'sonet_spotify_import' for n in spotify.iter('node')), 'Spotify setup screen missing'
    connect = next((n for n in spotify.iter('node') if n.get('resource-id', '') == 'sonet_spotify_connect'), None)
    assert connect is not None and connect.get('enabled') == 'true', 'Direct Spotify connection missing or disabled'
    assert any('Connect Spotify' in n.get('text', '') for n in connect.iter('node')), 'Connect Spotify must be the primary action'

    choose = next((n for n in spotify.iter('node') if n.get('resource-id', '') == 'sonet_spotify_choose_file'), None)
    assert choose is not None and choose.get('enabled') == 'true', 'CSV picker missing'
    assert not any('client id' in n.get('text', '').lower() for n in spotify.iter('node')), 'Technical Spotify setup remains'
    fixture = out / 'sonet-spotify-test.csv'
    fixture.write_text('Track Name,Artist Name(s),Track Duration (ms)\nNever Gonna Give You Up,Rick Astley,213000\n', encoding='utf-8')
    adb('push', str(fixture), '/sdcard/Download/sonet-spotify-test.csv')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', choose.get('bounds', '')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(2)
    picker = csv_picker_snapshot('24-csv-picker')
    if not any('sonet-spotify-test.csv' in n.get('text', '') for n in picker.iter('node')):
        # DocumentsUI navigation drawer exposes Downloads across emulator versions.
        tap_label(picker, 'show roots')
        time.sleep(1)
        picker = snapshot('24-csv-locations')
        tap_label(picker, 'downloads')
        picker = snapshot('24-csv-downloads')
    tap_label(picker, 'sonet-spotify-test.csv')
    time.sleep(2)
    preview = scroll_to_label(snapshot('24-csv-preview'), 'import songs', '24-find-import-button')
    assert any('Never Gonna Give You Up' in n.get('text', '') for n in preview.iter('node')), 'CSV preview missing'
    assert any(n.get('resource-id', '') == 'sonet_spotify_import_button' and n.get('enabled') == 'true' for n in preview.iter('node')), 'Explicit import button missing'
    size = list(map(int, re.findall(r'\d+', adb('shell', 'wm', 'size').decode())))[-2:]
    for _ in range(3):
        adb('shell', 'input', 'swipe', str(size[0]//2), str(size[1]//3), str(size[0]//2), str(size[1]*4//5), '250')
    # Changing the OS per-app locale must change the same resource-backed screen.
    adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', 'es')
    time.sleep(3)
    spanish = snapshot('24-spotify-spanish')
    assert any('Conectar Spotify' in n.get('text', '') for n in spanish.iter('node')), 'Spanish direct connection translation missing'
    adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', 'en')
    time.sleep(3)
    english = snapshot('24-spotify-english')
    assert any('Connect Spotify' in n.get('text', '') for n in english.iter('node')), 'English direct connection translation did not return'
    print('PASS: page actions, artist palette, direct Spotify connection UI, CSV picker/preview, and Spanish/English app locales')
    validate_spotify_background(english)


def save_runtime_log():
    raw = adb('logcat', '-d', check=False)
    (out / 'player-logcat.txt').write_bytes(raw)
    for line in raw.decode(errors='replace').splitlines():
        if 'SonetVideo' in line:
            print(line)
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

for i, video in enumerate(['dQw4w9WgXcQ', 'aqz-KE-bpKQ']):
    insert('song', {'id': video, 'title': 'Sonet UI check ' + str(i + 1), 'duration': 180,
                    'isVideo': 1 if i == 0 else 0, 'liked': 1, 'likedDate': 1770000000000, 'inLibrary': 1770000000000,
                    'thumbnailUrl': 'https://i.ytimg.com/vi/' + video + '/hqdefault.jpg'})
    insert('lyrics', {'id': video, 'lyrics': '\n'.join(f'[{i // 60:02d}:{i % 60:02d}.00]Local timing test line {i}' for i in range(180)), 'provider': 'Local validation'})
    insert('event', {'id': i + 1, 'songId': video, 'timestamp': 1770000000000 + i * 1000, 'playTime': 41000})
insert('artist', {'id': 'onyx-ui-check', 'name': 'Sonet Studio', 'lastUpdateTime': 1770000000000,
                  'isLocal': 1, 'bookmarkedAt': 1770000000000,
                  'thumbnailUrl': 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg'})
for i, video in enumerate(['dQw4w9WgXcQ', 'aqz-KE-bpKQ']):
    insert('song_artist_map', {'songId': video, 'artistId': 'onyx-ui-check', 'position': 0})
insert('related_song_map', {'id': 1, 'songId': 'dQw4w9WgXcQ', 'relatedSongId': 'aqz-KE-bpKQ'})
insert('related_song_map', {'id': 2, 'songId': 'aqz-KE-bpKQ', 'relatedSongId': 'dQw4w9WgXcQ'})
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
if not video_only:
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
liked_songs = snapshot('09-liked-songs')
if page_actions_only:
    badges = [n for n in liked_songs.iter('node') if n.get('resource-id', '') == 'sonet_favorite_badge']
    assert badges, 'Favorite badges missing from liked songs'
    pixels = Image.open(out / '09-liked-songs.png').convert('RGB')
    for badge in badges:
        bounds = list(map(int, re.findall(r'\d+', badge.get('bounds', ''))))
        bright = [p for p in pixels.crop(tuple(bounds)).getdata() if min(p) > 120]
        assert len(bright) > 10 and sum(max(p) - min(p) < 12 for p in bright) / len(bright) > .95, 'Favorite heart is colored instead of white'
tap_label(scroll_to_label(liked_songs, 'sonet ui check 1', '09-find-song'), 'sonet ui check 1')
time.sleep(3)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
tap_label(snapshot('10-mini-player'), 'sonet ui check 1')
player = snapshot('11-player')
for attempt in range(8):
    if any('Minimize player' in n.get('content-desc', '') for n in player.iter('node')):
        break
    time.sleep(1)
    player = snapshot('11-player-settled-' + str(attempt))
assert any('Minimize player' in n.get('content-desc', '') for n in player.iter('node')), 'Expanded player header missing'
assert any('Next track' in n.get('content-desc', '') for n in player.iter('node')), 'Transport missing'
transport = next(n for n in player.iter('node') if n.get('resource-id', '') == 'sonet_player_transport')
x1, y1, x2, y2 = map(int, re.findall(r'\d+', transport.get('bounds', '')))
pixels = Image.open(out / '11-player.png').convert('RGB')
# Empty regions inside and outside the old capsule should show the same sheet.
y = (y1+y2)//2
inside = ImageStat.Stat(pixels.crop((x1+12,y-2,x1+18,y+2))).mean
outside = ImageStat.Stat(pixels.crop((4,y-2,10,y+2))).mean
assert max(abs(a-b) for a,b in zip(inside,outside)) < 4, 'Transport capsule background remains'

if page_actions_only:
    next_control = next(n for n in player.iter('node') if n.get('content-desc', '') == 'Next track')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', next_control.get('bounds', '')))
    player_height = Image.open(out / '11-player.png').height
    assert (y1 + y2) / 2 < player_height * .80, 'Portrait transport controls are still too low'
    validate_page_actions()
    assert adb('shell', 'pidof', package).strip(), 'App stopped'
    sys.exit(0)
if not video_only:
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
    recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '45', '/sdcard/sonet-transitions.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    for cycle in range(5):
        for step, label in enumerate(['lyrics', 'lyrics', 'fullscreen lyrics', 'fullscreen lyrics']):
            # Coordinates move during fullscreen layout. Query the settled control
            # rather than replaying stale positions against a software-rendered UI.
            screen = snapshot(f'16-transition-{cycle}-{step}')
            tap_label(screen, label)
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
else:
    video_header = player
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PLAY')
tap_label(video_header, 'video')
# Reproduce video -> audio-only -> video. The cover must come back without
# requiring a hidden Video toggle to be switched off on a different song.
time.sleep(1)
tap_label(snapshot('19-switch-from-video'), 'minimize player')
tap_label(snapshot('19-audio-library'), 'sonet ui check 2')
time.sleep(2)
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PAUSE')
tap_label(snapshot('19-audio-mini'), 'sonet ui check 2')
audio_only = snapshot('19-audio-only-cover')
assert not any(n.get('content-desc', '') in ('Video', 'Video loading', 'Video ready', 'Video playing') for n in audio_only.iter('node')), 'Video mode leaked into audio-only track'
from PIL import Image, ImageStat
cover = Image.open(out / '19-audio-only-cover.png').convert('RGB')
w, h = cover.size
region = cover.crop((int(w * .12), int(h * .24), int(w * .88), int(h * .40)))
assert max(ImageStat.Stat(region).stddev) > 8, 'Audio-only cover stayed blank after leaving video mode'
tap_label(audio_only, 'minimize player')
tap_label(snapshot('19-return-video-library'), 'sonet ui check 1')
time.sleep(2)
tap_label(snapshot('19-return-video-mini'), 'sonet ui check 1')
returned = snapshot('19-video-option-reset')
assert not any(n.get('content-desc', '') in ('Video loading', 'Video ready', 'Video playing') for n in returned.iter('node')), 'Video selection was not reset on track change'
adb('shell', 'input', 'keyevent', 'KEYCODE_MEDIA_PLAY')
tap_label(returned, 'video')
video_result = None
for attempt in range(40):
    video = snapshot('19-video-' + str(attempt))
    descriptions = [n.get('content-desc', '') for n in video.iter('node')]
    if any('Video playing' in d for d in descriptions):
        video_result = 'Native video rendered and playback advanced'
        break
    time.sleep(1)
assert video_result is not None, 'Known public clip never rendered advancing video; READY/cover fallback does not pass'
# A loaded clip must survive collapse without a second extraction or loading overlay.
resolved_before = adb('logcat', '-d', '-s', 'SonetVideo:I').count(b'Video stream resolved:')
tap_label(video, 'minimize player')
time.sleep(2)
tap_label(snapshot('20-video-collapsed'), 'sonet ui check 1')
reopened = snapshot('20-video-reopened')
assert not any('Video loading' in n.get('content-desc', '') for n in reopened.iter('node')), 'Video lost its loaded session on collapse'
time.sleep(2)
assert adb('logcat', '-d', '-s', 'SonetVideo:I').count(b'Video stream resolved:') == resolved_before, 'Collapse extracted the video again'
# Tapping the surface changes presentation without restarting the decoder.
tap_label(reopened, 'Video playing')
immersive = snapshot('21-video-immersive')
assert any('Video immersive' in n.get('content-desc', '') for n in immersive.iter('node')), 'Tap did not expand the video'
assert any('Equalizer' in n.get('content-desc', '') for n in immersive.iter('node')), 'Immersive video covered the toolbar'
# Expanded footage must reveal the same artwork palette as the fitted cover.
image = Image.open(out / '21-video-immersive.png').convert('RGB')
tap_label(immersive, 'Video immersive')
snapshot('21-video-fit')
fitted = Image.open(out / '21-video-fit.png').convert('RGB')
edge_y = min(image.height - 6, int(image.width / .82) + 2)
patch = (0, edge_y, 12, edge_y + 4)
expanded_color = ImageStat.Stat(image.crop(patch)).mean
fitted_color = ImageStat.Stat(fitted.crop(patch)).mean
assert max(abs(a - b) for a, b in zip(expanded_color, fitted_color)) < 6, 'Expanded video loses the artwork palette'
assert adb('logcat', '-d', '-s', 'SonetVideo:I').count(b'Video stream resolved:') == resolved_before, 'Changing presentation restarted extraction'
(out / 'video-result.txt').write_text(video_result)
print('VIDEO:', video_result)

validate_page_actions()

log = adb('logcat', '-d').decode(errors='replace')
(out / 'player-logcat.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log or 'Process: ' + package not in log, 'App crash; inspect player-logcat'
assert adb('shell', 'pidof', package).strip(), 'App stopped'
(out / 'gfxinfo.txt').write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package))
print('PASS: native video frames and video-to-audio cover restoration' if video_only else 'PASS: expanded/mini player, repeat, sleep timer, rotation and lyrics tools exercised')
