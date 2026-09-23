const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

function load(file, mocks = {}, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8')
    .replaceAll('import.meta.env', '__env');
  const output = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText;
  const exports = {};
  vm.runInNewContext(output, {
    exports, require(name) {
      if (name in mocks) return mocks[name];
      if (name === 'react') return { ...React, default: React };
      if (name === 'react/jsx-runtime') return require(name);
      throw new Error('Unexpected dependency: ' + name);
    },
    console, URLSearchParams, setTimeout, clearTimeout, __env: {}, ...globals,
  });
  return exports;
}

const plain = value => JSON.parse(JSON.stringify(value));
const tick = () => new Promise(resolve => setImmediate(resolve));
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
};
const identity = { username: 'alice', email: 'alice@example.com', nickname: 'Alice' };
const unauthorized = { response: { status: 401 } };
const auth = load('services/authSession.ts');
const navigation = load('services/authNavigation.ts');

function sessionHarness(overrides = {}) {
  const removed = [];
  const calls = [];
  const api = {
    async currentUser() { calls.push('me'); return identity; },
    async login() { calls.push('login'); },
    async logout() { calls.push('logout'); },
    ...overrides,
  };
  const storage = {
    getItem() { throw new Error('Cached identity must never be read'); },
    setItem() { throw new Error('Identity or tokens must never be persisted'); },
    removeItem(key) { removed.push(key); },
  };
  return { session: auth.createAuthSession(api, storage), calls, removed };
}

test('startup ignores cached identity and waits for /me', async () => {
  const pending = deferred();
  const h = sessionHarness({ currentUser: () => pending.promise });
  const restoring = h.session.restore();
  assert.deepEqual(plain(h.session.getSnapshot()), { user: null, isLoading: true, error: null });
  assert.ok(h.removed.includes('user'));
  pending.resolve({ ...identity, accessToken: 'must-not-be-retained', password: 'must-not-be-retained' });
  await restoring;
  assert.deepEqual(plain(h.session.getSnapshot().user), identity);
});

test('duplicate startup effects share one /me request', async () => {
  const pending = deferred();
  let reads = 0;
  const h = sessionHarness({ currentUser() { reads++; return pending.promise; } });
  const first = h.session.restore();
  assert.equal(h.session.restore(), first);
  assert.equal(reads, 1);
  pending.resolve(identity);
  await first;
});

test('401 is a normal signed-out state and clears previous room cache', async () => {
  const h = sessionHarness({ currentUser: async () => { throw unauthorized; } });
  assert.equal(await h.session.restore(), null);
  assert.deepEqual(plain(h.session.getSnapshot()), { user: null, isLoading: false, error: null });
  assert.ok(h.removed.includes('bobgourmet_current_room'));
});

test('network failure is not disguised as expiry and supports explicit retry', async () => {
  let unavailable = true;
  const h = sessionHarness({ currentUser: async () => {
    if (unavailable) throw { response: { status: 503 } };
    return identity;
  } });
  await h.session.restore();
  assert.equal(h.session.getSnapshot().user, null);
  assert.ok(h.session.getSnapshot().error);
  assert.equal(h.session.getSnapshot().isLoading, false);
  unavailable = false;
  await h.session.restore();
  assert.deepEqual(plain(h.session.getSnapshot().user), identity);
  assert.equal(h.session.getSnapshot().error, null);
});

test('local login uses /me identity, not form input or response JWT', async () => {
  const pending = deferred();
  const calls = [];
  const h = sessionHarness({
    login: async () => { calls.push('login'); return { accessToken: 'ignored' }; },
    currentUser: () => { calls.push('me'); return pending.promise; },
  });
  const loggingIn = h.session.login({ username: 'untrusted-form-name', password: 'test' });
  await tick();
  assert.equal(h.session.getSnapshot().user, null);
  assert.deepEqual(calls, ['login', 'me']);
  pending.resolve(identity);
  await loggingIn;
  assert.deepEqual(plain(h.session.getSnapshot().user), identity);
});

