import {Linking} from 'react-native'
import {ActivityAction, startActivityAsync} from 'expo-intent-launcher'

import {openLinkSettings} from './openSettings'

jest.mock('expo-application', () => ({applicationId: 'am.noz.caelum'}))
jest.mock('expo-intent-launcher', () => ({
  ActivityAction: {
    APP_OPEN_BY_DEFAULT_SETTINGS:
      'android.settings.APP_OPEN_BY_DEFAULT_SETTINGS',
  },
  startActivityAsync: jest.fn(),
}))

beforeEach(() => {
  jest.mocked(startActivityAsync).mockReset()
  jest.spyOn(Linking, 'openSettings').mockResolvedValue()
})

afterEach(() => {
  jest.restoreAllMocks()
})

it('opens link preferences for our installed package', async () => {
  await openLinkSettings()
  expect(startActivityAsync).toHaveBeenCalledWith(
    ActivityAction.APP_OPEN_BY_DEFAULT_SETTINGS,
    {data: 'package:am.noz.caelum'},
  )
  expect(Linking.openSettings).not.toHaveBeenCalled()
})

it('falls back to app details if the link settings activity is unavailable', async () => {
  jest.mocked(startActivityAsync).mockRejectedValue(new Error('No activity'))
  await openLinkSettings()
  expect(Linking.openSettings).toHaveBeenCalledTimes(1)
})

it('reports failure to the caller if neither settings screen can open', async () => {
  jest.mocked(startActivityAsync).mockRejectedValue(new Error('No activity'))
  jest.mocked(Linking.openSettings).mockRejectedValue(new Error('No settings'))
  await expect(openLinkSettings()).rejects.toThrow('No settings')
})
