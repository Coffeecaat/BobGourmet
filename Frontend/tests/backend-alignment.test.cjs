const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const flush = () => new Promise(resolve => setImmediate(resolve));
const plain = value => JSON.parse(JSON.stringify(value));
function load(file, mocks = {}, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8')
    .replaceAll('import.meta.env', '__env');
  const output = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  const exports = {};
  vm.runInNewContext(output, {
    exports, require: name => {
      if (!(name in mocks)) throw new Error('Unexpected dependency: ' + name);
      return mocks[name];
    },
    console, setTimeout, clearTimeout, __env: {},
    window: { location: { origin: 'http://localhost:5173' } }, ...globals,
  });
  return exports;
}

function socketHarness() {
  const clients = [];
  const timers = new Map();
  let timerId = 0;
  class Client {
    constructor(config) { this.config = config; this.subscriptions = []; this.stops = 0; clients.push(this); }
    activate() { this.activated = true; }
    deactivate(options) { this.stops++; this.stopOptions = options; return this.stopPromise || Promise.resolve(); }
    subscribe(destination, callback, headers) {
      const sub = { destination, callback, headers, disposed: 0, unsubscribe() { this.disposed++; } };
      this.subscriptions.push(sub);
      return sub;
    }
  }
  const module = load('services/websocket.ts', {
    '@stomp/stompjs': { Client },
    'sockjs-client': { default: class SockJS {} },
  }, {
    setTimeout: callback => { timers.set(++timerId, callback); return timerId; },
    clearTimeout: id => timers.delete(id),
  });
  return { service: module.webSocketService, clients, timers };
}
async function connected(h) {
  const connection = h.service.connect();
  await flush();
  h.clients.at(-1).config.onConnect();
  await connection;
  return h.clients.at(-1);
}

test('simultaneous CONNECT requests share one client and promise', async () => {
  const h = socketHarness();
  const first = h.service.connect();
  assert.equal(h.service.connect(), first);
  await flush();
  assert.equal(h.clients.length, 1);
  h.clients[0].config.onConnect();
  await first;
  assert.equal(h.service.isConnected(), true);
  assert.equal(h.timers.size, 0);
  assert.equal(h.clients[0].config.reconnectDelay, 0);
  assert.deepEqual(plain(h.clients[0].config.connectHeaders), {});
});

test('socket failure rejects pending CONNECT and allows an explicit retry', async () => {
  const h = socketHarness();
  const failure = assert.rejects(h.service.connect());
  await flush();
  h.clients[0].config.onWebSocketError();
  await failure;
  await connected(h);
  assert.equal(h.clients.length, 2);
});

test('CONNECT timeout rejects and deactivates without automatic retry', async () => {
  const h = socketHarness();
  const failure = assert.rejects(h.service.connect());
  await flush();
  [...h.timers.values()][0]();
  await failure;
  assert.equal(h.clients[0].stops, 1);
  assert.equal(h.service.isConnected(), false);
});

test('manual disconnect cancels pending CONNECT and does not report network loss', async () => {
  const h = socketHarness();
  let losses = 0;
  h.service.onConnectionLost(() => losses++);
  const failure = assert.rejects(h.service.connect());
  await flush();
  await h.service.disconnect();
  h.clients[0].config.onWebSocketClose();
  await failure;
  assert.equal(losses, 0);
  assert.equal(h.clients.length, 1);
  assert.equal(h.clients[0].stopOptions.force, true);
});

test('a new connection waits for previous client deactivation', async () => {
  const h = socketHarness();
  const client = await connected(h);
  let finish;
  client.stopPromise = new Promise(resolve => { finish = resolve; });
  const stopping = h.service.disconnect();
  const next = h.service.connect();
  await flush();
  assert.equal(h.clients.length, 1);
  finish();
  await stopping;
  await flush();
  h.clients[1].config.onConnect();
  await next;
  assert.equal(h.service.isConnected(), true);
});

test('unexpected close reports loss once and does not reconnect', async () => {
  const h = socketHarness();
  const client = await connected(h);
  let losses = 0;
  const off = h.service.onConnectionLost(() => losses++);
  client.config.onWebSocketClose();
  client.config.onWebSocketClose();
  await flush();
  assert.equal(losses, 1);
  assert.equal(h.clients.length, 1);
  assert.equal(h.service.isConnected(), false);
  off();
});

