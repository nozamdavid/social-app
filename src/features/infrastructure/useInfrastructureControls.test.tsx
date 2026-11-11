import {createElement, Fragment} from 'react'
import {act, renderHook} from '@testing-library/react-native'

import {DEFAULT_INFRASTRUCTURE} from '#/features/infrastructure/config'
import {
  getInfrastructureSettings,
  InfrastructureProvider,
  useInfrastructureSettings,
} from '#/features/infrastructure/preferences'
import {useInfrastructureControls} from '#/features/infrastructure/useInfrastructureControls'

jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))
let mockRaw: string | undefined
let mockMutations = 0
const mockListeners = new Set<() => void>()
jest.mock('#/storage', () => ({
  device: {
    getRaw: () => mockRaw,
    set: (_keys: string[], value: unknown) => {
      mockRaw = JSON.stringify({data: value})
      mockListeners.forEach(listener => listener())
    },
    addOnValueChangedListener: (_keys: string[], listener: () => void) => {
      mockListeners.add(listener)
      return {remove: () => mockListeners.delete(listener)}
    },
  },
}))
jest.mock('@tanstack/react-query', () => ({
  useIsMutating: () => mockMutations,
  useQueryClient: () => ({isMutating: () => mockMutations}),
}))

beforeEach(() => {
  mockRaw = undefined
  mockMutations = 0
  mockListeners.clear()
  getInfrastructureSettings()
})

function AppviewCacheBoundary({children}: {children: React.ReactNode}) {
  const {appviewHostname} = useInfrastructureSettings()
  return createElement(Fragment, {key: appviewHostname}, children)
}

function setup({
  remount = false,
  accountDid = 'did:plc:alice',
  screenKey = 'active-settings',
} = {}) {
  const beforeAppviewChange = jest.fn()
  const hook = renderHook(
    () =>
      useInfrastructureControls({beforeAppviewChange, accountDid, screenKey}),
    {
      wrapper: ({children}) =>
        createElement(
          InfrastructureProvider,
          null,
          remount
            ? createElement(AppviewCacheBoundary, null, children)
            : children,
        ),
    },
  )
  return {...hook, beforeAppviewChange}
}

it('immediately saves redirects and presets without committing custom drafts', () => {
  const {result, beforeAppviewChange} = setup()
  act(() => result.current.chooseAppview('custom'))
  act(() => result.current.setCustomAppview('api.draft.example'))
  act(() => result.current.chooseImageCdn('custom'))
  act(() => result.current.setImageCdn('cdn.draft.example'))
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  act(() => result.current.setUseLinkRedirects(false))
  act(() => result.current.chooseImageCdn('luna.danielmorrisey.com'))
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    useLinkRedirects: false,
    imageCdnHostname: 'luna.danielmorrisey.com',
  })
  expect(result.current.customAppview).toBe('api.draft.example')
  expect(beforeAppviewChange).not.toHaveBeenCalled()
  act(() => result.current.chooseAppview('api.eurosky.network'))
  expect(getInfrastructureSettings().appviewHostname).toBe(
    'api.eurosky.network',
  )
  expect(beforeAppviewChange).toHaveBeenCalledTimes(1)
  act(() => result.current.chooseAppview('api.eurosky.network'))
  expect(beforeAppviewChange).toHaveBeenCalledTimes(1)
})

it('applies each custom hostname independently and normalizes it', () => {
  const {result, beforeAppviewChange} = setup()
  act(() => result.current.chooseAppview('custom'))
  act(() => result.current.setCustomAppview('https://api.custom.example/'))
  act(() => result.current.chooseImageCdn('custom'))
  act(() => result.current.setImageCdn('https://cdn.custom.example/'))
  act(() => expect(result.current.applyImageCdn()).toBe(true))
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    imageCdnHostname: 'cdn.custom.example',
  })
  expect(result.current.cdnDirty).toBe(false)
  expect(beforeAppviewChange).not.toHaveBeenCalled()
  act(() => result.current.setImageCdn('cdn.unsaved.example'))
  act(() => expect(result.current.applyAppview()).toBe(true))
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    appviewHostname: 'api.custom.example',
    imageCdnHostname: 'cdn.custom.example',
  })
  expect(result.current.appviewDirty).toBe(false)
  expect(result.current.imageCdn).toBe('cdn.unsaved.example')
  expect(beforeAppviewChange).toHaveBeenCalledTimes(1)
})

