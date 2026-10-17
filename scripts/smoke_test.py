#!/usr/bin/env python3
"""Check real admin sessions and H2 restart persistence with a disposable database.

Requires a built backend JAR and the same Firebase Admin credential as the application.
Student token exchange is covered by isolated backend tests instead of expiring live tokens.
"""

import http.cookiejar
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'apps/backend/target/backend-0.1.0-SNAPSHOT.jar'


def client(base):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(path, method='GET', body=None, status=200):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(
            base + path,
            data=data,
            method=method,
            headers={'Content-Type': 'application/json', 'X-StudyBuddy-Request': '1'},
        )
        try:
            response = opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            value = json.loads(response.read())
            assert response.code == status, (path, response.code, value)
            return value

    request('/api/session/admin-login', 'POST', {
        'username': os.environ.get('ADMIN_USERNAME', 'admin'),
        'password': os.environ.get('ADMIN_PASSWORD', 'admin123'),
    })
    return request


def start(database, output):
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen(
        ['java', '-jar', str(JAR), f'--server.port={port}',
         f'--spring.datasource.url=jdbc:h2:file:{database};DB_CLOSE_ON_EXIT=FALSE'],
        cwd=ROOT,
        stdout=output,
        stderr=subprocess.STDOUT,
    )
    base = f'http://127.0.0.1:{port}'
    for _ in range(150):
        if process.poll() is not None:
            raise RuntimeError('Backend stopped unexpectedly. Inspect the smoke-test log.')
        try:
            with urllib.request.urlopen(base + '/', timeout=1) as response:
                if json.loads(response.read()).get('status') == 'running':
                    return process, base
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(0.1)
    stop(process)
    raise TimeoutError('Backend did not become ready within 15 seconds.')


def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def main():
    if not JAR.exists():
        raise SystemExit('Build the backend first: cd apps/backend && ./mvnw package')
    key = os.environ.get('FIREBASE_SERVICE_ACCOUNT')
    if not key or not Path(key).is_file():
        raise SystemExit('Set FIREBASE_SERVICE_ACCOUNT to a local Firebase Admin JSON file first.')
    process = None
    with tempfile.TemporaryDirectory(prefix='studybuddy-smoke-') as temp:
        folder = Path(temp)
        log = folder / 'backend.log'
        try:
            with log.open('w') as output:
                process, base = start(folder / 'database', output)
                admin = client(base)
                assert len(admin('/api/students')) == 50
                assert len(admin('/api/students/courses')) == 10
                config = admin('/api/admin/matching-config')
                config['strategy'] = 'PARETO'
                admin('/api/admin/matching-config', 'PUT', config)
                admin('/api/admin/accounts/S050', 'DELETE')
                stop(process)
                process, base = start(folder / 'database', output)
                admin = client(base)
                assert len(admin('/api/students')) == 49, 'Deleted seed must not reappear'
                assert admin('/api/admin/matching-config')['strategy'] == 'PARETO'
                print('PASS: real admin session, 10 courses, 50 initial profiles and restart persistence')
        except Exception:
            print(log.read_text())
            raise
        finally:
            stop(process)


if __name__ == '__main__':
    main()
