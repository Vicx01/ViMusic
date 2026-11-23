#!/usr/bin/env python3
"""Reproduce Room/KAPT behavior in a disposable Actions checkout, never production."""
import os
from pathlib import Path
import re
import subprocess
import sys

case = os.environ['ROOM_DIAGNOSTIC_CASE']
project = Path(os.environ['RUNNER_TEMP']) / 'room-interop-copy'
report = Path(os.environ['RUNNER_TEMP']) / 'room-interop-report'
report.mkdir(parents=True, exist_ok=True)
settings = project / 'settings.gradle.kts'
text = settings.read_text()
assert text.count('version("kotlin", "1.8.22")') == 1
assert text.count('version("compose-compiler", "1.4.8")') == 1
assert text.count('version("room", "2.5.0-beta01")') == 1
if case != 'baseline':
    text = text.replace('version("kotlin", "1.8.22")', 'version("kotlin", "1.9.25")')
    text = text.replace('version("compose-compiler", "1.4.8")', 'version("compose-compiler", "1.5.15")')
if case == 'room252':
    text = text.replace('version("room", "2.5.0-beta01")', 'version("room", "2.5.2")')
elif case == 'room261':
    text = text.replace('version("room", "2.5.0-beta01")', 'version("room", "2.6.1")')
elif case not in ('baseline', 'kotlin19', 'static'):
    raise SystemExit('Unknown diagnostic case')
settings.write_text(text)
if case == 'static':
    source = project / 'app/src/main/kotlin/it/vfsfitvnm/vimusic/Database.kt'
    data = source.read_text()
    for method in ('mediaItemFromByteArray', 'mediaItemToByteArray'):
        old = f'    @TypeConverter\n    fun {method}('
        new = f'    @JvmStatic\n    @TypeConverter\n    fun {method}('
        assert data.count(old) == 1
        data = data.replace(old, new)
    source.write_text(data)

exe = os.environ['GRADLE_EXECUTABLE']
command = [exe, '--no-daemon', '--console=plain', ':app:assembleDebug', ':innertube:test', ':kugou:test', ':app:testDebugUnitTest', ':app:assembleDebugAndroidTest']
run = subprocess.run(command, cwd=project, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
# Do not upload unfiltered Gradle output or secrets. Extract only the known compiler
# error and known-generated converter references, plus metadata version numbers.
lines = run.stdout.splitlines()
errors = [line.strip() for line in lines if 'error:' in line or 'What went wrong:' in line or 'BUILD FAILED' in line or 'BUILD SUCCESSFUL' in line]
errors = [re.sub(r'https?://\S+', '[redacted-url]', line) for line in errors if 'Converters' in line or line in ('* What went wrong:', 'BUILD FAILED', 'BUILD SUCCESSFUL')]
summary = [f'case={case}', f'gradle_exit={run.returncode}', *errors[-12:]]
graph = subprocess.run([exe, '--no-daemon', '--console=plain', ':app:dependencies', '--configuration', 'kapt'], cwd=project, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
summary.append(f'kapt_graph_exit={graph.returncode}')
for line in graph.stdout.splitlines():
    if ('androidx.room:room-compiler:' in line or 'org.jetbrains.kotlinx:kotlinx-metadata-jvm:' in line) and len(line) < 350:
        summary.append('kapt graph: ' + line.strip())
for name, pattern in [('generated', 'app/build/generated/source/kapt/debug/**/Database_Impl.java'), ('stub', 'app/build/tmp/kapt3/stubs/debug/**/Converters.java')]:
    matches = list(project.glob(pattern))
    summary.append(f'{name}_files={len(matches)}')
    if len(matches) == 1:
        content = matches[0].read_text(errors='replace')
        if name == 'generated':
            summary.extend('generated: ' + line.strip() for line in content.splitlines() if 'Converters' in line)
        else:
            metadata = re.search(r'@kotlin\.Metadata\(mv\s*=\s*\{([^}]*)\}', content)
            summary.append('stub_metadata_version=' + (metadata.group(1).strip() if metadata else 'not found'))
            summary.extend('stub: ' + line.strip() for line in content.splitlines() if re.search(r'\b(class Converters|INSTANCE|Converters\(|mediaItemFromByteArray\(|mediaItemToByteArray\()', line))

out = '\n'.join(summary)
(report / f'{case}.txt').write_text(out + '\n')
print('::notice title=Room diagnostic ' + case + '::' + out[:9000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
# The known baseline must pass; the reproduced incompatible pair must fail with
# the observed constructor error. Exploratory candidates report their real status.
if case == 'baseline' and run.returncode != 0:
    print('::error::Baseline diagnostic did not build.')
    sys.exit(1)
if case == 'kotlin19' and (run.returncode == 0 or not any('Converters() has private access' in line for line in errors)):
    print('::error::The known Kotlin 1.9 Room failure did not reproduce as expected.')
    sys.exit(1)
