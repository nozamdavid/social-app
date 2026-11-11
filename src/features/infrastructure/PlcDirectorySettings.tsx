import {View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a, useTheme} from '#/alf'
import {Button, ButtonText} from '#/components/Button'
import * as TextField from '#/components/forms/TextField'
import * as Toggle from '#/components/forms/Toggle'
import * as Toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {PLC_DIRECTORY_PRESETS} from '#/features/infrastructure/config'

export function PlcDirectorySettings({
  choice,
  hostname,
  isDirty,
  isInvalid,
  onChoiceChange,
  onHostnameChange,
  onApply,
}: {
  choice: string
  hostname: string
  isDirty: boolean
  isInvalid: boolean
  onChoiceChange: (choice: string) => void
  onHostnameChange: (hostname: string) => void
  onApply: () => boolean
}) {
  const {t: l} = useLingui()
  const t = useTheme()
  return (
    <View style={[a.gap_md]}>
      <Text accessibilityRole="header" style={[a.text_lg, a.font_bold]}>
        <Trans>PLC directory</Trans>
      </Text>
      <Text style={t.atoms.text_contrast_medium}>
        <Trans>
          Choose where the app looks up did:plc identities to find account
          hosting providers when signing in. Existing sessions keep their
          current hosting provider.
        </Trans>
      </Text>
      <Toggle.Group
        type="radio"
        label={l`PLC directory`}
        values={[choice]}
        onChange={values => {
          if (values[0] !== undefined) onChoiceChange(values[0])
        }}
        style={[a.gap_xs]}>
        {[...PLC_DIRECTORY_PRESETS, {name: l`Custom`, hostname: 'custom'}].map(
          preset => (
            <Toggle.Item
              key={preset.hostname}
              name={preset.hostname}
              testID={`infrastructurePlcDirectory-${preset.hostname || 'default'}`}
              label={
                preset.hostname === ''
                  ? l`plc.directory (default)`
                  : preset.name
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
                    ? l`plc.directory (default)`
                    : preset.name}
                </Text>
                {preset.hostname !== 'custom' && preset.hostname !== '' && (
                  <Text style={t.atoms.text_contrast_medium}>
                    {preset.hostname}
                  </Text>
                )}
              </View>
            </Toggle.Item>
          ),
        )}
      </Toggle.Group>
      {choice === 'custom' && (
        <View style={[a.gap_sm]}>
          <TextField.LabelText>
            <Trans>PLC directory hostname</Trans>
          </TextField.LabelText>
          <TextField.Root isInvalid={isInvalid}>
            <TextField.Input
              testID="infrastructurePlcDirectoryHostname"
              label={l`PLC directory hostname`}
              value={hostname}
              onChangeText={onHostnameChange}
              placeholder="plc.example.com"
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
            />
          </TextField.Root>
          <Button
            testID="applyInfrastructurePlcDirectory"
            label={l`Apply custom PLC directory`}
            color="primary"
            size="large"
            disabled={!isDirty}
            onPress={() => {
              if (onApply()) Toast.show(l`PLC directory updated`)
            }}>
            <ButtonText>
              <Trans>Apply PLC directory</Trans>
            </ButtonText>
          </Button>
        </View>
      )}
      {isInvalid && (
        <Text accessibilityRole="alert" style={a.font_semi_bold}>
          <Trans>
            Enter a PLC directory hostname, such as plc.example.com. Paths,
            ports and login details are not supported.
          </Trans>
        </Text>
      )}
    </View>
  )
}
