import {useEffect, useState} from 'react'
import {AppState, I18nManager, View} from 'react-native'
import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated'
import {useLingui} from '@lingui/react/macro'

import {useA11y} from '#/state/a11y'
import {useAutoplayDisabled} from '#/state/preferences'
import {atoms as a} from '#/alf'
import {ButtonText} from '#/components/Button'

/** Network names are brands, kept in their original spelling. */
const NETWORKS = ['Atmosphere', 'Bluesky', 'Blacksky', 'Eurosky'] as const
const WORD_POSITIONS = NETWORKS.map((_, index) => index).concat(NETWORKS.length)
const HOLD_DURATION = 2200
const ROLL_DURATION = 380
const NETWORK_SLOT = '\uFFFC'

export function RollingLoginLabel() {
  const {t: l} = useLingui()
  const {reduceMotionEnabled, screenReaderEnabled} = useA11y()
  const autoplayDisabled = useAutoplayDisabled()
  const [foreground, setForeground] = useState(
    AppState.currentState === 'active',
  )
  const [rowHeight, setRowHeight] = useState(0)
  const [wordWidths, setWordWidths] = useState<number[]>(() =>
    NETWORKS.map(() => 0),
  )
  const position = useSharedValue(0)
  const direction = I18nManager.isRTL ? -1 : 1
  const animate =
    foreground &&
    !reduceMotionEnabled &&
    !screenReaderEnabled &&
    !autoplayDisabled

  const network = NETWORK_SLOT
  const [before, after] = l`Login with ${network}`.split(NETWORK_SLOT)

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      setForeground(state === 'active')
    })
    return () => subscription.remove()
  }, [])

  useEffect(() => {
    position.set(0)
    if (!animate || !rowHeight) return

    const config = {
      duration: ROLL_DURATION,
      easing: Easing.out(Easing.exp),
    }
    position.set(
      withRepeat(
        withSequence(
          ...NETWORKS.map((_, index) =>
            withDelay(HOLD_DURATION, withTiming(index + 1, config)),
          ),
          // The duplicated Atmosphere row makes this reset invisible.
          withTiming(0, {duration: 0}),
        ),
        -1,
      ),
    )
    return () => cancelAnimation(position)
  }, [animate, rowHeight, position])

  const rollingStyle = useAnimatedStyle(() => ({
    transform: [{translateY: -position.get() * rowHeight}],
  }))
  // React Compiler can hoist array callbacks, so keep them off the UI thread.
  const widthsReady = wordWidths.every(width => width > 0)
  const loopWidths = [...wordWidths, wordWidths[0]]

  /** Balance the reserved word space around the visible, shorter phrase. */
  const spareWidth = useDerivedValue(() => {
    if (!widthsReady) return 0
    return (
      loopWidths[0] - interpolate(position.get(), WORD_POSITIONS, loopWidths)
    )
  })
  const centeringStyle = useAnimatedStyle(() => ({
    transform: [{translateX: (direction * spareWidth.get()) / 2}],
  }))
  const suffixStyle = useAnimatedStyle(() => ({
    transform: [{translateX: -direction * spareWidth.get()}],
  }))

  return (
    <Animated.View
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[a.flex_row, a.align_center, a.flex_shrink, centeringStyle]}>
      {!!before && <ButtonText style={[a.flex_shrink]}>{before}</ButtonText>}
      <View
        style={[a.overflow_hidden]}
        onLayout={event => setRowHeight(event.nativeEvent.layout.height)}>
        {/* Reserve the longest word's natural size, including font scaling. */}
        <ButtonText numberOfLines={1} style={{opacity: 0}}>
          {NETWORKS[0]}
        </ButtonText>
        <Animated.View
          style={[a.absolute, a.top_0, a.left_0, a.right_0, rollingStyle]}>
          {[...NETWORKS, NETWORKS[0]].map((word, index) => (
            <View
              key={index}
              style={rowHeight ? {height: rowHeight} : undefined}>
              <ButtonText
                numberOfLines={1}
                style={{textAlign: I18nManager.isRTL ? 'right' : 'left'}}
                onTextLayout={event => {
                  const width = event.nativeEvent.lines[0]?.width
                  const wordIndex = index % NETWORKS.length
                  if (!width) return
                  setWordWidths(prev =>
                    prev[wordIndex] === width
                      ? prev
                      : prev.map((value, i) =>
                          i === wordIndex ? width : value,
                        ),
                  )
                }}>
                {word}
              </ButtonText>
            </View>
          ))}
        </Animated.View>
      </View>
      {!!after && (
        <Animated.View style={[a.flex_shrink, suffixStyle]}>
          <ButtonText>{after}</ButtonText>
        </Animated.View>
      )}
    </Animated.View>
  )
}
