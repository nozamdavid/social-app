import {ActivityIndicator, Modal, View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a, useTheme} from '#/alf'
import {Text} from '#/components/Typography'

export function ApplyingThemeModal({
  visible,
  onShow,
}: {
  visible: boolean
  onShow: () => void
}) {
  const t = useTheme()
  const {t: l} = useLingui()
  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onShow={onShow}
      onRequestClose={() => {}}
      statusBarTranslucent
      navigationBarTranslucent
      supportedOrientations={[
        'portrait',
        'portrait-upside-down',
        'landscape',
        'landscape-left',
        'landscape-right',
      ]}>
      <View
        style={[
          a.flex_1,
          a.align_center,
          a.justify_center,
          a.p_xl,
          {backgroundColor: 'rgba(0, 0, 0, 0.45)'},
        ]}>
        <View
          accessibilityViewIsModal
          accessibilityRole="progressbar"
          accessibilityLabel={l`Applying theme…`}
          accessibilityHint=""
          accessibilityState={{busy: true}}
          accessibilityLiveRegion="polite"
          testID="applying-theme-modal"
          style={[
            a.w_full,
            a.flex_row,
            a.align_center,
            a.justify_center,
            a.gap_md,
            a.p_xl,
            a.rounded_lg,
            t.atoms.bg,
            {
              maxWidth: 320,
              backgroundColor: t.surfaceRaised ?? t.atoms.bg.backgroundColor,
            },
          ]}>
          <ActivityIndicator color={t.atoms.text.color} />
          <Text style={[a.flex_shrink, a.text_md, a.font_semi_bold]}>
            <Trans>Applying theme…</Trans>
          </Text>
        </View>
      </View>
    </Modal>
  )
}
