import {createRef} from 'react'
import {Image as ExpoImage, type ImageProps} from 'expo-image'
import {render, screen} from '@testing-library/react-native'

import {Image, ImageBackground} from '#/components/Image'
import {DEFAULT_INFRASTRUCTURE} from './config'

let mockSettings = {...DEFAULT_INFRASTRUCTURE}
jest.mock('#/features/infrastructure/preferences', () => ({
  getInfrastructureSettings: () => mockSettings,
  useInfrastructureSettings: () => mockSettings,
}))
jest.mock('expo-image', () => {
  const React = jest.requireActual('react') as typeof import('react')
  class MockImage extends React.Component {
    static prefetch = jest.fn(() => Promise.resolve(true))
    static loadAsync = jest.fn(() => Promise.resolve(null))
    render() {
      return React.createElement('Image', this.props)
    }
  }
  return {
    Image: MockImage,
    ImageBackground: (props: Record<string, unknown>) =>
      React.createElement('ImageBackground', props),
  }
})

beforeEach(() => {
  mockSettings = {...DEFAULT_INFRASTRUCTURE}
  jest.clearAllMocks()
})

const uri = 'https://cdn.bsky.app/img/feed_thumbnail/plain/did/cid@jpeg'

it('keeps image refs and callbacks while updating live sources and placeholders', () => {
  const ref = createRef<ExpoImage>()
  const onLoad = jest.fn()
  const source = {uri, width: 100, height: 100}
  const {rerender} = render(
    <Image
      testID="photo"
      ref={ref}
      source={source}
      placeholder={{uri}}
      onLoad={onLoad}
    />,
  )
  expect(ref.current).toBeInstanceOf(ExpoImage)
  expect((screen.getByTestId('photo').props as ImageProps).source).toBe(source)
  mockSettings.imageCdnHostname = 'cdn.example.com'
  rerender(
    <Image
      testID="photo"
      ref={ref}
      source={source}
      placeholder={{uri}}
      onLoad={onLoad}
    />,
  )
  const props = screen.getByTestId('photo').props as ImageProps
  expect(props.source).toEqual({
    ...source,
    uri: uri.replace('cdn.bsky.app', 'cdn.example.com'),
  })
  expect(props.placeholder).toEqual({
    uri: uri.replace('cdn.bsky.app', 'cdn.example.com'),
  })
  expect(props.onLoad).toBe(onLoad)
  expect(source.uri).toBe(uri)
})

it('uses the current CDN for prefetch and decoded-image loading', async () => {
  mockSettings.imageCdnHostname = 'cdn.example.com'
  const target = uri.replace('cdn.bsky.app', 'cdn.example.com')
  await Image.prefetch([uri, 'file:///local.jpg'], 'memory')
  expect(ExpoImage.prefetch).toHaveBeenCalledWith(
    [target, 'file:///local.jpg'],
    'memory',
  )
  await Image.loadAsync({uri})
  expect(ExpoImage.loadAsync).toHaveBeenCalledWith({uri: target}, undefined)
  mockSettings.imageCdnHostname = ''
  await Image.prefetch(uri)
  expect(ExpoImage.prefetch).toHaveBeenLastCalledWith(uri, undefined)
})

it('also routes image backgrounds without changing layout props', () => {
  mockSettings.imageCdnHostname = 'cdn.example.com'
  render(
    <ImageBackground
      testID="background"
      source={{uri}}
      style={{height: 100}}
    />,
  )
  const props = screen.getByTestId('background').props as React.ComponentProps<
    typeof ImageBackground
  >
  expect(props.source).toEqual({
    uri: uri.replace('cdn.bsky.app', 'cdn.example.com'),
  })
  expect(props.style).toEqual({height: 100})
})
