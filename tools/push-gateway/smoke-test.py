"""Exercise the local-only dev gateway; no real account or push token is used."""
import json
import os
import urllib.error
import urllib.request

base = os.environ.get('PUSH_GATEWAY_URL', 'http://127.0.0.1:18787')
actor = 'did:plc:caelumgatewaytest'
payload = {
    'serviceDid': 'did:web:push.noz.am',
    'platform': 'android',
    'token': 'caelum-smoke-test-not-a-real-fcm-token',
    'appId': 'am.noz.caelum',
}


def request(path, body=None, authenticated=False):
    headers = {'Content-Type': 'application/json'}
    if authenticated:
        headers['X-Actor-DID'] = actor
    req = urllib.request.Request(
        base + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        return error.code, None


assert request('/health')[0] == 200
status, did = request('/.well-known/did.json')
assert status == 200 and did['id'] == payload['serviceDid']
assert did['service'][0]['serviceEndpoint'] == 'https://push.noz.am'
register = '/xrpc/app.bsky.notification.registerPush'
unregister = '/xrpc/app.bsky.notification.unregisterPush'
assert request(register, payload)[0] == 401
assert request(register, {**payload, 'serviceDid': 'did:web:wrong.example'}, True)[0] == 400
try:
    assert request(register, payload, True)[0] == 200
    assert request(register, payload, True)[0] == 200
    status, result = request('/test/push', {'actorDid': actor})
    assert status == 200 and result['tokens'] == 1
finally:
    assert request(unregister, payload, True)[0] == 200
assert request('/test/push', {'actorDid': actor})[0] == 404
print('PASS: health, DID discovery, auth rejection, DID validation, registration, deduplication, logout cleanup')