test('failed login does not request /me or authenticate', async () => {
  const h = sessionHarness({ login: async () => { throw unauthorized; } });
  await assert.rejects(h.session.login({ username: 'alice', password: 'wrong' }), error => error === unauthorized);
  assert.equal(h.session.getSnapshot().user, null);
  assert.deepEqual(h.calls, []);
});

test('login is not complete when /me cannot confirm the cookie', async () => {
  const h = sessionHarness({ currentUser: async () => { throw unauthorized; } });
  await assert.rejects(h.session.login({ username: 'alice', password: 'test' }));
  assert.equal(h.session.getSnapshot().user, null);
});

test('late startup response cannot restore a user after logout', async () => {
  const pending = deferred();
  const h = sessionHarness({ currentUser: () => pending.promise });
  const restoring = h.session.restore();
  await h.session.logout();
  pending.resolve(identity);
  await restoring;
  assert.equal(h.session.getSnapshot().user, null);
  assert.equal(h.session.getSnapshot().isLoading, false);
});

test('late startup response cannot overwrite a newer login', async () => {
  const pending = deferred();
  let reads = 0;
  const h = sessionHarness({ currentUser: () => ++reads === 1 ? pending.promise : Promise.resolve(identity) });
  const restoring = h.session.restore();
  await h.session.login({ username: 'alice', password: 'test' });
  pending.resolve({ username: 'old-user', email: 'old@example.com' });
  await restoring;
  assert.equal(h.session.getSnapshot().user.username, 'alice');
});

test('failed logout keeps verified user and does not falsely report success', async () => {
  const h = sessionHarness({ logout: async () => { throw new Error('network down'); } });
  await h.session.restore();
  await assert.rejects(h.session.logout());
  assert.equal(h.session.getSnapshot().user.username, 'alice');
});

test('already expired logout clears identity and room cache', async () => {
  const h = sessionHarness({ logout: async () => { throw unauthorized; } });
  await h.session.restore();
  assert.equal(await h.session.logout(), true);
  assert.equal(h.session.getSnapshot().user, null);
  assert.ok(h.removed.includes('bobgourmet_current_room'));
});

test('HTML fallback or malformed /me response never becomes an authenticated user', async () => {
  for (const invalid of ['<html>SPA fallback</html>', null, { username: 'alice' }, { username: '', email: '' }]) {
    const h = sessionHarness({ currentUser: async () => invalid });
    await h.session.restore();
    assert.equal(h.session.getSnapshot().user, null);
    assert.ok(h.session.getSnapshot().error);
  }
});

test('disabled localStorage does not block server authentication', async () => {
  const session = auth.createAuthSession({ currentUser: async () => identity }, {
    removeItem() { throw new Error('storage denied'); },
  });
  await session.restore();
  assert.equal(session.getSnapshot().user.username, 'alice');
});

test('unsubscribed session listeners receive no subsequent updates', async () => {
  const h = sessionHarness();
  let changes = 0;
  const off = h.session.subscribe(() => changes++);
  await h.session.restore();
  const before = changes;
  off();
  await h.session.logout();
  assert.equal(changes, before);
});

test('Google login starts at the backend-owned authorization endpoint', () => {
  assert.equal(navigation.googleLoginUrl(), '/oauth2/authorization/google');
  assert.equal(navigation.googleLoginUrl('https://api.example/'), 'https://api.example/oauth2/authorization/google');
});

test('callback never treats URL tokens, codes or error text as identity', () => {
  assert.equal(navigation.oauthCallbackIssue(''), null);
  for (const key of ['token', 'access_token', 'code']) {
    assert.equal(navigation.oauthCallbackIssue('?' + key + '=untrusted'), 'legacy');
  }
  assert.equal(navigation.oauthCallbackIssue('?error=oauth_failed&token=untrusted'), 'provider');
  assert.equal(navigation.oauthCallbackIssue('?error='), 'provider');
});

