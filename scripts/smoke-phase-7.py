#!/usr/bin/env python3
"""Check the real M3 gateway workflow; retain history and restore temporary demo roles."""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.parse import urlencode, urlsplit

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
            and not target.query and not target.fragment, 'Private credentials require the loopback gateway')
    subprocess.run([sys.executable, str(ROOT / 'scripts/smoke-phase-6.py'), '--expected-commit', args.expected_commit,
                    '--url', args.url.rstrip('/'), '--wait', str(args.wait)], cwd=ROOT, check=True)
    env = _phase3.private_environment()
    api = _phase3.Platform(args.url, [v for k, v in env.items() if k.endswith(('PASSWORD', 'SECRET', 'TOKEN'))])
    tokens = {u: api.login(u, env['DEV_SEED_PASSWORD'], role) for u, role in _phase3.DEMO_ROLES.items()}
    owner, other, staff, manager, admin = [tokens[u] for u in ('demo_student_01', 'demo_student_02',
                  'demo_property_staff', 'demo_room_admin', 'demo_admin')]

    def call(method, path, body=None, token=owner, status=200, code=None):
        value, trace = api.api(method, path, body, token, status, code)
        api.safe(value)
        return value, trace

    users, _ = call('GET', '/api/v1/users?limit=100', token=admin)
    by_name = {row['username']: row for row in users}
    original = {name: by_name[name] for name in ('demo_room_admin', 'demo_property_staff')}
    require(all(not row['roomIds'] for row in original.values()), 'Development smoke requires the empty scope baseline')
    rooms, _ = call('GET', '/api/v1/rooms?limit=100')
    room = next(row['id'] for row in rooms if row['type'] == 'CLASSROOM' and row['enabled'])
    path = '/api/v1/maintenance-tickets'
    body = {'roomId': room, 'description': 'Local Phase 7 runtime repair verification'}
    created, traces = [], {}
    final_id = None

    def report():
        value, trace = call('POST', path, body, status=201)
        created.append(value['id'])
        traces[str(value['id'])] = trace
        require(value['status'] == 'REPORTED' and not value['escalated'], 'Invalid report baseline')
        return value['id']

    def detail(ticket_id):
        value, _ = call('GET', f'{path}/{ticket_id}')
        require(set(value) == {'ticket', 'events', 'review'}, 'Invalid coherent ticket detail')
        return value

    def assign(ticket_id):
        value, _ = call('POST', f'{path}/{ticket_id}/assign',
                       {'assigneeId': original['demo_property_staff']['id']}, manager)
        require(value['status'] == 'ASSIGNED' and value['dueAt'] is not None, 'Assignment lacks persisted deadline')

    try:
        call('POST', path, body, token=None, status=401, code='UNAUTHORIZED')
        call('POST', path, body, token=admin, status=403, code='FORBIDDEN')
        call('POST', path, dict(body, reporterId=1), status=400, code='VALIDATION_ERROR')
        call('GET', path + '?view=managed', token=admin, status=403, code='FORBIDDEN')
        call('PUT', f"/api/v1/users/{original['demo_room_admin']['id']}/roles",
             {'roleCodes': ['ROOM_ADMIN', 'PROPERTY_MANAGER']}, admin)
        first = report()
        call('GET', f'{path}/{first}', token=other, status=404, code='MAINTENANCE_NOT_FOUND')
        call('GET', f'{path}/{first}', token=admin, status=404, code='MAINTENANCE_NOT_FOUND')
        call('GET', f'{path}/{first}', token=manager)
        call('POST', f'{path}/{first}/cancel')
        second = report()
        call('POST', f'{path}/{second}/assign', {'assigneeId': by_name['demo_student_02']['id']},
             manager, 422, 'MAINTENANCE_INVALID_ASSIGNEE')
        assign(second)
        call('POST', f'{path}/{second}/cancel')
        final_id = report()
        assign(final_id)
        call('POST', f'{path}/{final_id}/start', token=owner, status=403, code='FORBIDDEN')
        started, _ = call('POST', f'{path}/{final_id}/start', token=staff)
        require(started['status'] == 'BLOCKING_ROOM' and started['syncOperation'] == 'BLOCK', 'Start falsely claimed room blocking')
        deadline = time.monotonic() + 90
        while True:
            current = detail(final_id)['ticket']
            if current['status'] == 'SYNC_FAILED':
                break
            require(current['status'] == 'BLOCKING_ROOM', 'Unexpected confirmed repair state')
            require(time.monotonic() < deadline, 'Real scheduled M1 synchronization did not finish')
            time.sleep(1)
        require(current['syncAttempts'] == 1 and current['syncError'] == 'M1_HTTP_403'
                and current['occupancyId'] is None, 'M1 refusal was hidden or retried')
        call('POST', f'{path}/{final_id}/finish', {'note': 'Should not falsely finish'}, staff,
             409, 'MAINTENANCE_STATE_CONFLICT')
        call('POST', f'{path}/{final_id}/cancel', status=409, code='MAINTENANCE_STATE_CONFLICT')
        call('POST', f'{path}/{final_id}/review', {'rating': 5}, status=409, code='MAINTENANCE_STATE_CONFLICT')
        revoked = report()
        assign(revoked)
        call('PUT', f"/api/v1/users/{original['demo_property_staff']['id']}/roles", {'roleCodes': ['STUDENT']}, admin)
        call('POST', f'{path}/{revoked}/start', token=staff, status=403, code='FORBIDDEN')
        call('GET', path + '?view=assigned', token=staff, status=403, code='FORBIDDEN')
        call('POST', f'{path}/{revoked}/cancel')
    finally:
        failures = []
        for ticket_id in created:
            try:
                value = detail(ticket_id)['ticket']
                if value['status'] in ('REPORTED', 'ASSIGNED'):
                    call('POST', f'{path}/{ticket_id}/cancel')
            except Exception:
                failures.append('ticket:' + str(ticket_id))
        for name, row in original.items():
            try:
                call('PUT', f"/api/v1/users/{row['id']}/roles", {'roleCodes': row['roles']}, admin)
            except Exception:
                failures.append('roles:' + name)
        require(not failures, 'Failed to restore temporary smoke changes: ' + ','.join(failures))
    restored, _ = call('GET', '/api/v1/users?limit=100', token=admin)
    lookup = {row['username']: row for row in restored}
    require(all(lookup[name]['roles'] == row['roles'] and lookup[name]['roomIds'] == row['roomIds']
                for name, row in original.items()), 'Demo roles/scopes were not restored')
    evidence = {'verifiedAt': datetime.now(timezone.utc).isoformat(), 'gitCommit': args.expected_commit,
                'ticketIds': created, 'submissionTraces': traces, 'syncFailedTicketId': final_id,
                'checks': {'phase6Regression': True, 'reportAndScopedVisibility': True,
                           'realDStaffEligibilityAndManagerAssignment': True, 'preSyncCancellation': True,
                           'currentStaffRevocation': True, 'scheduledRealM1Refusal': True,
                           'noFakeMaintenanceCompletionOrOccupancy': True, 'demoRolesAndScopesRestored': True},
                'scope': 'Real M1 currently403: live checks stop at truthful SYNC_FAILED. Exact receipt/finish/accept/rework/review/release and retry/lease use labeled external response fixtures with real M3 DB and D identities; actual blocking/release remains Phase8.'}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + '\n')
    print('PASS: real M3 report, current manager/staff assignment, pre-sync cancel and immediate revocation')
    print('PASS: actual scheduled M1 403 persists SYNC_FAILED; no fake repair completion; demo roles restored')


if __name__ == '__main__':
    try:
        main()
    except (SmokeFailure, OSError, URLError, ValueError, KeyError, TypeError, StopIteration, subprocess.CalledProcessError) as error:
        print('FAIL:', str(error), file=sys.stderr)
        sys.exit(1)
