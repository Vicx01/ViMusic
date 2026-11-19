#!/usr/bin/env python3
"""Run an isolated Gradle task; emit a sanitized CI error and optional report."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--project', required=True)
parser.add_argument('--report')
parser.add_argument('arguments', nargs=argparse.REMAINDER)
options = parser.parse_args()
arguments = options.arguments
if arguments and arguments[0] == '--':
    arguments = arguments[1:]
if not arguments:
    parser.error('at least one Gradle argument is required')

command = ['gradle', '-p', options.project, '--no-daemon', '--console=plain', *arguments]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')

def sanitize(text):
    text = re.sub(r'https?://[^\s\]\[<>"\']+', '[redacted-url]', text, flags=re.IGNORECASE)
    text = re.sub(r'AIza[0-9A-Za-z_-]{20,}', '[redacted-key]', text)
    lines = []
    for line in text.splitlines():
        if re.search(r'authorization|cookie|sapisid|po[-_ ]?token|api[-_ ]?key|signed[-_ ]?url|password|secret|credential|bearer', line, re.IGNORECASE):
            lines.append('[redacted sensitive line]')
        else:
            lines.append(line)
    return '\n'.join(lines)

safe = sanitize(result.stdout)
label = next((arg for arg in arguments if arg.startswith(':')), 'Gradle')
if result.returncode:
    # The full log is not uploaded; annotations contain a bounded, sanitized tail.
    excerpt = '\n'.join(safe.splitlines()[-55:])[-10000:]
    escaped = excerpt.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title=InnerTubeX compatibility failed::{escaped}')
else:
    if options.report:
        path = Path(options.report)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(safe + '\n')
    print(f'{label}: completed successfully')
sys.exit(result.returncode)