it.each(['', 'http://api.example.com', 'https://api.example.com/path'])(
  'rejects invalid custom inputs (%s) without changing saved values',
  value => {
    const {result} = setup()
    act(() => result.current.setCustomAppview(value))
    act(() => result.current.setImageCdn(value))
    act(() => expect(result.current.applyAppview()).toBe(false))
    act(() => expect(result.current.applyImageCdn()).toBe(false))
    expect(result.current.appviewInvalid).toBe(true)
    expect(result.current.cdnInvalid).toBe(true)
    expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
    act(() => result.current.setImageCdn('cdn.fixed.example'))
    expect(result.current.cdnInvalid).toBe(false)
  },
)

it('does not allow an AppView switch during a mutation, including one begun after rendering', () => {
  const {result, rerender, beforeAppviewChange} = setup()
  act(() => result.current.setCustomAppview('api.custom.example'))
  mockMutations = 1
  act(() => result.current.chooseAppview('api.eurosky.network'))
  act(() => expect(result.current.applyAppview()).toBe(false))
  expect(getInfrastructureSettings().appviewHostname).toBe('')
  expect(beforeAppviewChange).not.toHaveBeenCalled()
  rerender({})
  expect(result.current.appviewBusy).toBe(true)
  act(() => result.current.setUseLinkRedirects(false))
  act(() => result.current.chooseImageCdn('luna.danielmorrisey.com'))
  expect(getInfrastructureSettings().useLinkRedirects).toBe(false)
  expect(getInfrastructureSettings().imageCdnHostname).toBe(
    'luna.danielmorrisey.com',
  )
  mockMutations = 0
  act(() => {
    result.current.applyAppview()
  })
  expect(getInfrastructureSettings().appviewHostname).toBe('api.custom.example')
})

it('restores defaults immediately, guarding AppView changes during mutations', () => {
  const {result, rerender, beforeAppviewChange} = setup()
  act(() => result.current.chooseAppview('api.eurosky.network'))
  act(() => result.current.chooseImageCdn('luna.danielmorrisey.com'))
  act(() => result.current.setUseLinkRedirects(false))
  mockMutations = 1
  rerender({})
  expect(result.current.resetBlocked).toBe(true)
  act(() => result.current.resetDefaults())
  expect(getInfrastructureSettings().appviewHostname).toBe(
    'api.eurosky.network',
  )
  mockMutations = 0
  act(() => result.current.resetDefaults())
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  expect(result.current.appviewChoice).toBe('')
  expect(result.current.imageCdnChoice).toBe('')
  expect(beforeAppviewChange).toHaveBeenCalledTimes(2)
})

it('discards uncommitted custom inputs when leaving the screen', () => {
  const hook = setup()
  act(() => hook.result.current.chooseAppview('custom'))
  act(() => hook.result.current.setCustomAppview('api.unsaved.example'))
  act(() => hook.result.current.chooseImageCdn('custom'))
  act(() => hook.result.current.setImageCdn('cdn.unsaved.example'))
  hook.unmount()
  const {result} = setup()
  expect(result.current.appviewChoice).toBe('')
  expect(result.current.imageCdnChoice).toBe('')
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
})

it('retains the unsaved CDN draft through the AppView cache boundary remount', () => {
  const {result, beforeAppviewChange} = setup({remount: true})
  act(() => result.current.chooseImageCdn('custom'))
  act(() => result.current.setImageCdn('https://cdn.unsaved.example/'))
  act(() => result.current.chooseAppview('api.eurosky.network'))
  expect(beforeAppviewChange).toHaveBeenCalledTimes(1)
  expect(result.current.settings.appviewHostname).toBe('api.eurosky.network')
  expect(result.current.imageCdnChoice).toBe('custom')
  expect(result.current.imageCdn).toBe('https://cdn.unsaved.example/')
  expect(getInfrastructureSettings().imageCdnHostname).toBe('')
  act(() => result.current.chooseAppview('custom'))
  act(() => result.current.setCustomAppview('api.custom.example'))
  act(() => {
    result.current.applyAppview()
  })
  expect(result.current.customAppview).toBe('api.custom.example')
  expect(result.current.imageCdn).toBe('https://cdn.unsaved.example/')
  expect(getInfrastructureSettings().imageCdnHostname).toBe('')
})

