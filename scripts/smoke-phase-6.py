#!/usr/bin/env python3
"""Verify real M2 approval and the explicit missing Phase 8 dependency; restore demo authorization."""
import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.parse import urlencode, urlsplit
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location('phase3_smoke', ROOT / 'scripts/smoke-phase-3.py')
_phase3 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_phase3)
SmokeFailure, require = _phase3.SmokeFailure, _phase3.require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-commit', required=True)
    parser.add_argument('--url', default='http://127.0.0.1:18080')
    parser.add_argument('--wait', type=int, default=180)
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    target = urlsplit(args.url)
    require(target.scheme in ('http', 'https') and target.hostname in ('127.0.0.1', 'localhost', '::1')
            and target.username is None and target.password is None and target.path in ('', '/')
            and not target.query and not target.fragment, 'Private demo credentials require the loopback gateway')
    subprocess.run([sys.executable, str(ROOT / 'scripts/smoke-phase-5.py'), '--expected-commit', args.expected_commit,
                    '--url', args.url.rstrip('/'), '--wait', str(args.wait)], cwd=ROOT, check=True)
    env = _phase3.private_environment()
    api = _phase3.Platform(args.url, [v for k, v in env.items() if k.endswith(('PASSWORD', 'SECRET', 'TOKEN'))])
    tokens = {u: api.login(u, env['DEV_SEED_PASSWORD'], role) for u, role in _phase3.DEMO_ROLES.items()}
    owner, other, reviewer, admin = [tokens[u] for u in ('demo_student_01', 'demo_student_02', 'demo_room_admin', 'demo_admin')]

    def call(method, path, body=None, token=owner, status=200, code=None):
        value, trace = api.api(method, path, body, token, status, code)
        api.safe(value)
        return value, trace

    users, _ = call('GET', '/api/v1/users?limit=100', token=admin)
    by_name = {row['username']: row for row in users}
    original = {name: by_name[name] for name in ('demo_student_02', 'demo_room_admin')}
    require(all(not row['roomIds'] for row in original.values()), 'Development smoke requires the empty demo scope baseline')
    rooms, _ = call('GET', '/api/v1/rooms?limit=100')
    room = next(row['id'] for row in rooms if row['type'] == 'CLASSROOM' and row['enabled'])
    start = datetime.now(ZoneInfo(env['CAMPUS_BUSINESS_ZONE'])).replace(minute=0, second=0, microsecond=0) + timedelta(days=3)
    payload = {'roomId': room, 'startAt': start.isoformat(), 'endAt': (start + timedelta(hours=2)).isoformat(),
               'purpose': 'Local Phase 6 runtime verification'}
    created = []
    final_id = None
    traces = {}

    def detail(request_id):
        value, _ = call('GET', f'/api/v1/classroom-requests/{request_id}')
        require(set(value) == {'request', 'steps', 'actions', 'events'}, 'Invalid coherent M2 detail')
        return value

    def submit():
        value, trace = call('POST', '/api/v1/classroom-requests', payload, status=201)
        created.append(value['id'])
        require(value['status'] == 'SUBMITTED', 'Submission did not persist the first stage')
        traces[str(value['id'])] = trace
        return value['id']

    try:
        call('POST', '/api/v1/classroom-requests', payload, token=None, status=401, code='UNAUTHORIZED')
        call('POST', '/api/v1/classroom-requests', payload, token=admin, status=403, code='FORBIDDEN')
        call('POST', '/api/v1/classroom-requests', dict(payload, applicantId=1), status=400, code='VALIDATION_ERROR')
        call('GET', '/api/v1/me/room-scopes', status=403, code='FORBIDDEN')
        for name in original:
            if name == 'demo_student_02':
                call('PUT', f"/api/v1/users/{original[name]['id']}/roles", {'roleCodes': ['STUDENT', 'DEPARTMENT_ADMIN']}, admin)
            call('PUT', f"/api/v1/users/{original[name]['id']}/room-scopes", {'roomIds': [room]}, admin)
        scopes, _ = call('GET', '/api/v1/me/room-scopes', token=other)
        require(scopes == [room], 'The existing second-student JWT did not acquire its explicit current department scope')
        cancelled = submit()
        call('GET', f'/api/v1/classroom-requests/{cancelled}', token=other, status=200)
        # A currently scoped approver can inspect the chain, but a system role alone is not an override.
        call('GET', f'/api/v1/classroom-requests/{cancelled}', token=admin, status=404, code='CLASSROOM_REQUEST_NOT_FOUND')
        call('POST', f'/api/v1/classroom-requests/{cancelled}/cancel')
        rejected = submit()
        call('POST', f'/api/v1/classroom-requests/{rejected}/reject', {'reason': 'Local verification rejection'}, reviewer)
        require(detail(rejected)['request']['status'] == 'REJECTED', 'Rejection was not retained')
        final_id = submit()
        first, _ = call('POST', f'/api/v1/classroom-requests/{final_id}/approve', {}, reviewer)
        require(first['status'] == 'APPROVING', 'First approval did not activate the department stage')
        call('POST', f'/api/v1/classroom-requests/{final_id}/approve', {}, reviewer, 403, 'FORBIDDEN')
        second, _ = call('POST', f'/api/v1/classroom-requests/{final_id}/approve', {}, other)
        require(second['status'] == 'APPROVED_PENDING_SYNC', 'Final approval incorrectly claimed occupancy success')
        deadline = time.monotonic() + 90
        while True:
            current = detail(final_id)['request']
            if current['status'] == 'SYNC_FAILED':
                break
            require(current['status'] == 'APPROVED_PENDING_SYNC', 'Unexpected or fake confirmed approval')
            require(time.monotonic() < deadline, 'The real scheduled synchronization did not finish within its bound')
            time.sleep(1)
        require(current['syncError'] == 'M1_HTTP_403' and current['syncAttempts'] == 1
                and current['occupancyId'] is None, 'Real M1 refusal was hidden or retried as success')
        call('POST', f'/api/v1/classroom-requests/{final_id}/cancel', status=409, code='CLASSROOM_STATE_CONFLICT')
        revoked = submit()
        call('PUT', f"/api/v1/users/{original['demo_room_admin']['id']}/room-scopes", {'roomIds': []}, admin)
        call('POST', f'/api/v1/classroom-requests/{revoked}/approve', {}, reviewer, 403, 'FORBIDDEN')
        pending, _ = call('GET', '/api/v1/classroom-requests?view=pending', token=reviewer)
        require(pending == [], 'Revoked reviewer retained an approval inbox')
        call('POST', f'/api/v1/classroom-requests/{revoked}/cancel')
        query = urlencode({'startAt': payload['startAt'], 'endAt': payload['endAt']})
        available, _ = call('GET', f'/api/v1/reservation/rooms/{room}/availability?' + query)
        require(available['seats'] and all(row['available'] for row in available['seats']), 'Unconfirmed M2 sync changed M1 availability')
    finally:
        cleanup_failures = []
        for request_id in created:
            try:
                current = detail(request_id)['request']
                if current['status'] in ('SUBMITTED', 'APPROVING'):
                    call('POST', f'/api/v1/classroom-requests/{request_id}/cancel')
            except Exception:
                cleanup_failures.append('request:' + str(request_id))
        try:
            call('PUT', f"/api/v1/users/{original['demo_room_admin']['id']}/room-scopes", {'roomIds': original['demo_room_admin']['roomIds']}, admin)
        except Exception:
            cleanup_failures.append('room-admin-scope')
        try:
            current_users, _ = call('GET', '/api/v1/users?limit=100', token=admin)
            current_student = next(row for row in current_users if row['username'] == 'demo_student_02')
            if set(current_student['roles']).intersection({'ROOM_ADMIN', 'DEPARTMENT_ADMIN'}):
                call('PUT', f"/api/v1/users/{original['demo_student_02']['id']}/room-scopes", {'roomIds': original['demo_student_02']['roomIds']}, admin)
            if current_student['roles'] != original['demo_student_02']['roles']:
                call('PUT', f"/api/v1/users/{original['demo_student_02']['id']}/roles", {'roleCodes': original['demo_student_02']['roles']}, admin)
        except Exception:
            cleanup_failures.append('department-role-and-scope')
        require(not cleanup_failures, 'Failed to restore smoke-created changes: ' + ','.join(cleanup_failures))
    restored, _ = call('GET', '/api/v1/users?limit=100', token=admin)
    lookup = {row['username']: row for row in restored}
    require(all(lookup[name]['roles'] == row['roles'] and lookup[name]['roomIds'] == row['roomIds']
                for name, row in original.items()), 'Temporary demo authorization was not restored')
    evidence = {'verifiedAt': datetime.now(timezone.utc).isoformat(), 'gitCommit': args.expected_commit,
                'requestIds': created, 'submissionTraces': traces, 'syncFailedRequestId': final_id,
                'checks': {'phase5Regression': True, 'realDCurrentScopes': True, 'twoStageApproval': True,
                           'rejectionAndPreSyncCancellation': True, 'revokedScopeImmediate': True,
                           'realM1RefusalRecorded': True, 'noFakeApprovedOrOccupancy': True,
                           'temporaryRolesAndScopesRestored': True},
                'scope': 'Real scheduled M1 call currently returns403; true room blocking/release remains Phase8. Timeout boundaries and successful receipt handling are covered by injected-Clock tests / explicitly labeled response fixtures.'}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + '\n')
    print('PASS: real M2 submission, scoped two-stage approvals, rejection/cancel and current permission revocation')
    print('PASS: scheduled real M1 refusal persists SYNC_FAILED without fake occupancy; demo roles/scopes restored')


if __name__ == '__main__':
    try:
        main()
    except (SmokeFailure, OSError, URLError, ValueError, KeyError, TypeError, StopIteration, subprocess.CalledProcessError) as error:
        print('FAIL:', str(error), file=sys.stderr)
        sys.exit(1)
