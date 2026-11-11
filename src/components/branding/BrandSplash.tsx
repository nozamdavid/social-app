import {useEffect, useState} from 'react'
import {useColorScheme, View} from 'react-native'
import Animated, {FadeOut, useReducedMotion} from 'react-native-reanimated'
import * as SplashScreen from 'expo-splash-screen'

import {CAELUM_BRAND} from '#/lib/app-brand'
import {Logo} from '#/view/icons/Logo'
import {atoms as a} from '#/alf'

/** Match the native launch image before fading into the application. */
export function BrandSplash({
  isReady,
  children,
}: React.PropsWithChildren<{isReady: boolean}>) {
  const dark = useColorScheme() === 'dark'
  const reduceMotion = useReducedMotion()
  const [laidOut, setLaidOut] = useState(false)
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    if (!isReady || !laidOut) return
    let mounted = true
    void SplashScreen.hideAsync()
      .then(() => {
        if (mounted) setVisible(false)
      })
      .catch(() => {
        if (mounted) setVisible(false)
      })
    return () => {
      mounted = false
    }
  }, [isReady, laidOut])

  return (
    <View style={[a.flex_1]}>
      {isReady && children}
      {visible && (
        <Animated.View
          onLayout={() => setLaidOut(true)}
          exiting={reduceMotion ? undefined : FadeOut.duration(200)}
          style={[
            a.absolute,
            a.inset_0,
            a.align_center,
            a.justify_center,
            {
              backgroundColor: dark
                ? CAELUM_BRAND.colors.dark
                : CAELUM_BRAND.colors.primary,
            },
          ]}>
          <Logo
            width={102}
            fill={
              dark
                ? CAELUM_BRAND.colors.lavender
                : CAELUM_BRAND.colors.onPrimary
            }
          />
        </Animated.View>
      )}
    </View>
  )
}
