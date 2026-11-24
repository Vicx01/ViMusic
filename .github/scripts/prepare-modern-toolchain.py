#!/usr/bin/env python3
"""Prepare the next Kotlin/AGP port only in a disposable Actions copy."""
import os
from pathlib import Path

if os.environ.get('CI') != 'true' or not os.environ.get('RUNNER_TEMP'):
    raise SystemExit('This candidate runs only in GitHub Actions')
root = Path(os.environ['RUNNER_TEMP']).resolve() / 'vimusic-modern-candidate'
if not root.is_dir():
    raise SystemExit('Missing disposable candidate')

def replace(path, old, new):
    target = root / path
    text = target.read_text()
    if text.count(old) != 1:
        raise SystemExit(f'Unexpected source in {path}: {old!r}')
    target.write_text(text.replace(old, new))

replace('settings.gradle.kts', 'version("kotlin", "1.9.25")', 'version("kotlin", "2.3.10")')
replace('build.gradle.kts', 'classpath("com.android.tools.build", "gradle", "7.3.0")', 'classpath("com.android.tools.build", "gradle", "9.1.1")')
replace('build.gradle.kts', 'classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))', 'classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))\n        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")')
# AGP 9 supports this temporary compatibility bridge; no source or feature is removed.
with (root / 'gradle.properties').open('a') as stream:
    stream.write('\nandroid.builtInKotlin=false\nandroid.newDsl=false\n')
for module in ('app', 'compose-persist', 'compose-reordering', 'compose-routing'):
    path = f'{module}/build.gradle.kts'
    replace(path, '    kotlin("android")', '    kotlin("android")\n    id("org.jetbrains.kotlin.plugin.compose")')
    replace(path, '    composeOptions {\n        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()\n    }\n\n', '')
print('Prepared AGP 9.1.1 / Gradle 9.3.1 / Kotlin and Compose compiler 2.3.10 candidate. Ktor and SDK declarations are unchanged.')
