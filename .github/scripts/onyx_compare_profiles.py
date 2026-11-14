"""Catch a return of automatic idle grid animation using a matched control."""
import json
from pathlib import Path

root = Path('smoke/performance')
before = json.loads((root / 'baseline-metrics.json').read_text())
after = json.loads((root / 'candidate-metrics.json').read_text())
assert before['idle']['frames'] >= 30, 'Control did not reproduce automatic title animation'
assert after['idle']['frames'] <= max(4, before['idle']['frames'] * .1), 'Candidate still redraws the idle grid repeatedly'
assert after['home_idle']['frames'] <= max(4, before['home_idle']['frames'] * .1), 'Candidate still animates idle Home'
comparison = {'baseline': before, 'candidate': after,
              'idle_redraw_reduction_percent': round(100 * (1 - after['idle']['frames'] / before['idle']['frames']), 1),
              'scope': 'One paused local album grid in the same software-rendered emulator; not phone FPS.'}
(root / 'comparison.json').write_text(json.dumps(comparison, indent=2))
print('PASS: matched grid profile:', json.dumps(comparison))
