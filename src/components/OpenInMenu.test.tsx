'use no memo'

import type * as ReactNative from 'react-native'
import {Linking} from 'react-native'
import type * as LinguiCore from '@lingui/core'
import type * as LinguiReact from '@lingui/react'
import {act, fireEvent, render, screen} from '@testing-library/react-native'

import {type DialogControlProps} from '#/components/Dialog'
import {OpenInMenu} from '#/components/OpenInMenu'
import * as Toast from '#/components/Toast'
import {useAtprotoAppsQuery} from '#/features/atprotoApps/queries'

let mockWeb = false

jest.mock('#/env', () => ({
  get IS_WEB() {
    return mockWeb
  },
}))
jest.mock('@lingui/react', () => {
  const actual = jest.requireActual<typeof LinguiReact>('@lingui/react')
  const {i18n} = jest.requireActual<typeof LinguiCore>('@lingui/core')
  i18n.loadAndActivate({locale: 'en', messages: {}})
  return {
    ...actual,
    Trans: () => null,
    useLingui: () => ({_: i18n._.bind(i18n), i18n}),
  }
})
jest.mock('#/components/icons/Square', () => ({
  SquareArrowTopRight_Stroke2_Corner0_Rounded: () => null,
}))
jest.mock('#/features/atprotoApps/queries', () => ({
  useAtprotoAppsQuery: jest.fn(() => ({isLoading: false, data: undefined})),
}))
jest.mock('#/components/Loader', () => ({Loader: () => null}))
jest.mock('#/components/Image', () => ({Image: () => null}))
jest.mock('#/components/Toast', () => ({show: jest.fn()}))
jest.mock('#/components/Button', () => {
  const {Pressable, Text} =
    jest.requireActual<typeof ReactNative>('react-native')
  return {
    Button: ({
      children,
      label,
      onPress,
      testID,
    }: React.PropsWithChildren<{
      label: string
      onPress: () => void
      testID: string
    }>) => (
      <Pressable
        accessibilityHint=""
        accessibilityLabel={label}
        onPress={onPress}
        testID={testID}>
        {children}
      </Pressable>
    ),
    ButtonText: ({children}: React.PropsWithChildren) => (
      <Text>{children}</Text>
    ),
    ButtonIcon: () => null,
  }
})

jest.mock('#/alf', () => ({atoms: new Proxy({}, {get: () => ({})})}))
jest.mock('#/components/Typography', () => {
  const {Text} = jest.requireActual<typeof ReactNative>('react-native')
  return {Text}
})
let latestDialogCallbacks: {onOpen?: () => void; onClose?: () => void} = {}

jest.mock('#/components/Dialog', () => {
  const {View} = jest.requireActual<typeof ReactNative>('react-native')
  function Outer({
    children,
    onOpen,
    onClose,
  }: React.PropsWithChildren<{onOpen?: () => void; onClose?: () => void}>) {
    latestDialogCallbacks = {onOpen, onClose}
    return <View>{children}</View>
  }
  function Container({children}: React.PropsWithChildren) {
    return <View>{children}</View>
  }
  return {
    Outer,
    ScrollableInner: Container,
    Handle: () => null,
    Close: () => null,
  }
})

const did = 'did:plc:testprofile'
const close = jest.fn<void, [callback?: () => void]>()
const control = {close} as unknown as DialogControlProps

beforeEach(() => {
  mockWeb = false
  latestDialogCallbacks = {}
  jest.clearAllMocks()
  jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined)
})

afterEach(() => jest.restoreAllMocks())

it('opens pds.ls with the viewed DID only after the Android sheet closes', async () => {
  render(<OpenInMenu control={control} target={{type: 'profile', did}} />)
  fireEvent.press(screen.getByTestId('profileOpenIn-pds'))
  expect(close).toHaveBeenCalledTimes(1)
  expect(Linking.openURL).not.toHaveBeenCalled()
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith(`https://pds.ls/at/${did}`)
})

