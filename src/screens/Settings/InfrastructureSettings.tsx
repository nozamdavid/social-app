import {View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'
import {type NativeStackScreenProps} from '@react-navigation/native-stack'

import {type CommonNavigatorParams} from '#/lib/routes/types'
import {useSession} from '#/state/session'
import {atoms as a, useTheme} from '#/alf'
import {Button, ButtonText} from '#/components/Button'
import * as TextField from '#/components/forms/TextField'
import * as Toggle from '#/components/forms/Toggle'
import * as Layout from '#/components/Layout'
import * as Toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {
  APPVIEW_PRESETS,
  IMAGE_CDN_PRESETS,
} from '#/features/infrastructure/config'
import {PlcDirectorySettings} from '#/features/infrastructure/PlcDirectorySettings'
import {useInfrastructureControls} from '#/features/infrastructure/useInfrastructureControls'
import {preserveNavigationForAppviewChange} from '#/Navigation'

export function InfrastructureSettingsScreen({
  route,
}: NativeStackScreenProps<CommonNavigatorParams, 'InfrastructureSettings'>) {
  const t = useTheme()
  const {t: l} = useLingui()
  const {currentAccount} = useSession()
  const controls = useInfrastructureControls({
    screenKey: route.key,
    accountDid: currentAccount?.did,
    beforeAppviewChange: () =>
      preserveNavigationForAppviewChange(currentAccount?.did),
  })
  const {settings, appviewChoice, customAppview, imageCdnChoice, imageCdn} =
    controls

  return (
    <Layout.Screen testID="infrastructureSettingsScreen">
      <Layout.Header.Outer>
        <Layout.Header.BackButton />
        <Layout.Header.Content>
          <Layout.Header.TitleText>
            <Trans>Infrastructure</Trans>
          </Layout.Header.TitleText>
        </Layout.Header.Content>
        <Layout.Header.Slot />
      </Layout.Header.Outer>
      <Layout.Content
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={[a.p_lg, a.gap_2xl]}>
        <Text style={t.atoms.text_contrast_medium}>
          <Trans>Network settings apply to all accounts on this device.</Trans>
        </Text>
        <View style={[a.gap_md]}>
          <Text accessibilityRole="header" style={[a.text_lg, a.font_bold]}>
            <Trans>Links</Trans>
          </Text>
          <Toggle.Item
            name="link-redirects"
            testID="infrastructureLinkRedirects"
            label={l`Use go.bsky.app redirects`}
            value={settings.useLinkRedirects}
            onChange={controls.setUseLinkRedirects}
            style={[
              a.flex_row,
              a.align_center,
              a.gap_md,
              a.py_sm,
              {minHeight: 48},
            ]}>
            <View style={[a.flex_1, a.gap_xs]}>
              <Text style={a.font_semi_bold}>
                <Trans>Use go.bsky.app redirects</Trans>
              </Text>
              <Text style={t.atoms.text_contrast_medium}>
                <Trans>
                  Open post links through Bluesky’s redirect service. Turn off
                  to open destinations directly.
                </Trans>
              </Text>
            </View>
            <Toggle.Platform />
          </Toggle.Item>
        </View>
        <View style={[a.gap_md]}>
          <Text accessibilityRole="header" style={[a.text_lg, a.font_bold]}>
            <Trans>AppView</Trans>
          </Text>
          <Text style={t.atoms.text_contrast_medium}>
            <Trans>
              Choose the service that provides feeds, profiles and search.
              Signed-in requests are sent through your PDS. Changing AppView
              refreshes the app’s feeds.
            </Trans>
          </Text>
          <Toggle.Group
            type="radio"
            label={l`AppView`}
            disabled={controls.appviewBusy}
            values={[appviewChoice]}
            onChange={values => {
              if (values[0] !== undefined) {
                controls.chooseAppview(values[0])
              }
            }}
            style={[a.gap_xs]}>
            {[...APPVIEW_PRESETS, {name: l`Custom`, hostname: 'custom'}].map(
              preset => (
                <Toggle.Item
                  key={preset.hostname}
                  name={preset.hostname}
                  testID={`infrastructureAppview-${preset.hostname || 'default'}`}
                  label={
                    preset.hostname === '' ? l`Bluesky (default)` : preset.name
                  }
                  style={[
                    a.flex_row,
                    a.align_center,
                    a.gap_md,
                    a.py_sm,
                    {minHeight: 48},
                  ]}>
                  <Toggle.Radio />
                  <View style={[a.flex_1, a.gap_2xs]}>
                    <Text style={a.font_semi_bold}>
                      {preset.hostname === ''
                        ? l`Bluesky (default)`
                        : preset.name}
                    </Text>
                    {preset.hostname !== 'custom' && (
                      <Text style={t.atoms.text_contrast_medium}>
                        {preset.hostname || 'api.bsky.app'}
                      </Text>
                    )}
                  </View>
                </Toggle.Item>
              ),
            )}
          </Toggle.Group>
          {appviewChoice === 'custom' && (
            <View style={[a.gap_sm]}>
              <TextField.LabelText>
                <Trans>AppView hostname</Trans>
              </TextField.LabelText>
              <TextField.Root isInvalid={controls.appviewInvalid}>
                <TextField.Input
                  testID="infrastructureAppviewHostname"
                  label={l`AppView hostname`}
                  value={customAppview}
                  onChangeText={value => {
                    controls.setCustomAppview(value)
                  }}
                  placeholder="api.example.com"
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="url"
                />
              </TextField.Root>
              <Button
                testID="applyInfrastructureAppview"
                label={l`Apply custom AppView`}
                color="primary"
                size="large"
                disabled={!controls.appviewDirty || controls.appviewBusy}
                onPress={() => {
                  if (controls.applyAppview()) Toast.show(l`AppView updated`)
                }}>
                <ButtonText>
                  <Trans>Apply AppView</Trans>
                </ButtonText>
              </Button>
            </View>
          )}
          {controls.appviewBusy && (
            <Text style={t.atoms.text_contrast_medium}>
              <Trans>
                Wait for current changes to finish before switching AppView.
              </Trans>
            </Text>
          )}
          {controls.appviewInvalid && (
            <Text accessibilityRole="alert" style={a.font_semi_bold}>
              <Trans>
                Enter an AppView hostname, such as api.example.com. Paths, ports
                and login details are not supported.
              </Trans>
            </Text>
          )}
        </View>
        <View style={[a.gap_md]}>
          <Text accessibilityRole="header" style={[a.text_lg, a.font_bold]}>
            <Trans>Image CDN</Trans>
          </Text>
          <Text style={t.atoms.text_contrast_medium}>
            <Trans>
              Choose where post images, avatars and banners load from. A custom
              CDN must support Bluesky’s image URL paths.
            </Trans>
          </Text>
          <Toggle.Group
            type="radio"
            label={l`Image CDN`}
            values={[imageCdnChoice]}
            onChange={values => {
              if (values[0] !== undefined) {
                controls.chooseImageCdn(values[0])
              }
            }}
            style={[a.gap_xs]}>
            {[...IMAGE_CDN_PRESETS, {name: l`Custom`, hostname: 'custom'}].map(
              preset => (
                <Toggle.Item
                  key={preset.hostname}
                  name={preset.hostname}
                  testID={`infrastructureImageCdn-${preset.hostname || 'default'}`}
                  label={
                    preset.hostname === '' ? l`Bluesky (default)` : preset.name
                  }
                  style={[
                    a.flex_row,
                    a.align_center,
                    a.gap_md,
                    a.py_sm,
                    {minHeight: 48},
                  ]}>
                  <Toggle.Radio />
                  <View style={[a.flex_1, a.gap_2xs]}>
                    <Text style={a.font_semi_bold}>
                      {preset.hostname === ''
                        ? l`Bluesky (default)`
                        : preset.name}
                    </Text>
                    {preset.hostname !== 'custom' && (
                      <Text style={t.atoms.text_contrast_medium}>
                        {preset.hostname || 'cdn.bsky.app'}
                      </Text>
                    )}
                  </View>
                </Toggle.Item>
              ),
            )}
          </Toggle.Group>
          {imageCdnChoice === 'custom' && (
            <View style={[a.gap_sm]}>
              <TextField.LabelText>
                <Trans>Image CDN hostname</Trans>
              </TextField.LabelText>
              <TextField.Root isInvalid={controls.cdnInvalid}>
                <TextField.Input
                  testID="infrastructureImageCdnHostname"
                  label={l`Image CDN hostname`}
                  value={imageCdn}
                  onChangeText={value => {
                    controls.setImageCdn(value)
                  }}
                  placeholder="cdn.example.com"
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="url"
                />
              </TextField.Root>
              <Button
                testID="applyInfrastructureImageCdn"
                label={l`Apply custom image CDN`}
                color="primary"
                size="large"
                disabled={!controls.cdnDirty}
                onPress={() => {
                  if (controls.applyImageCdn()) Toast.show(l`Image CDN updated`)
                }}>
                <ButtonText>
                  <Trans>Apply image CDN</Trans>
                </ButtonText>
              </Button>
            </View>
          )}
          {controls.cdnInvalid && (
            <Text accessibilityRole="alert" style={a.font_semi_bold}>
              <Trans>
                Enter a CDN hostname, such as cdn.example.com. Paths, ports and
                login details are not supported.
              </Trans>
            </Text>
          )}
        </View>
        <PlcDirectorySettings
          choice={controls.plcDirectoryChoice}
          hostname={controls.plcDirectory}
          isDirty={controls.plcDirectoryDirty}
          isInvalid={controls.plcDirectoryInvalid}
          onChoiceChange={controls.choosePlcDirectory}
          onHostnameChange={controls.setPlcDirectory}
          onApply={controls.applyPlcDirectory}
        />
        <Button
          label={l`Use Bluesky defaults`}
          color="secondary"
          size="large"
          disabled={controls.resetBlocked}
          onPress={controls.resetDefaults}>
          <ButtonText>
            <Trans>Use Bluesky defaults</Trans>
          </ButtonText>
        </Button>
      </Layout.Content>
    </Layout.Screen>
  )
}
