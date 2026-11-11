import {execFileSync} from 'node:child_process'
import {resolve} from 'node:path'

function productionMessages(variant: string) {
  return JSON.parse(
    execFileSync(
      process.execPath,
      [
        '-e',
        `
          const {transformSync} = require('@babel/core')
          const {i18n} = require('@lingui/core')
          const {compileMessage} = require('@lingui/message-utils/compileMessage')
          const config = require('./babel.config')({env: name => name === 'production'})
          const plugin = config.plugins.find(p => Array.isArray(p) && p[0] === '@lingui/babel-plugin-lingui-macro')
          const source = "import {t} from '@lingui/core/macro'; const percent = 42; export const label = t\u0060Describe\u0060; export const progress = t\u0060Downloading description model… \u0024{percent}%\u0060;"
          const output = transformSync(source, {
            filename: 'release-fallback-probe.ts', babelrc: false, configFile: false,
            plugins: [plugin, '@babel/plugin-transform-modules-commonjs'],
          }).code
          const jsx = transformSync("import {Trans} from '@lingui/react/macro'; const label = <Trans>Describe</Trans>;", {
            filename: 'release-fallback-probe.tsx', babelrc: false, configFile: false,
            parserOpts: {plugins: ['jsx']}, plugins: [plugin],
          }).code
          i18n.loadAndActivate({locale: 'en', messages: {}})
          if (process.env.EXPO_PUBLIC_APP_VARIANT === 'bluesky-plus') {
            i18n.setMessagesCompiler(compileMessage)
          }
          const result = {exports: {}}
          new Function('require', 'module', 'exports', output)(require, result, result.exports)
          process.stdout.write(JSON.stringify({...result.exports, jsx}))
        `,
      ],
      {
        cwd: resolve(__dirname, '..'),
        env: {
          ...process.env,
          NODE_ENV: 'production',
          EXPO_PUBLIC_APP_VARIANT: variant,
        },
        encoding: 'utf8',
      },
    ),
  ) as {label: string; progress: string; jsx: string}
}

it('renders new fork messages and ICU values in production before catalogs are updated', () => {
  const result = productionMessages('bluesky-plus')
  expect(result.label).toBe('Describe')
  expect(result.progress).toBe('Downloading description model… 42%')
  expect(result.jsx).toMatch(/message:\s*"Describe"/)
})

it('preserves message stripping for upstream production builds', () => {
  const result = productionMessages('')
  expect(result.label).not.toBe('Describe')
  expect(result.jsx).not.toMatch(/message:\s*"Describe"/)
})
