import {execFileSync} from 'node:child_process'
import {resolve} from 'node:path'

type AppConfig = {
  expo: {
    name: string
    scheme: string
    icon: string
    owner?: string
    ios: {icon: string; infoPlist: {CFBundleSpokenName: string}}
    android: {
      package: string
      googleServicesFile?: string
      intentFilters: unknown[]
      icon: string
      adaptiveIcon: {foregroundImage: string; monochromeImage: string}
    }
    extra: {eas: {projectId?: string}}
    updates: {enabled: boolean; codeSigningCertificate?: string}
    plugins: (string | [string, unknown])[]
  }
}

/** Evaluate build configuration outside Expo's test-time env inlining. */
function configure(_config: {}) {
  return JSON.parse(
    execFileSync(
      process.execPath,
      [
        '-e',
        "process.stdout.write(JSON.stringify(require('./app.config')({})))",
      ],
      {cwd: resolve(__dirname, '..'), env: process.env, encoding: 'utf8'},
    ),
  ) as AppConfig
}

const savedEnv = process.env

beforeEach(() => {
  process.env = {...savedEnv}
  delete process.env.EXPO_PUBLIC_APP_VARIANT
  delete process.env.SENTRY_AUTH_TOKEN
  delete process.env.SENTRY_ORG
  delete process.env.SENTRY_PROJECT
  delete process.env.DISABLE_UPDATES
  delete process.env.CAELUM_GOOGLE_SERVICES_FILE
  process.env.EXPO_PUBLIC_ENV = 'production'
  process.env.EAS_BUILD_PLATFORM = 'android'
})

afterEach(() => {
  process.env = savedEnv
})

it('isolates the fork from upstream updates, Firebase and build services', () => {
  process.env.EXPO_PUBLIC_APP_VARIANT = 'bluesky-plus'
  process.env.SENTRY_AUTH_TOKEN = 'inherited-token'
  delete process.env.BLUESKY_PLUS_EAS_OWNER
  delete process.env.BLUESKY_PLUS_EAS_PROJECT_ID
  const {expo} = configure({})
  expect(expo.name).toBe('Caelum')
  expect(expo.ios.infoPlist.CFBundleSpokenName).toBe('Caelum')
  expect(expo.scheme).toBe('blueskyplus')
  expect(expo.android.package).toBe('am.noz.caelum')
  expect(expo.android.googleServicesFile).toBeUndefined()
  expect(expo.android.intentFilters).toContainEqual({
    action: 'VIEW',
    autoVerify: true,
    data: [{scheme: 'https', host: 'bsky.app'}],
    category: ['BROWSABLE', 'DEFAULT'],
  })
  expect(expo.owner).toBeUndefined()
  expect(expo.extra.eas.projectId).toBeUndefined()
  expect(expo.updates.enabled).toBe(false)
  expect(expo.updates.codeSigningCertificate).toBeUndefined()
  const plugins = expo.plugins.map((p: string | string[]) =>
    typeof p === 'string' ? p : p[0],
  )
  expect(plugins).not.toContain('@bitdrift/react-native')
  expect(plugins).not.toContain('@sentry/react-native/expo')
})

it('uses the fork publisher configuration for Sentry uploads and Expo', () => {
  process.env.EXPO_PUBLIC_APP_VARIANT = 'bluesky-plus'
  process.env.BLUESKY_PLUS_EAS_OWNER = 'our-publisher'
  process.env.BLUESKY_PLUS_EAS_PROJECT_ID = 'our-project'
  process.env.SENTRY_AUTH_TOKEN = 'our-token'
  process.env.SENTRY_ORG = 'our-org'
  process.env.SENTRY_PROJECT = 'our-android-app'
  const {expo} = configure({})
  expect(expo.owner).toBe('our-publisher')
  expect(expo.extra.eas.projectId).toBe('our-project')
  expect(expo.plugins).toContainEqual([
    '@sentry/react-native/expo',
    expect.objectContaining({
      organization: 'our-org',
      project: 'our-android-app',
    }),
  ])
})

it('preserves upstream production configuration outside the fork profile', () => {
  const {expo} = configure({})
  expect(expo.name).toBe('Bluesky')
  expect(expo.android.package).toBe('xyz.blueskyweb.app')
  expect(expo.android.googleServicesFile).toBe('./google-services.json')
  expect(expo.updates.enabled).toBe(true)
  expect(expo.owner).toBe('blueskysocial')
  expect(expo.android.adaptiveIcon.foregroundImage).toBe(
    './assets/icon-android-foreground.png',
  )
})

it('uses Caelum artwork for every fork launcher alias and native splash', () => {
  process.env.EXPO_PUBLIC_APP_VARIANT = 'bluesky-plus'
  const {expo} = configure({})
  const prefix = './assets/branding/caelum/generated/'
  expect(expo.icon).toBe(prefix + 'icon-purple.png')
  expect(expo.ios.icon).toBe(expo.icon)
  expect(expo.android.icon).toBe(expo.icon)
  expect(expo.android.adaptiveIcon.foregroundImage).toBe(
    prefix + 'adaptive-foreground.png',
  )
  expect(expo.android.adaptiveIcon.monochromeImage).toBe(
    prefix + 'adaptive-monochrome.png',
  )
  const [, icons] = expo.plugins.find(
    p => Array.isArray(p) && p[0] === '@bsky.app/expo-dynamic-app-icon',
  ) as [string, Record<string, {ios: string; android: string}>]
  expect(Object.keys(icons)).toHaveLength(11)
  for (const icon of Object.values(icons) as {ios: string; android: string}[]) {
    expect(icon.ios.startsWith(prefix)).toBe(true)
    expect(icon.android.startsWith(prefix)).toBe(true)
  }
  const [, splash] = expo.plugins.find(
    p => Array.isArray(p) && p[0] === 'expo-splash-screen',
  ) as [string, {android: {image: string; dark: {image: string}}}]
  expect(splash.android.image).toBe(prefix + 'splash.png')
  expect(splash.android.dark.image).toBe(prefix + 'splash-dark.png')
})

it('excludes the Bitdrift Android SDK from the fork native dependency graph', () => {
  process.env.EXPO_PUBLIC_APP_VARIANT = 'bluesky-plus'
  const config = JSON.parse(
    execFileSync(
      process.execPath,
      [
        '-e',
        "process.stdout.write(JSON.stringify(require('./react-native.config')))",
      ],
      {cwd: resolve(__dirname, '..'), env: process.env, encoding: 'utf8'},
    ),
  ) as {dependencies: {'@bitdrift/react-native': {platforms: {android: null}}}}
  expect(
    config.dependencies['@bitdrift/react-native'].platforms.android,
  ).toBeNull()
})
