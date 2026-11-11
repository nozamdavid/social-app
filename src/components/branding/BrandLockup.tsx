import Svg, {G, Path} from 'react-native-svg'

import {CAELUM_BRAND} from '#/lib/app-brand'
import artwork from '#/../assets/branding/caelum/generated/artwork.json'

/** The same artwork and proportions as the standalone, generated logo SVG. */
export function BrandLockup({
  width,
  markFill,
  wordmarkFill,
}: {
  width: number
  markFill: string
  wordmarkFill: string
}) {
  const {lockup, mark, wordmark} = artwork
  return (
    <Svg
      accessibilityLabel={CAELUM_BRAND.name}
      accessibilityHint=""
      viewBox={lockup.viewBox}
      width={width}
      height={(width * lockup.height) / lockup.width}>
      <G transform={`translate(${lockup.markX} 0)`} fill={markFill}>
        {mark.paths.map((d, i) => (
          <Path key={i} d={d} />
        ))}
      </G>
      <G
        transform={`translate(${lockup.wordmarkX} ${lockup.wordmarkY})`}
        fill={wordmarkFill}>
        {wordmark.paths.map((d, i) => (
          <Path key={i} d={d} />
        ))}
      </G>
    </Svg>
  )
}
