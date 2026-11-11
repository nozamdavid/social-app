import {forwardRef} from 'react'
import {
  Image as ExpoImage,
  ImageBackground as ExpoImageBackground,
  type ImagePrefetchOptions,
  type ImageProps,
} from 'expo-image'

import {rewriteImageSource} from '#/features/infrastructure/imageSources'
import {
  getInfrastructureSettings,
  useInfrastructureSettings,
} from '#/features/infrastructure/preferences'

export * from 'expo-image'
export type Image = ExpoImage

const RoutedImage = forwardRef<ExpoImage, ImageProps>(function RoutedImage(
  {source, placeholder, ...props},
  ref,
) {
  const {imageCdnHostname} = useInfrastructureSettings()
  return (
    <ExpoImage
      {...props}
      ref={ref}
      source={rewriteImageSource(source, imageCdnHostname)}
      placeholder={rewriteImageSource(placeholder, imageCdnHostname)}
    />
  )
})

/** Shared image boundary for live CDN selection, including prefetch and refs. */
export const Image = Object.assign(RoutedImage, {
  prefetch(
    urls: string | string[],
    options?: ImagePrefetchOptions | ImagePrefetchOptions['cachePolicy'],
  ) {
    const rewritten = rewriteImageSource(
      urls,
      getInfrastructureSettings().imageCdnHostname,
    )
    return typeof options === 'string'
      ? ExpoImage.prefetch(rewritten, options)
      : ExpoImage.prefetch(rewritten, options)
  },
  clearMemoryCache: ExpoImage.clearMemoryCache,
  clearDiskCache: ExpoImage.clearDiskCache,
  getCachePathAsync: ExpoImage.getCachePathAsync,
  loadAsync(...[source, options]: Parameters<typeof ExpoImage.loadAsync>) {
    return ExpoImage.loadAsync(
      rewriteImageSource(source, getInfrastructureSettings().imageCdnHostname),
      options,
    )
  },
})

export function ImageBackground({
  source,
  placeholder,
  ...props
}: React.ComponentProps<typeof ExpoImageBackground>) {
  const {imageCdnHostname} = useInfrastructureSettings()
  return (
    <ExpoImageBackground
      {...props}
      source={rewriteImageSource(source, imageCdnHostname)}
      placeholder={rewriteImageSource(placeholder, imageCdnHostname)}
    />
  )
}
