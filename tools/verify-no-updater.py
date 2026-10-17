"""Verify a clean-built fork JAR no longer contains the official update checker."""
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as jar:
    violations = []
    for name in jar.namelist():
        if not name.endswith('.class'):
            continue
        data = jar.read(name)
        if '/impl/updater/' in name or any(token in data for token in (
            b'nl/rutgerkok/blocklocker/impl/updater/',
            b'rutgerkok.nl/tools/updater/', b'runTimerAsync',
        )):
            violations.append(name)
    if b'\nupdater:' in jar.read('config.yml'):
        violations.append('config.yml still advertises updater')
    assert not violations, 'Official update feature remains: ' + ', '.join(violations)
print('PASS: no official updater classes, references, endpoint, timer bridge or default setting')