it('never carries the transient CDN draft into a different account', () => {
  const hook = setup()
  act(() => hook.result.current.chooseImageCdn('custom'))
  act(() => hook.result.current.setImageCdn('cdn.alice.example'))
  act(() => hook.result.current.chooseAppview('api.eurosky.network'))
  hook.unmount()
  const {result} = setup({accountDid: 'did:plc:bob'})
  expect(result.current.imageCdnChoice).toBe('')
  expect(result.current.imageCdn).toBe('')
})

it('does not let another restored settings screen consume the active screen draft', () => {
  const original = setup()
  act(() => original.result.current.chooseImageCdn('custom'))
  act(() => original.result.current.setImageCdn('cdn.active-draft.example'))
  act(() => original.result.current.chooseAppview('api.eurosky.network'))
  original.unmount()
  const earlierScreen = setup({screenKey: 'earlier-settings'})
  expect(earlierScreen.result.current.imageCdnChoice).toBe('')
  const activeScreen = setup()
  expect(activeScreen.result.current.imageCdnChoice).toBe('custom')
  expect(activeScreen.result.current.imageCdn).toBe('cdn.active-draft.example')
  expect(getInfrastructureSettings().imageCdnHostname).toBe('')
  activeScreen.unmount()
  const reopened = setup()
  expect(reopened.result.current.imageCdnChoice).toBe('')
})

it('saves PLC presets immediately without applying other custom drafts', () => {
  const {result} = setup()
  act(() => result.current.setCustomAppview('api.unsaved.example'))
  act(() => result.current.setImageCdn('cdn.unsaved.example'))
  act(() => result.current.choosePlcDirectory('plc.eurosky.network'))
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    plcDirectoryHostname: 'plc.eurosky.network',
  })
  act(() => result.current.choosePlcDirectory('plc.wafflehouse.dev'))
  expect(getInfrastructureSettings().plcDirectoryHostname).toBe(
    'plc.wafflehouse.dev',
  )
  act(() => result.current.choosePlcDirectory(''))
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
})

it('applies and validates only the custom PLC field', () => {
  const {result} = setup()
  act(() => result.current.choosePlcDirectory('custom'))
  act(() => result.current.setPlcDirectory('http://invalid.example'))
  act(() => expect(result.current.applyPlcDirectory()).toBe(false))
  expect(result.current.plcDirectoryInvalid).toBe(true)
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  act(() => result.current.setCustomAppview('api.unsaved.example'))
  act(() => result.current.setImageCdn('cdn.unsaved.example'))
  act(() => result.current.setPlcDirectory('https://plc.custom.example/'))
  expect(result.current.plcDirectoryInvalid).toBe(false)
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  act(() => expect(result.current.applyPlcDirectory()).toBe(true))
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    plcDirectoryHostname: 'plc.custom.example',
  })
  expect(result.current.plcDirectoryDirty).toBe(false)
  act(() => result.current.resetDefaults())
  expect(result.current.plcDirectoryChoice).toBe('')
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
})

it('retains both PLC and CDN drafts across the AppView remount without saving either', () => {
  const {result} = setup({remount: true})
  act(() => result.current.choosePlcDirectory('custom'))
  act(() => result.current.setPlcDirectory('plc.unsaved.example'))
  act(() => result.current.chooseImageCdn('custom'))
  act(() => result.current.setImageCdn('cdn.unsaved.example'))
  act(() => result.current.chooseAppview('api.eurosky.network'))
  expect(result.current.plcDirectoryChoice).toBe('custom')
  expect(result.current.plcDirectory).toBe('plc.unsaved.example')
  expect(result.current.imageCdn).toBe('cdn.unsaved.example')
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    appviewHostname: 'api.eurosky.network',
  })
})
