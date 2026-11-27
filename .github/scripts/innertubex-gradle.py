#!/usr/bin/env python3
"""Run an isolated Gradle task; emit a sanitized CI error and optional report."""
import argparse
import os
from pathlib import Path
import re
import random
import subprocess
import sys
import time

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
# Retry dependency-only rate limiting, never a compiler or test failure.
for attempt in range(1, 4):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
    output = result.stdout
    retryable = (
        result.returncode != 0
        and 'Received status code 429' in output
        and 'Could not resolve' in output
        and not re.search(r'There were failing tests|Tests failed|^e: |: error:', output, re.MULTILINE)
    )
    if not retryable or attempt == 3:
        break
    print(f'::warning title=Maven rate limit::Gradle dependency resolution received HTTP 429; retry {attempt} of 2.', flush=True)
    time.sleep(attempt * 60 + random.SystemRandom().uniform(0, 30))

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
    lines = safe.splitlines()
    start = next((i for i, line in enumerate(lines) if '* What went wrong:' in line), None)
    primary = '\n'.join(lines[start:start + 40])[:7000] if start is not None else '\n'.join(lines[-40:])[:7000]
    tail = '\n'.join(lines[-12:])[-2500:]
    excerpt = primary + '\n--- tail ---\n' + tail
    escaped = excerpt.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title=InnerTubeX compatibility failed::{escaped}')
else:
    if options.report:
        path = Path(options.report)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(safe + '\n')
    print(f'{label}: completed successfully')
sys.exit(result.returncode)
