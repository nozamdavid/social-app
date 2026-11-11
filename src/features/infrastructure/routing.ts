import {type Agent, type AgentOptions, buildAgent, isAgent} from '@atproto/lex'

import {BLUESKY_PROXY_HEADER} from '#/lib/constants'
import {getInfrastructureSettings} from './preferences'

/** AppView calls exclude preferences, video, repo writes, sessions and chat. */
export function isAppviewRequest(path: string) {
  const pathname = path.split('?')[0]
  return (
    /^\/xrpc\/app\.bsky\.(?!video\.)[A-Za-z0-9.]+$/.test(pathname) &&
    !/^\/xrpc\/app\.bsky\.actor\.(getPreferences|putPreferences)$/.test(
      pathname,
    )
  )
}

/** Route signed requests through the PDS; retarget only unauthenticated public reads. */
export function withInfrastructureRouting(
  options: Agent | AgentOptions,
): Agent {
  const agent = buildAgent(options)
  const config =
    typeof options === 'string' || options instanceof URL
      ? {service: options}
      : typeof options === 'object' && !isAgent(options)
        ? options
        : undefined
  return {
    get did() {
      return agent.did
    },
    fetchHandler(path, init) {
      const hostname = getInfrastructureSettings().appviewHostname
      if (!hostname || !isAppviewRequest(path))
        return agent.fetchHandler(path, init)
      const headers = new Headers(init.headers)
      if (headers.get('atproto-proxy') === BLUESKY_PROXY_HEADER.get()) {
        headers.set('atproto-proxy', `did:web:${hostname}#bsky_appview`)
        headers.delete('x-atproto-device-id')
        headers.delete('x-atproto-session-id')
        return agent.fetchHandler(path, {...init, headers})
      }
      if (
        config &&
        !agent.did &&
        !headers.has('atproto-proxy') &&
        (init.method ?? 'GET') === 'GET'
      ) {
        const defaults = new Headers(
          'headers' in config ? config.headers : undefined,
        )
        const authenticated = ['authorization', 'dpop'].some(
          header => headers.has(header) || defaults.has(header),
        )
        const service = new URL(config.service)
        if (
          !authenticated &&
          service.protocol === 'https:' &&
          ['api.bsky.app', 'public.api.bsky.app'].includes(service.hostname)
        ) {
          headers.delete('x-atproto-device-id')
          headers.delete('x-atproto-session-id')
          return buildAgent({
            ...config,
            service: `https://${hostname}`,
          }).fetchHandler(path, {...init, headers})
        }
      }
      return agent.fetchHandler(path, init)
    },
  }
}
