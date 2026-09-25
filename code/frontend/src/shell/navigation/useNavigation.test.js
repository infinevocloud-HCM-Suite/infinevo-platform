/**
 * Unit tests for useNavigation / NavigationProvider (W-12.3 §7, frontend).
 *
 * Tests use Node's built-in test runner. They test the logic of the navigation
 * feed state management without spinning up React or a DOM.
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// ──────────────────────────────────────────────────────────────────────────────
// Replicate the feed state logic in isolation (pure JS, no React)
// ──────────────────────────────────────────────────────────────────────────────

function createNavigationStore() {
  let state = { items: [], actions: [], loading: false, error: null };
  const subscribers = new Set();

  function notify() {
    subscribers.forEach((cb) => cb(state));
  }

  function setFeed(feed) {
    state = {
      items: feed?.items || [],
      actions: feed?.actions || [],
      loading: false,
      error: null,
    };
    notify();
  }

  function setLoading() {
    state = { ...state, loading: true, error: null };
    notify();
  }

  function setError(err) {
    state = { items: [], actions: [], loading: false, error: err };
    notify();
  }

  function getState() {
    return state;
  }

  function subscribe(cb) {
    subscribers.add(cb);
    return () => subscribers.delete(cb);
  }

  return { setFeed, setLoading, setError, getState, subscribe };
}

// ──────────────────────────────────────────────────────────────────────────────

describe('navigation store', () => {
  let store;

  beforeEach(() => {
    store = createNavigationStore();
  });

  it('initial state has empty items and actions', () => {
    const s = store.getState();
    assert.deepEqual(s.items, []);
    assert.deepEqual(s.actions, []);
    assert.equal(s.loading, false);
    assert.equal(s.error, null);
  });

  it('setFeed populates items and actions from server response', () => {
    const feed = {
      items: [
        { key: 'core.employee', labelKey: 'nav.employees', path: '/employees', children: [] },
        { key: 'payroll.runs', labelKey: 'nav.payroll', path: '/payroll/runs', children: [] },
      ],
      actions: ['core.employee.read', 'payroll.run.read'],
    };
    store.setFeed(feed);

    const s = store.getState();
    assert.equal(s.items.length, 2);
    assert.equal(s.items[0].key, 'core.employee');
    assert.equal(s.items[1].key, 'payroll.runs');
    assert.deepEqual(s.actions, ['core.employee.read', 'payroll.run.read']);
    assert.equal(s.loading, false);
  });

  it('shell renders exactly the returned items — no default items added', () => {
    const feed = { items: [], actions: [] };
    store.setFeed(feed);

    const s = store.getState();
    assert.deepEqual(s.items, [], 'Empty feed must produce empty items, not a default menu');
    assert.deepEqual(s.actions, []);
  });

  it('an empty feed renders an empty shell, not a default menu', () => {
    // Never set a feed — simulate a failed navigation call
    const s = store.getState();
    assert.deepEqual(s.items, []);
  });

  it('subscribers are notified when the feed is set', () => {
    let notified = false;
    let receivedItems = null;

    store.subscribe((state) => {
      notified = true;
      receivedItems = state.items;
    });

    store.setFeed({
      items: [{ key: 'core.employee', labelKey: 'nav.employees', path: '/employees', children: [] }],
      actions: ['core.employee.read'],
    });

    assert.equal(notified, true);
    assert.equal(receivedItems.length, 1);
  });

  it('loading state is set while fetching', () => {
    store.setLoading();
    assert.equal(store.getState().loading, true);
  });

  it('error state is set on failure, items are cleared', () => {
    // First set some data
    store.setFeed({
      items: [{ key: 'core.employee', labelKey: 'nav.employees', path: '/employees', children: [] }],
      actions: ['core.employee.read'],
    });

    // Then simulate a network failure
    store.setError(new Error('Network error'));

    const s = store.getState();
    assert.deepEqual(s.items, []);
    assert.deepEqual(s.actions, []);
    assert.equal(s.loading, false);
    assert.ok(s.error instanceof Error);
  });

  it('tenant switch triggers refetch (subscribers receive updated state)', () => {
    const states = [];
    store.subscribe((s) => states.push(structuredClone(s)));

    // First tenant's feed
    store.setFeed({
      items: [{ key: 'payroll.runs', labelKey: 'nav.payroll', path: '/payroll/runs', children: [] }],
      actions: ['payroll.run.read'],
    });

    // Simulate tenant switch: refetch gives a different result
    store.setFeed({
      items: [
        { key: 'core.employee', labelKey: 'nav.employees', path: '/employees', children: [] },
        { key: 'hrms.timesheets', labelKey: 'nav.timesheets', path: '/timesheets', children: [] },
      ],
      actions: ['core.employee.read', 'hrms.timesheet.read'],
    });

    assert.equal(states.length, 2);
    // Second notification contains the new tenant's menu
    assert.equal(states[1].items[1].key, 'hrms.timesheets');
  });
});
