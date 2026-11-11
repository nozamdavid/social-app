import {type NavigationState} from '@react-navigation/native'

let preserved: {did: string | undefined; state: NavigationState} | undefined

/** Retain the current route through an AppView-triggered query-cache remount. */
export function preserveAppviewNavigationState(
  did: string | undefined,
  state: NavigationState,
) {
  preserved = {did, state}
}

/** Never carry an account's navigation state into another account. */
export function getPreservedAppviewNavigationState(did: string | undefined) {
  return preserved?.did === did ? preserved?.state : undefined
}

/** Clear after navigation is ready, keeping render and Strict Mode reads pure. */
export function clearPreservedAppviewNavigationState() {
  preserved = undefined
}
