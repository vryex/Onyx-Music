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


def snapshot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'uiautomator', 'dump', '/sdcard/onyx-window.xml')
    xml = adb('exec-out', 'cat', '/sdcard/onyx-window.xml')
    (OUT / (name + '.xml')).write_bytes(xml)
    root = ET.fromstring(xml)
    if any(n.get('resource-id') == 'android:id/alertTitle' and n.get('text') == "Pixel Launcher isn't responding" for n in root.iter('node')):
        close = next(n for n in root.iter('node') if n.get('resource-id') == 'android:id/aerr_close')
        b = [int(v) for v in re.findall(r'\d+', close.get('bounds', ''))]
        assert len(b) == 4, 'Missing launcher-dialog dismissal bounds'
        print('Emulator recovery: dismissing Pixel Launcher ANR; app ANRs remain failures')
        (OUT / (name + '-launcher-anr.xml')).write_bytes(xml)
        adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(2)
        return snapshot(name)
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


adb('install', '-r', 'dist/Onyx-Music.apk')
adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS', check=False)
adb('logcat', '-c')
adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
time.sleep(10)
assert adb('shell', 'pidof', PACKAGE).strip(), 'App terminated during launch'
home = snapshot('01-home')
tap_label(home, 'library')
library = snapshot('02-library')
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
tap_label(snapshot('04-library-portrait'), 'search')
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
