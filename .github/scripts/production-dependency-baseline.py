#!/usr/bin/env python3
"""Capture sanitized, resolved app dependency trees with the CI Gradle distribution."""
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
REPORTS = ROOT / 'build' / 'reports' / 'production-dependencies'
CONFIGURATIONS = ('debugCompileClasspath', 'debugRuntimeClasspath', 'debugUnitTestRuntimeClasspath')
SENSITIVE = re.compile(r'authorization|cookie|sapisid|po[-_ ]?token|api[-_ ]?key|signed[-_ ]?url|password|secret|credential|bearer', re.IGNORECASE)
URL = re.compile(r'https?://[^\s\]\[<>"\']+', re.IGNORECASE)
MODULE = re.compile(r'\b((?:io\.ktor|org\.jetbrains\.kotlin|org\.jetbrains\.kotlinx):[A-Za-z0-9_.-]+):([A-Za-z0-9_.+\-]+)(?:\s+->\s+([A-Za-z0-9_.+\-]+))?')


def sanitize(text):
    text = URL.sub('[redacted-url]', text)
    text = re.sub(r'AIza[0-9A-Za-z_-]{20,}', '[redacted-key]', text)
    return '\n'.join('[redacted sensitive line]' if SENSITIVE.search(line) else line for line in text.splitlines())


def escape(text):
    return text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def main():
    executable = os.environ.get('GRADLE_EXECUTABLE', str(ROOT / 'gradlew'))
    REPORTS.mkdir(parents=True, exist_ok=True)
    for configuration in CONFIGURATIONS:
        command = [executable, '--no-daemon', '--console=plain', ':app:dependencies', '--configuration', configuration]
        completed = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
        text = sanitize(completed.stdout)
        if completed.returncode or re.search(r'\(FAILED\)', text):
            lines = text.splitlines()
            start = next((i for i, line in enumerate(lines) if '* What went wrong:' in line), None)
            detail = '\n'.join(lines[start:start + 35])[:6000] if start is not None else '\n'.join(lines[-35:])[-6000:]
            print('::error title=Production dependency baseline failed::' + escape(detail))
            return 1
        (REPORTS / f'{configuration}.txt').write_text(text + '\n')
        if configuration == 'debugRuntimeClasspath':
            selected = sorted({f'{match.group(1)}:{match.group(3) or match.group(2)}' for match in MODULE.finditer(text)})
            if not any(item.startswith('io.ktor:ktor-client-core') for item in selected):
                print('::error::Expected Ktor client module missing from runtime dependency graph.')
                return 1
            (REPORTS / 'runtime-selected.txt').write_text('\n'.join(selected) + '\n')
            print('::notice title=Production runtime dependency baseline::' + escape('\n'.join(selected)[:9000]))
        print(f'{configuration}: sanitized dependency tree captured')
    return 0


if __name__ == '__main__':
    sys.exit(main())
