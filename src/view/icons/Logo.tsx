import {forwardRef} from 'react'
import {type TextProps} from 'react-native'
import Svg, {
  Defs,
  LinearGradient,
  Path,
  type PathProps,
  Stop,
  type SvgProps,
} from 'react-native-svg'

import {APP_NAME, CAELUM_BRAND} from '#/lib/app-brand'
import {useLogoVariant} from '#/view/icons/useLogoVariant'
import {flatten, useTheme} from '#/alf'
import {Image} from '#/components/Image'
import artwork from '#/../assets/branding/caelum/generated/artwork.json'
import {IS_BLUESKY_PLUS} from '#/env'

const ratio = 57 / 64

type Props = {
  allowVariants?: boolean
  fill?: PathProps['fill']
  style?: TextProps['style']
} & Omit<SvgProps, 'style'>

export const Logo = forwardRef(function LogoImpl(props: Props, ref) {
  const t = useTheme()
  const {allowVariants = true, fill, ...rest} = props
  const gradient = fill === 'sky'
  const styles = flatten(props.style)
  const _fill = gradient
    ? 'url(#sky)'
    : fill || styles?.color || t.palette.primary_500
  // @ts-expect-error it's fiiiiine
  const size = parseInt(rest.width || 32, 10)

  const logoVariant = useLogoVariant(allowVariants)

  if (IS_BLUESKY_PLUS) {
    return (
      <Svg
        // @ts-expect-error it's fiiiiine
        ref={ref}
        accessibilityLabel={APP_NAME}
        accessibilityHint=""
        {...rest}
        viewBox={artwork.mark.viewBox}
        style={[
          {
            width: size,
            height: (size * artwork.mark.height) / artwork.mark.width,
          },
          styles,
        ]}>
        {artwork.mark.paths.map((d, i) => (
          <Path
            key={i}
            d={d}
            fill={gradient ? CAELUM_BRAND.colors.primary : _fill}
          />
        ))}
      </Svg>
    )
  }

  if (logoVariant === 'kawaii') {
    return (
      <Image
        source={
          size > 100
            ? require('../../../assets/kawaii.png')
            : require('../../../assets/kawaii_smol.png')
        }
        accessibilityLabel={APP_NAME}
        accessibilityHint=""
        accessibilityIgnoresInvertColors
        style={[{height: size, aspectRatio: 1.4}]}
      />
    )
  }

  return (
    <Svg
      fill="none"
      // @ts-expect-error it's fiiiiine
      ref={ref}
      viewBox="0 0 64 57"
      {...rest}
      style={[{width: size, height: size * ratio}, styles]}>
      {gradient && (
        <Defs>
          <LinearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
            <Stop offset="0" stopColor="#0A7AFF" stopOpacity="1" />
            <Stop offset="1" stopColor="#59B9FF" stopOpacity="1" />
          </LinearGradient>
        </Defs>
      )}

      <Path
        fill={_fill}
        d="M13.873 3.805C21.21 9.332 29.103 20.537 32 26.55v15.882c0-.338-.13.044-.41.867-1.512 4.456-7.418 21.847-20.923 7.944-7.111-7.32-3.819-14.64 9.125-16.85-7.405 1.264-15.73-.825-18.014-9.015C1.12 23.022 0 8.51 0 6.55 0-3.268 8.579-.182 13.873 3.805ZM50.127 3.805C42.79 9.332 34.897 20.537 32 26.55v15.882c0-.338.13.044.41.867 1.512 4.456 7.418 21.847 20.923 7.944 7.111-7.32 3.819-14.64-9.125-16.85 7.405 1.264 15.73-.825 18.014-9.015C62.88 23.022 64 8.51 64 6.55c0-9.818-8.578-6.732-13.873-2.745Z"
      />
    </Svg>
  )
})
