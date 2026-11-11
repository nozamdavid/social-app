import {type NavigationState} from '@react-navigation/native'

import {
  clearPreservedAppviewNavigationState,
  getPreservedAppviewNavigationState,
  preserveAppviewNavigationState,
} from '#/features/infrastructure/navigationState'

const state: NavigationState = {
  stale: false,
  type: 'stack',
  key: 'root',
  index: 1,
  routeNames: ['Home', 'InfrastructureSettings'],
  routes: [
    {key: 'home', name: 'Home'},
    {key: 'settings', name: 'InfrastructureSettings'},
  ],
}

beforeEach(clearPreservedAppviewNavigationState)

it('retains navigation for repeated render reads by the same account only', () => {
  preserveAppviewNavigationState('did:plc:alice', state)
  expect(getPreservedAppviewNavigationState('did:plc:bob')).toBeUndefined()
  expect(getPreservedAppviewNavigationState(undefined)).toBeUndefined()
  expect(getPreservedAppviewNavigationState('did:plc:alice')).toBe(state)
  expect(getPreservedAppviewNavigationState('did:plc:alice')).toBe(state)
  clearPreservedAppviewNavigationState()
  expect(getPreservedAppviewNavigationState('did:plc:alice')).toBeUndefined()
})

it('replaces the previous snapshot for the next cache remount', () => {
  preserveAppviewNavigationState('did:plc:alice', state)
  const next = {...state, index: 0}
  preserveAppviewNavigationState('did:plc:alice', next)
  expect(getPreservedAppviewNavigationState('did:plc:alice')).toBe(next)
})
