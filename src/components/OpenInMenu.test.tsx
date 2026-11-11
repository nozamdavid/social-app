'use no memo'

import type * as ReactNative from 'react-native'
import {Linking} from 'react-native'
import type * as LinguiCore from '@lingui/core'
import type * as LinguiReact from '@lingui/react'
import {act, fireEvent, render, screen} from '@testing-library/react-native'

import {type DialogControlProps} from '#/components/Dialog'
import {OpenInMenu} from '#/components/OpenInMenu'
import * as Toast from '#/components/Toast'

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
jest.mock('#/components/Dialog', () => {
  const {View} = jest.requireActual<typeof ReactNative>('react-native')
  function Container({children}: React.PropsWithChildren) {
    return <View>{children}</View>
  }
  return {
    Outer: Container,
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
  jest.clearAllMocks()
  jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined)
})

afterEach(() => jest.restoreAllMocks())

it.each([
  ['pds', 'https://pds.ls/at/'],
  ['blacksky', 'https://blacksky.community/profile/'],
  ['witchsky', 'https://witchsky.app/profile/'],
  ['red-dwarf', 'https://reddwarf.app/profile/'],
])(
  'opens %s with the viewed DID only after the Android sheet closes',
  async (id, baseUrl) => {
    render(<OpenInMenu control={control} target={{type: 'profile', did}} />)
    fireEvent.press(screen.getByTestId(`profileOpenIn-${id}`))
    expect(close).toHaveBeenCalledTimes(1)
    expect(Linking.openURL).not.toHaveBeenCalled()
    await act(async () => {
      close.mock.calls[0][0]?.()
      await Promise.resolve()
    })
    expect(Linking.openURL).toHaveBeenCalledWith(`${baseUrl}${did}`)
  },
)

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
  fireEvent.press(screen.getByTestId('profileOpenIn-blacksky'))
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith(
    `https://blacksky.community/profile/${nextDid}`,
  )
})

it('opens web links during the user gesture before dismissing the dialog', () => {
  mockWeb = true
  render(<OpenInMenu control={control} target={{type: 'profile', did}} />)
  fireEvent.press(screen.getByTestId('profileOpenIn-pds'))
  expect(Linking.openURL).toHaveBeenCalledWith(`https://pds.ls/at/${did}`)
  expect(close).toHaveBeenCalledWith()
})

it.each([
  ['pds', 'https://pds.ls/at/did:plc:postauthor/app.bsky.feed.post/3example'],
  [
    'blacksky',
    'https://blacksky.community/profile/did:plc:postauthor/post/3example',
  ],
  ['witchsky', 'https://witchsky.app/profile/did:plc:postauthor/post/3example'],
  [
    'red-dwarf',
    'https://reddwarf.app/profile/did:plc:postauthor/post/3example',
  ],
])('opens the exact post in %s after closing the sheet', async (id, url) => {
  render(
    <OpenInMenu
      control={control}
      target={{
        type: 'post',
        uri: 'at://did:plc:postauthor/app.bsky.feed.post/3example',
      }}
    />,
  )
  fireEvent.press(screen.getByTestId(`postOpenIn-${id}`))
  expect(Linking.openURL).not.toHaveBeenCalled()
  await act(async () => {
    close.mock.calls[0][0]?.()
    await Promise.resolve()
  })
  expect(Linking.openURL).toHaveBeenCalledWith(url)
})
