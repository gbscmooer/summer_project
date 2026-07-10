import { computed } from 'vue'
import { useSettingsStore } from '@/store/settings'

const messages = {
  'zh-CN': {
    nav: {
      home: '首页',
      publish: '发布',
      orders: '订单',
      notifications: '通知',
      profile: '我的',
      settings: '设置'
    },
    settings: {
      title: '设置',
      appearance: '外观',
      appearanceDesc: '调整界面主题与显示密度',
      language: '语言',
      languageDesc: '切换应用界面语言',
      theme: '主题',
      themeDesc: '选择浅色、深色或跟随系统',
      themeDark: '深色',
      themeLight: '浅色',
      themeSystem: '跟随系统',
      compactMode: '紧凑模式',
      compactModeDesc: '减小页面内边距与间距',
      reduceMotion: '减少动效',
      reduceMotionDesc: '关闭过渡动画，减轻视觉干扰',
      account: '账户',
      accountDesc: '管理个人资料与登录状态',
      goProfile: '前往个人中心',
      logout: '退出登录',
      logoutConfirm: '确定要退出登录吗？',
      logoutSuccess: '已退出登录',
      tip: '提示',
      confirm: '确定',
      cancel: '取消',
      data: '数据与缓存',
      dataDesc: '清理本地偏好或重置设置（不会删除账号）',
      clearCache: '清除本地缓存',
      clearCacheDesc: '清除浏览相关的临时数据，保留登录状态与偏好设置',
      clearCacheDone: '本地缓存已清除',
      resetPrefs: '重置偏好设置',
      resetPrefsDesc: '将语言、主题等恢复为默认值',
      resetPrefsConfirm: '确定将所有偏好设置恢复为默认吗？',
      resetPrefsDone: '偏好设置已重置',
      about: '关于',
      aboutDesc: '校园淘 · 校园二手交易平台',
      version: '版本',
      savedLocally: '偏好保存在本机，切换设备后需重新设置'
    },
    common: {
      login: '去登录',
      logout: '退出登录',
      profile: '个人中心',
      notLoggedIn: '未登录',
      clickToLogin: '点击登录',
      promo: '登录后即可发布闲置、下单交易'
    }
  },
  'en-US': {
    nav: {
      home: 'Home',
      publish: 'Publish',
      orders: 'Orders',
      notifications: 'Notifications',
      profile: 'Profile',
      settings: 'Settings'
    },
    settings: {
      title: 'Settings',
      appearance: 'Appearance',
      appearanceDesc: 'Theme and display density',
      language: 'Language',
      languageDesc: 'Choose the interface language',
      theme: 'Theme',
      themeDesc: 'Light, dark, or match your system',
      themeDark: 'Dark',
      themeLight: 'Light',
      themeSystem: 'System',
      compactMode: 'Compact mode',
      compactModeDesc: 'Reduce page padding and spacing',
      reduceMotion: 'Reduce motion',
      reduceMotionDesc: 'Disable transitions for less visual noise',
      account: 'Account',
      accountDesc: 'Manage profile and sign-in',
      goProfile: 'Go to profile',
      logout: 'Sign out',
      logoutConfirm: 'Are you sure you want to sign out?',
      logoutSuccess: 'Signed out',
      tip: 'Confirm',
      confirm: 'OK',
      cancel: 'Cancel',
      data: 'Data & cache',
      dataDesc: 'Clear local data or reset preferences (does not delete your account)',
      clearCache: 'Clear local cache',
      clearCacheDesc: 'Remove temporary browse data; keep sign-in and preferences',
      clearCacheDone: 'Local cache cleared',
      resetPrefs: 'Reset preferences',
      resetPrefsDesc: 'Restore language, theme, and related defaults',
      resetPrefsConfirm: 'Reset all preferences to defaults?',
      resetPrefsDone: 'Preferences reset',
      about: 'About',
      aboutDesc: 'CampusTrade · campus second-hand marketplace',
      version: 'Version',
      savedLocally: 'Preferences are stored on this device only'
    },
    common: {
      login: 'Sign in',
      logout: 'Sign out',
      profile: 'Profile',
      notLoggedIn: 'Not signed in',
      clickToLogin: 'Tap to sign in',
      promo: 'Sign in to list items and place orders'
    }
  }
}

function getByPath(obj, path) {
  return path.split('.').reduce((acc, key) => (acc && acc[key] != null ? acc[key] : null), obj)
}

export function useI18n() {
  const settings = useSettingsStore()
  const locale = computed(() => settings.locale)

  function t(key) {
    const dict = messages[settings.locale] || messages['zh-CN']
    return getByPath(dict, key) ?? getByPath(messages['zh-CN'], key) ?? key
  }

  return { t, locale }
}

export { messages }
