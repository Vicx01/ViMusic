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
replace('build.gradle.kts', 'delete(rootProject.buildDir)', 'delete(rootProject.layout.buildDirectory)')
replace('build.gradle.kts', '        kotlinOptions {', '        compilerOptions {')
replace('build.gradle.kts', '                    freeCompilerArgs = freeCompilerArgs + listOf(', '                    freeCompilerArgs.addAll(listOf(')
replace('build.gradle.kts', '${project.buildDir.absolutePath}/compose_metrics"\n                    )', '${project.layout.buildDirectory.get().asFile.absolutePath}/compose_metrics"\n                    ))')
# AGP 9 supports this temporary compatibility bridge; no source or feature is removed.
with (root / 'gradle.properties').open('a') as stream:
    stream.write('\nandroid.builtInKotlin=false\nandroid.newDsl=false\n')
for module in ('app', 'compose-persist', 'compose-reordering', 'compose-routing'):
    path = f'{module}/build.gradle.kts'
    replace(path, '    kotlin("android")', '    kotlin("android")\n    id("org.jetbrains.kotlin.plugin.compose")')
    replace(path, '    composeOptions {\n        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()\n    }\n\n', '')
    target = root / path
    data = target.read_text()
    for with_context in (True, False):
        old = '    kotlinOptions {\n' + ('        freeCompilerArgs += "-Xcontext-receivers"\n' if with_context else '') + '        jvmTarget = "1.8"\n    }\n'
        if old in data:
            args = '        freeCompilerArgs.add("-Xcontext-receivers")\n' if with_context else ''
            data = data.replace(old, '')
            data += '\nkotlin {\n    compilerOptions {\n' + args + '        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)\n    }\n}\n'
            break
    else:
        raise SystemExit(f'Cannot find Kotlin compiler options in {path}')
    target.write_text(data)
print('Prepared AGP 9.1.1 / Gradle 9.3.1 / Kotlin and Compose compiler 2.3.10 candidate. Ktor and SDK declarations are unchanged.')