it('shows an error if no browser can open the chosen site', async () => {
  jest.mocked(Linking.openURL).mockRejectedValue(new Error('No activity'))
  render(<OpenInMenu control={control} target={{type: 'profile', did}} />)
  fireEvent.press(screen.getByTestId('profileOpenIn-pds'))
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Toast.show).toHaveBeenCalledWith(
    'Could not open this website. Please try again.',
    {type: 'error'},
  )
})

it('uses the latest profile DID after the viewed profile changes', async () => {
  const {rerender} = render(
    <OpenInMenu control={control} target={{type: 'profile', did}} />,
  )
  const nextDid = 'did:web:profiles.example.com'
  rerender(
    <OpenInMenu control={control} target={{type: 'profile', did: nextDid}} />,
  )
  fireEvent.press(screen.getByTestId('profileOpenIn-pds'))
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith(`https://pds.ls/at/${nextDid}`)
})

it('opens web links during the user gesture before dismissing the dialog', () => {
  mockWeb = true
  render(<OpenInMenu control={control} target={{type: 'profile', did}} />)
  fireEvent.press(screen.getByTestId('profileOpenIn-pds'))
  expect(Linking.openURL).toHaveBeenCalledWith(`https://pds.ls/at/${did}`)
  expect(close).toHaveBeenCalledWith()
})

it('opens the exact post in pds.ls after closing the sheet', async () => {
  render(
    <OpenInMenu
      control={control}
      target={{
        type: 'post',
        uri: 'at://did:plc:postauthor/app.bsky.feed.post/3example',
      }}
    />,
  )
  fireEvent.press(screen.getByTestId('postOpenIn-pds'))
  expect(Linking.openURL).not.toHaveBeenCalled()
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith(
    'https://pds.ls/at/did:plc:postauthor/app.bsky.feed.post/3example',
  )
})

it('lists discovered apps for profiles and opens them', async () => {
  jest.mocked(useAtprotoAppsQuery).mockReturnValue({
    isLoading: false,
    data: [
      {id: 'tangled', name: 'Tangled', url: 'https://tangled.org/a.test'},
      {
        id: 'standard-r1',
        name: 'Blog',
        url: 'https://blog.test',
        icon: {
          uri: 'https://pds.example.com/xrpc/com.atproto.sync.getBlob?did=d&cid=c',
        },
      },
    ],
  } as ReturnType<typeof useAtprotoAppsQuery>)
  render(
    <OpenInMenu
      control={control}
      target={{type: 'profile', did, handle: 'a.test'}}
    />,
  )
  expect(screen.getByTestId('profileOpenIn-standard-r1')).toBeTruthy()
  fireEvent.press(screen.getByTestId('profileOpenIn-tangled'))
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith('https://tangled.org/a.test')
})

it('enables discovery only while the dialog is open for profiles', () => {
  render(
    <OpenInMenu
      control={control}
      target={{type: 'profile', did, handle: 'a.test'}}
    />,
  )
  expect(useAtprotoAppsQuery).toHaveBeenLastCalledWith(
    expect.objectContaining({did, handle: 'a.test', enabled: false}),
  )
  act(() => {
    latestDialogCallbacks.onOpen?.()
  })
  expect(useAtprotoAppsQuery).toHaveBeenLastCalledWith(
    expect.objectContaining({did, handle: 'a.test', enabled: true}),
  )
  act(() => {
    latestDialogCallbacks.onClose?.()
  })
  expect(useAtprotoAppsQuery).toHaveBeenLastCalledWith(
    expect.objectContaining({did, handle: 'a.test', enabled: false}),
  )
})

it('does not run discovery for posts', () => {
  render(
    <OpenInMenu
      control={control}
      target={{type: 'post', uri: 'at://did:plc:x/app.bsky.feed.post/1'}}
    />,
  )
  act(() => {
    latestDialogCallbacks.onOpen?.()
  })
  expect(useAtprotoAppsQuery).toHaveBeenLastCalledWith(
    expect.objectContaining({enabled: false}),
  )
})
