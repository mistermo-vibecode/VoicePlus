"""Export the approved HTML phone frames and populate existing F-Droid slots.

Requires agent-browser and its installed Chromium. Run from any directory.
Only documentation and promotional images are written; no app/device data changes.
"""
import base64
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent
repo = root.parents[1]
session = subprocess.check_output(
    ['agent-browser', 'session', 'id', '--scope', 'worktree', '--prefix', 'screenshot-export'],
    cwd=repo, text=True,
).strip()
browser = ['agent-browser', '--session', session]

def run(*args):
    return subprocess.check_output(browser + list(args), text=True)

def evaluate(script):
    return run('eval', '-b', base64.b64encode(script.encode()).decode())

names = ['playback', 'listening-log', 'bookmarks', 'listening-statistics',
         'library', 'characters', 'playback-toolbar', 'playback-settings', 'sleep-timer']
output = root / 'framed'
output.mkdir(exist_ok=True)
try:
    run('--allow-file-access', 'open', (root / 'preview.html').as_uri())
    run('set', 'viewport', '1120', '900', '2')
    run('wait', '--fn', 'Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)')
    evaluate('''
      window.exportFigures = Array.from(document.querySelectorAll('.gallery figure'));
      document.querySelector('main').style.display = 'none';
      const box = document.createElement('div'); box.id = 'export';
      box.style.cssText = 'width:349px;height:772px;padding:32px;background:#0a0d13;';
      document.body.append(box);
    ''')
    for index, name in enumerate(names):
        evaluate(f"document.querySelector('#export').replaceChildren(window.exportFigures[{index}].cloneNode(true))")
        run('wait', '--fn', "document.querySelector('#export img').complete")
        run('screenshot', '#export', str(output / (name + '.png')))
        print('Exported', name, flush=True)
finally:
    run('close')

# Preserve legacy paths because removal alone previously left orphaned F-Droid images.
# Historical filenames are slot identifiers; two now showcase newer features.
phone_slots = {
    '1_en-US.png': 'library', '1_library.png': 'library',
    '2_en-US.png': 'playback', '2_playback.png': 'playback',
    '3_en-US.png': 'bookmarks', '3_sleep_timer.png': 'sleep-timer',
    '4_en-US.png': 'playback-toolbar', '4_character_list.png': 'characters',
    '5_edit_book.png': 'playback-toolbar', '6_settings.png': 'playback-settings',
    '7_listening_log.png': 'listening-log', '8_listening_stats.png': 'listening-statistics',
}
metadata = repo / 'fastlane/metadata/android/en-US/images'
for target, source in phone_slots.items():
    shutil.copyfile(root / 'phone' / (source + '.png'), metadata / 'phoneScreenshots' / target)
tablet_slots = ['library', 'playback', 'listening-log', 'listening-statistics', 'bookmarks']
for source_dir, target_dir in [('tablet-7', 'sevenInchScreenshots'), ('tablet-10', 'tenInchScreenshots')]:
    destination = metadata / target_dir
    destination.mkdir(exist_ok=True)
    for number, name in enumerate(tablet_slots, 1):
        shutil.copyfile(root / source_dir / (name + '.png'), destination / f'{number}_en-US.png')
print('Updated 12 phone slots and 10 tablet slots with plain captures.')