test('subscriptions need no receipt and ignore callbacks after idempotent cleanup', async () => {
  const h = socketHarness();
  const client = await connected(h);
  let messages = 0;
  const dispose = h.service.subscribeToInitialState(() => messages++);
  const sub = client.subscriptions[0];
  assert.equal(sub.headers, undefined);
  sub.callback({ body: JSON.stringify({ type: 'ROOM_STATE_UPDATE', payload: {} }) });
  assert.equal(messages, 1);
  dispose();
  dispose();
  sub.callback({ body: '{}' });
  assert.equal(messages, 1);
  assert.equal(sub.disposed, 1);
});

function sessionHarness(h, on = {}) {
  const { subscribeRoomSession } = load('services/roomSubscription.ts', { './websocket': { webSocketService: h.service } });
  return subscribeRoomSession('A', {
    message() {}, closed() {}, ready() {}, error(error) { throw error; }, ...on,
  });
}

test('room subscriptions send user queue first and snapshot-triggering topic last', async () => {
  const h = socketHarness();
  const client = await connected(h);
  const dispose = sessionHarness(h);
  await flush();
  assert.deepEqual(client.subscriptions.map(s => s.destination), [
    '/user/queue/events', '/topic/room/A/closed', '/topic/room/A/menuStatus', '/topic/room/A/events',
  ]);
  dispose();
  assert.equal(client.subscriptions.every(s => s.disposed === 1), true);
});

test('cleanup during pending CONNECT prevents all late subscriptions', async () => {
  const h = socketHarness();
  let ready = 0;
  const dispose = sessionHarness(h, { ready() { ready++; } });
  dispose();
  await flush();
  h.clients[0].config.onConnect();
  await flush();
  assert.equal(h.clients[0].subscriptions.length, 0);
  assert.equal(ready, 0);
});

test('cancelled setup suppresses connection errors', async () => {
  const h = socketHarness();
  let errors = 0;
  const dispose = sessionHarness(h, { error() { errors++; } });
  dispose();
  await flush();
  h.clients[0].config.onStompError();
  await flush();
  assert.equal(errors, 0);
});

test('initial snapshots require matching roomId, independent of room/menu message order', async () => {
  const h = socketHarness();
  const client = await connected(h);
  const received = [];
  const dispose = sessionHarness(h, { message(message) { received.push(message); } });
  await flush();
  const queue = client.subscriptions.find(s => s.destination === '/user/queue/events');
  for (const roomId of ['B', undefined, 'A']) {
    queue.callback({ body: JSON.stringify({ type: 'MENU_STATUS_UPDATE', roomId, payload: {} }) });
  }
  assert.equal(received.length, 1);
  assert.equal(received[0].roomId, 'A');
  const topic = client.subscriptions.find(s => s.destination === '/topic/room/A/menuStatus');
  topic.callback({ body: JSON.stringify({ type: 'MENU_STATUS_UPDATE', payload: {} }) });
  assert.equal(received.length, 2);
  dispose();
});

test('partial setup failure cleans up already registered subscriptions', async () => {
  const h = socketHarness();
  const client = await connected(h);
  h.service.subscribeToMenuStatus = () => { throw new Error('subscribe failed'); };
  let errors = 0;
  sessionHarness(h, { error() { errors++; } });
  await flush();
  assert.equal(errors, 1);
  assert.equal(client.subscriptions.every(s => s.disposed === 1), true);
});

const stateModule = load('services/roomState.ts');
const room = (id = 'A') => ({
  roomId: id, roomName: 'room', hostUsername: 'host', users: ['host'],
  participants: [{ username: 'host' }], state: 'inputting', private: true,
});
const status = () => ({
  submittedMenusByUsers: { host: ['pizza'] }, userSubmitStatus: { host: true },
  menuVotes: { pizza: { recommenders: [], submitters: ['host'], dislikedBy: [], excluded: true } },
  dislikedAndExcludedMenuKeys: ['pizza'],
});
const state = () => ({ room: room(), menuStatus: null, drawResult: null });

test('foreign room snapshot cannot replace the current room', () => {
  const before = state();
  const after = stateModule.reduceRoomMessage(before, { type: 'ROOM_STATE_UPDATE', payload: room('B') }, 'A', 'host');
  assert.equal(after, before);
});

