const listeners = new Map()

const isBrowser = typeof window !== 'undefined'
const channel = isBrowser && typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('cat-coffee-sync') : null

channel?.addEventListener('message', ({ data }) => {
  listeners.get(data.event)?.forEach((handler) => handler(data.payload))
})

if (isBrowser) {
  window.addEventListener('storage', (event) => {
    if (event.key !== 'cat-coffee-sync-event' || !event.newValue) return
    try {
      const message = JSON.parse(event.newValue)
      listeners.get(message.event)?.forEach((handler) => handler(message.payload))
    } catch {
      // Ignore malformed cross-tab events.
    }
  })
}

export const syncBus = {
  on(event, handler) {
    const handlers = listeners.get(event) || new Set()
    handlers.add(handler)
    listeners.set(event, handlers)
    return () => handlers.delete(handler)
  },
  emit(event, payload = {}) {
    listeners.get(event)?.forEach((handler) => handler(payload))
    channel?.postMessage({ event, payload })
    localStorage.setItem('cat-coffee-sync-event', JSON.stringify({ event, payload, timestamp: Date.now() }))
    window.dispatchEvent(new CustomEvent(`cat-coffee:${event}`, { detail: payload }))
  }
}

export const resourceChanged = (resource, payload = {}) => {
  syncBus.emit('resource-changed', { resource, ...payload })
}