function apiHarness() {
  let failure, config;
  const calls = [], removed = [], timers = [];
  const client = {
    interceptors: { request: { use() {} }, response: { use(_ok, bad) { failure = bad; } } },
    get(url, options) { calls.push({ url, options }); return Promise.resolve({ data: identity }); },
  };
  const module = load('services/api.ts', {
    axios: { default: { create(value) { config = value; return client; } } },
    './roomState': {},
  }, { localStorage: { removeItem: key => removed.push(key) },
    setTimeout: callback => timers.push(callback) });
  return { ...module, failure, config, calls, removed, timers };
}

test('current-user API uses cookie credentials and removes direct code-exchange client', async () => {
  const h = apiHarness();
  await h.authAPI.currentUser();
  assert.deepEqual(plain(h.calls), [{ url: '/auth/me', options: { timeout: 10000 } }]);
  assert.equal(h.config.withCredentials, true);
  assert.equal(h.authAPI.loginWithGoogle, undefined);
});

test('auth endpoint 401 errors do not schedule redirects or toast loops', async () => {
  const h = apiHarness();
  for (const url of ['/auth/me', '/auth/login', '/auth/register', '/auth/logout']) {
    const error = { response: { status: 401 }, config: { url } };
    await assert.rejects(h.failure(error), received => received === error);
  }
  assert.deepEqual(h.timers, []);
  assert.deepEqual(h.removed, []);
});

test('non-auth endpoint 401 retains existing expiry cleanup', async () => {
  const h = apiHarness();
  const error = { response: { status: 401 }, config: { url: '/MatchRooms' } };
  await assert.rejects(h.failure(error));
  assert.deepEqual(h.removed, ['user', 'bobgourmet_current_room']);
  assert.equal(h.timers.length, 1);
});

test('Google button navigates only when enabled', () => {
  const navigations = [];
  const { GoogleOAuthButton } = load('components/auth/GoogleOAuthButton.tsx', {
    '../../services/authNavigation': navigation,
  }, { window: { location: { assign: value => navigations.push(value) } } });
  const disabled = GoogleOAuthButton({ disabled: true });
  disabled.props.onClick();
  assert.deepEqual(navigations, []);
  const enabled = GoogleOAuthButton({});
  assert.equal(enabled.props.type, 'button');
  assert.equal(enabled.props.disabled, false);
  enabled.props.onClick();
  assert.deepEqual(navigations, ['/oauth2/authorization/google']);
});

function renderCallback(state, search = '') {
  const { OAuthCallback } = load('components/auth/OAuthCallback.tsx', {
    '../../contexts/AuthContext': { useAuth: () => ({ refreshUser() {}, ...state }) },
    '../../services/authNavigation': navigation,
    'react-router-dom': {
      Link: ({ children, to }) => React.createElement('a', { href: to }, children),
      Navigate: ({ to, replace }) => React.createElement('div', { 'data-destination': to, 'data-replace': replace }),
    },
    '../common/LoadingSpinner': { LoadingSpinner: () => React.createElement('div', null, 'Loading') },
  }, { window: { location: { search } } });
  return renderToStaticMarkup(React.createElement(OAuthCallback));
}

test('callback view redirects only after server identity has been confirmed', () => {
  assert.match(renderCallback({ user: null, isLoading: true }), /Loading/);
  assert.match(renderCallback({ user: identity, isLoading: false }), /data-destination="\/"/);
  assert.doesNotMatch(renderCallback({ user: null, isLoading: false }), /data-destination/);
});

test('provider errors and legacy tokens cannot become callback success even with an old session', () => {
  const state = { user: identity, isLoading: false };
  assert.doesNotMatch(renderCallback(state, '?error=oauth_failed'), /data-destination/);
  assert.doesNotMatch(renderCallback(state, '?token=untrusted'), /data-destination/);
});

test('callback network failure offers retry instead of silently declaring login success', () => {
  const html = renderCallback({ user: null, isLoading: false, authError: 'Server unavailable' });
  assert.match(html, /Server unavailable/);
  assert.match(html, /로그인 상태 다시 확인/);
  assert.doesNotMatch(html, /data-destination/);
});
