import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path('smoke')
OUT.mkdir(exist_ok=True)
PACKAGE = 'com.onyx.music'
ACTIVITY = PACKAGE + '/com.metrolist.music.MainActivity'


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout


def snapshot(name, recovered_launcher=False):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'uiautomator', 'dump', '/sdcard/onyx-window.xml')
    xml = adb('exec-out', 'cat', '/sdcard/onyx-window.xml')
    (OUT / (name + '.xml')).write_bytes(xml)
    root = ET.fromstring(xml)
    if any(n.get('resource-id') == 'android:id/alertTitle' and n.get('text') == "Pixel Launcher isn't responding" for n in root.iter('node')):
        assert not recovered_launcher, 'Pixel Launcher remained unresponsive after one recovery'
        close = next(n for n in root.iter('node') if n.get('resource-id') == 'android:id/aerr_close')
        b = [int(v) for v in re.findall(r'\d+', close.get('bounds', ''))]
        assert len(b) == 4, 'Missing launcher-dialog dismissal bounds'
        print('Emulator recovery: dismissing Pixel Launcher ANR; app ANRs remain failures')
        (OUT / (name + '-launcher-anr.xml')).write_bytes(xml)
        adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(2)
        return snapshot(name, recovered_launcher=True)
    return root


def tap_label(root, label):
    matches = []
    for node in root.iter('node'):
        text = (node.get('text', '') + ' ' + node.get('content-desc', '')).strip().lower()
        bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        if label in text and len(bounds) == 4:
            matches.append(bounds)
    if not matches:
        raise AssertionError('Missing visible control: ' + label)
    left, top, right, bottom = max(matches, key=lambda b: b[3])
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(2)


def scroll_to_label(root, label, name='find-control', max_swipes=6):
    width, height = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size').decode()).groups())
    for attempt in range(max_swipes + 1):
        if any(label.lower() in (n.get('text', '') + ' ' + n.get('content-desc', '')).lower()
               for n in root.iter('node')):
            return root
        if attempt == max_swipes:
            break
        adb('shell', 'input', 'swipe', str(width // 2), str(int(height * .76)),
            str(width // 2), str(int(height * .38)), '350')
        time.sleep(.5)
        root = snapshot(name + '-' + str(attempt))
    raise AssertionError('Missing control after scrolling: ' + label)


adb('install', '-r', 'dist/Sonet.apk')
adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS', check=False)
adb('logcat', '-c')
adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
time.sleep(10)
assert adb('shell', 'pidof', PACKAGE).strip(), 'App terminated during launch'
home = snapshot('01-home')
tap_label(home, 'library')
library = snapshot('02-library')
for index, tab in enumerate(['home', 'library', 'home', 'library']):
    root = library
    matches = []
    for node in root.iter('node'):
        if node.get('text', '').lower() == tab:
            bounds = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
            if len(bounds) == 4:
                matches.append(bounds)
    assert matches, 'Missing navigation tab: ' + tab
    left, top, right, bottom = max(matches, key=lambda b: b[3])
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(.06)
    (OUT / ('02-transition-' + str(index) + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
time.sleep(.4)
library = snapshot('02-after-interrupted-tabs')
assert not any(n.get('text', '') == 'Made for your next favorite song.' for n in library.iter('node')), 'Home remained over the Library after tab navigation'

assert any(
    node.get('selected') == 'true' and any(
        'library' in (child.get('text', '') + child.get('content-desc', '')).lower()
        for child in node.iter('node')
    ) for node in library.iter('node')
), 'Library tab did not become selected' 
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
time.sleep(3)
snapshot('03-library-landscape')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
time.sleep(2)
tap_label(scroll_to_label(snapshot('04-library-portrait'), 'search', '04-library-search'), 'search')
search = snapshot('05-search')
assert any(n.get('class', '').endswith('EditText') for n in search.iter('node')), 'Search input did not open'
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
adb('shell', 'am', 'start', '-W', '-n', ACTIVITY, '-a', 'android.intent.action.VIEW', '-d', 'https://music.youtube.com/channel/UC0WP5P-ufpRfjbNrmOWwLBQ')
time.sleep(10)
artist = snapshot('06-artist')
assert any(n.get('resource-id', '') == 'onyx_artist' for n in artist.iter('node')), 'Artist screen did not open'
log = adb('logcat', '-d').decode(errors='replace')
(OUT / 'logcat.txt').write_text(log)
assert adb('shell', 'pidof', PACKAGE).strip(), 'App terminated after navigation'
assert 'FATAL EXCEPTION' not in log or 'Process: ' + PACKAGE not in log, 'App crashed; inspect logcat'
print('PASS: signed release installed; home, library, search, artist navigation and rotation exercised')
