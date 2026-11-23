#!/usr/bin/env python3
"""Apply the Room experiment only to the disposable CI candidate copy."""
import os
from pathlib import Path

if os.environ.get('CI') != 'true' or not os.environ.get('RUNNER_TEMP'):
    raise SystemExit('This diagnostic may run only in GitHub Actions')
root = Path(os.environ['RUNNER_TEMP']).resolve() / 'room-candidate'
if not root.is_dir():
    raise SystemExit('Disposable candidate copy is missing')
settings = root / 'settings.gradle.kts'
text = settings.read_text()
for old, new in (
    ('version("kotlin", "1.8.22")', 'version("kotlin", "1.9.25")'),
    ('version("compose-compiler", "1.4.8")', 'version("compose-compiler", "1.5.15")'),
):
    assert text.count(old) == 1
    text = text.replace(old, new)
settings.write_text(text)
source = root / 'app/src/main/kotlin/it/vfsfitvnm/vimusic/Database.kt'
text = source.read_text()
for method in ('mediaItemFromByteArray', 'mediaItemToByteArray'):
    old = f'    @TypeConverter\n    fun {method}('
    assert text.count(old) == 1
    text = text.replace(old, f'    @JvmStatic\n    @TypeConverter\n    fun {method}(')
source.write_text(text)
print('Applied Kotlin 1.9.25 / Compose 1.5.15 and static converter annotations to disposable copy only.')
