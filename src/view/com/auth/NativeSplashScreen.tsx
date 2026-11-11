import {View} from 'react-native'
import Animated, {FadeIn, FadeOut} from 'react-native-reanimated'
import {Trans, useLingui} from '@lingui/react/macro'

import {CAELUM_BRAND} from '#/lib/app-brand'
import {useHaptics} from '#/lib/haptics'
import {Logo} from '#/view/icons/Logo'
import {Logotype} from '#/view/icons/Logotype'
import {RollingLoginLabel} from '#/screens/Login/components/RollingLoginLabel'
import {atoms as a, useBreakpoints, useTheme} from '#/alf'
import {BrandLockup} from '#/components/branding/BrandLockup'
import {Button, ButtonText} from '#/components/Button'
import {IS_BLUESKY_PLUS} from '#/env'

export function NativeSplashScreen({
  onPressSignin,
  onPressCreateAccount,
}: {
  onPressSignin: () => void
  onPressCreateAccount: () => void
}) {
  const t = useTheme()
  const {gtPhone} = useBreakpoints()
  const {t: l} = useLingui()
  const haptics = useHaptics()
  const logoFill =
    t.name === 'light'
      ? t.palette.primary_500
      : IS_BLUESKY_PLUS
        ? CAELUM_BRAND.colors.lavender
        : t.palette.white

  return (
    <Animated.View
      entering={FadeIn.duration(90)}
      exiting={FadeOut.duration(90)}
      style={[a.flex_1, a.justify_center, t.atoms.bg]}>
      <View style={[a.absolute, a.w_full, a.align_center, {gap: 6, top: 46}]}>
        {IS_BLUESKY_PLUS ? (
          <BrandLockup
            width={112}
            markFill={logoFill}
            wordmarkFill={t.atoms.text.color}
          />
        ) : (
          <>
            <Logo width={76} fill={logoFill} />
            <Logotype width={91} fill={logoFill} />
          </>
        )}
      </View>

      <View
        testID="signinOrCreateAccount"
        style={[
          a.px_5xl,
          a.gap_md,
          gtPhone && [a.w_full, a.self_center, {maxWidth: 600}],
        ]}>
        <Button
          testID="signInButton"
          onPress={() => {
            onPressSignin()
            haptics.tap()
          }}
          label={l`Login with Atmosphere`}
          accessibilityHint={l`Opens flow to sign in to your existing account`}
          size="large"
          color="primary">
          <RollingLoginLabel />
        </Button>

        <Button
          testID="createAccountButton"
          onPress={() => {
            onPressCreateAccount()
            haptics.tap()
          }}
          label={l`Create account`}
          accessibilityHint={l`Opens flow to create a new Bluesky account`}
          size="large"
          color="secondary">
          <ButtonText>
            <Trans>Create account</Trans>
          </ButtonText>
        </Button>
      </View>
    </Animated.View>
  )
}
