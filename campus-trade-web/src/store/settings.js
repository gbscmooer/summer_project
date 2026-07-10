import { defineStore } from 'pinia'

const STORAGE_KEY = 'campusTradeSettings'

const DEFAULTS = {
  locale: 'zh-CN',
  theme: 'dark', // dark | light | system
  compactMode: false,
  reduceMotion: false
}

function loadSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULTS }
    return { ...DEFAULTS, ...JSON.parse(raw) }
  } catch {
    return { ...DEFAULTS }
  }
}

function resolveDark(theme) {
  if (theme === 'light') return false
  if (theme === 'dark') return true
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyTheme(theme) {
  const isDark = resolveDark(theme)
  document.documentElement.classList.toggle('dark', isDark)
  document.documentElement.style.colorScheme = isDark ? 'dark' : 'light'
}

function applyLocale(locale) {
  document.documentElement.lang = locale === 'en-US' ? 'en' : 'zh-CN'
}

function applyMotion(reduceMotion) {
  document.documentElement.classList.toggle('reduce-motion', !!reduceMotion)
}

function applyCompact(compactMode) {
  document.documentElement.classList.toggle('compact', !!compactMode)
}

let mediaQueryBound = false

export const useSettingsStore = defineStore('settings', {
  state: () => loadSettings(),

  getters: {
    isDark: (state) => resolveDark(state.theme)
  },

  actions: {
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          locale: this.locale,
          theme: this.theme,
          compactMode: this.compactMode,
          reduceMotion: this.reduceMotion
        })
      )
    },

    init() {
      applyTheme(this.theme)
      applyLocale(this.locale)
      applyMotion(this.reduceMotion)
      applyCompact(this.compactMode)
      this.bindSystemThemeListener()
    },

    bindSystemThemeListener() {
      if (mediaQueryBound || typeof window === 'undefined') return
      mediaQueryBound = true
      const mq = window.matchMedia('(prefers-color-scheme: dark)')
      const onChange = () => {
        if (this.theme === 'system') applyTheme('system')
      }
      if (mq.addEventListener) mq.addEventListener('change', onChange)
      else mq.addListener(onChange)
    },

    setLocale(locale) {
      this.locale = locale
      applyLocale(locale)
      this.persist()
    },

    setTheme(theme) {
      this.theme = theme
      applyTheme(theme)
      this.persist()
    },

    setCompactMode(value) {
      this.compactMode = !!value
      applyCompact(this.compactMode)
      this.persist()
    },

    setReduceMotion(value) {
      this.reduceMotion = !!value
      applyMotion(this.reduceMotion)
      this.persist()
    },

    resetPreferences() {
      this.locale = DEFAULTS.locale
      this.theme = DEFAULTS.theme
      this.compactMode = DEFAULTS.compactMode
      this.reduceMotion = DEFAULTS.reduceMotion
      this.init()
      this.persist()
    }
  }
})