test('late messages for a previous room do not change current room', () => {
  const before = { ...state(), room: room('B') };
  assert.equal(stateModule.reduceRoomMessage(before, { type: 'MENU_STATUS_UPDATE', payload: status() }, 'A', 'host'), before);
});

test('a room snapshot without current membership clears all room state', () => {
  const after = stateModule.reduceRoomMessage(state(), { type: 'ROOM_STATE_UPDATE', payload: { ...room(), users: [] } }, 'A', 'host');
  assert.deepEqual(plain(after), { room: null, menuStatus: null, drawResult: null });
});

test('successive participant events use latest state and leave clears membership', () => {
  let next = stateModule.reduceRoomMessage(state(), { type: 'PARTICIPANT_UPDATE', payload: [{ username: 'host' }, { username: 'guest' }] }, 'A', 'host');
  assert.deepEqual(plain(next.room.users), ['host', 'guest']);
  next = stateModule.reduceRoomMessage(next, { type: 'PARTICIPANT_UPDATE', payload: [{ username: 'guest' }] }, 'A', 'host');
  assert.equal(next.room, null);
});

test('Jackson boolean properties are normalized without losing other fields', () => {
  assert.equal(stateModule.normalizeRoom(room()).isPrivate, true);
  const normalized = stateModule.normalizeMenuStatus(status());
  assert.equal(normalized.menuVotes.pizza.isExcluded, true);
  assert.deepEqual(plain(normalized.submittedMenusByUsers), { host: ['pizza'] });
});

test('draw_result string is converted for the result UI and cleared on new round', () => {
  let next = stateModule.reduceRoomMessage(state(), { type: 'draw_result', payload: { selectedMenu: 'pizza' } }, 'A', 'host');
  assert.deepEqual(plain(next.drawResult.selectedMenu), ['pizza']);
  next.room.state = 'result_viewing';
  next = stateModule.reduceRoomMessage(next, { type: 'ROOM_STATE_UPDATE', payload: room() }, 'A', 'host');
  assert.equal(next.drawResult, null);
});

test('messages after room closure cannot restore state', () => {
  const before = stateModule.emptyRoomState();
  assert.equal(stateModule.reduceRoomMessage(before, { type: 'ROOM_STATE_UPDATE', payload: room() }, 'A', 'host'), before);
});

function apiHarness() {
  const calls = [];
  let config, failure;
  const api = {
    interceptors: { request: { use() {} }, response: { use(_success, error) { failure = error; } } },
    post(url, body) { calls.push({ url, body }); return Promise.resolve({ data: url.includes('/menus') ? status() : room() }); },
    get(url) { calls.push({ url }); return Promise.resolve({ data: room() }); },
  };
  const module = load('services/api.ts', {
    axios: { default: { create(value) { config = value; return api; } } },
    './roomState': stateModule,
  });
  return { ...module, calls, config, failure };
}

test('HTTP uses cookies and same-origin API; create sends Jackson private field', async () => {
  const h = apiHarness();
  assert.equal(h.config.withCredentials, true);
  assert.equal(h.config.baseURL, '/api');
  const created = await h.roomAPI.createRoom({ roomName: 'private', maxUsers: 4, isPrivate: true, password: 'secret' });
  assert.equal(h.calls[0].body.private, true);
  assert.equal('isPrivate' in h.calls[0].body, false);
  assert.equal(created.isPrivate, true);
});

test('recommend and dislike address a menu key rather than an unsupported vote API', async () => {
  const h = apiHarness();
  const menuKey = '김치 찌개?#';
  const updated = await h.menuAPI.recommendMenu('A', menuKey);
  await h.menuAPI.dislikeMenu('A', menuKey);
  assert.equal(h.calls[0].url, '/MatchRooms/A/menus/' + encodeURIComponent(menuKey) + '/recommend');
  assert.equal(h.calls[1].url, '/MatchRooms/A/menus/' + encodeURIComponent(menuKey) + '/dislike');
  assert.equal(updated.menuVotes.pizza.isExcluded, true);
  assert.equal(h.menuAPI.vote, undefined);
});

test('a forbidden domain action does not attempt localStorage logout or redirect', async () => {
  const h = apiHarness();
  const error = { response: { status: 403 }, config: { url: '/MatchRooms/A/menus/pizza/dislike' } };
  await assert.rejects(h.failure(error), value => value === error);
});
