import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getOnboardingStatus, markOnboardingStep, completeOnboarding } from '@/api/user'
import { getTutorialProduct, getMyProducts } from '@/api/product'
import { getBuyerOrders } from '@/api/order'

const STEP_DEFS = [
  { key: 'register', route: '/register', action: 'register' },
  { key: 'browse', route: '/', action: 'browse' },
  { key: 'ai', route: '/?mode=ai', action: 'ai' },
  { key: 'publish', route: '/publish', action: 'publish' },
  { key: 'buy', route: null, action: 'buy' },
  { key: 'pay', route: '/orders', action: 'pay' },
  { key: 'confirm', route: '/orders', action: 'confirm' },
  { key: 'notify', route: '/notifications', action: 'notify' },
  { key: 'profile', route: '/my', action: 'profile' }
]

const sharedState = {
  loaded: false,
  visible: false,
  flags: {},
  tutorialProductId: null,
  publishedCount: 0,
  tutorialOrders: []
}

function isAllStepsDone(flags, orders, publishedCount, userInfo) {
  const hasTutorialOrder = orders.length > 0
  const paidOrder = orders.some((o) => o.status >= 1)
  const doneOrder = orders.some((o) => o.status === 2)
  return !!(
    flags.browse &&
    flags.ai &&
    publishedCount > 0 &&
    hasTutorialOrder &&
    paidOrder &&
    doneOrder &&
    flags.notify &&
    (flags.profile || (userInfo && userInfo.phone))
  )
}

export function useOnboarding() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)

  const steps = computed(() => {
    const flags = sharedState.flags || {}
    const orders = sharedState.tutorialOrders || []
    const hasTutorialOrder = orders.length > 0
    const paidOrder = orders.some((o) => o.status >= 1)
    const doneOrder = orders.some((o) => o.status === 2)

    const doneMap = {
      register: userStore.isLogin,
      browse: !!flags.browse,
      ai: !!flags.ai,
      publish: sharedState.publishedCount > 0,
      buy: hasTutorialOrder,
      pay: paidOrder,
      confirm: doneOrder,
      notify: !!flags.notify,
      profile: !!flags.profile || !!(userStore.userInfo && userStore.userInfo.phone)
    }

    return STEP_DEFS.map((def) => ({
      ...def,
      done: !!doneMap[def.key],
      productId: def.key === 'buy' ? sharedState.tutorialProductId : null
    }))
  })

  const completedCount = computed(() => steps.value.filter((s) => s.done).length)
  const totalCount = computed(() => steps.value.length)
  const allDone = computed(() => completedCount.value === totalCount.value)
  const progressPercent = computed(() =>
    totalCount.value ? Math.round((completedCount.value / totalCount.value) * 100) : 0
  )

  async function refresh() {
    if (!userStore.isLogin) {
      sharedState.visible = false
      sharedState.loaded = true
      return
    }
    loading.value = true
    try {
      const [statusRes, tutorialRes] = await Promise.all([
        getOnboardingStatus(),
        getTutorialProduct().catch(() => null)
      ])
      const status = statusRes.data || {}
      sharedState.visible = !!status.visible
      sharedState.flags = status.flags || {}
      sharedState.tutorialProductId = tutorialRes?.data?.productId || null

      if (!sharedState.visible) {
        sharedState.loaded = true
        return
      }

      const [myRes, orderRes] = await Promise.all([
        getMyProducts({ pageNum: 1, pageSize: 1 }),
        sharedState.tutorialProductId
          ? getBuyerOrders({ pageNum: 1, pageSize: 20 })
          : Promise.resolve({ data: { list: [] } })
      ])
      sharedState.publishedCount = myRes.data?.total || 0
      const allOrders = orderRes.data?.list || []
      sharedState.tutorialOrders = sharedState.tutorialProductId
        ? allOrders.filter((o) => o.productId === sharedState.tutorialProductId)
        : []

      if (
        isAllStepsDone(
          sharedState.flags,
          sharedState.tutorialOrders,
          sharedState.publishedCount,
          userStore.userInfo
        )
      ) {
        await completeOnboarding()
        sharedState.visible = false
        userStore.setUserInfo({ onboardingCompleted: 1 })
      }
    } catch (e) {
      // 静默失败，不影响首页其他内容
    } finally {
      sharedState.loaded = true
      loading.value = false
    }
  }

  async function trackStep(step) {
    if (!userStore.isLogin) return
    try {
      await markOnboardingStep(step)
      sharedState.flags = { ...sharedState.flags, [step]: true }
      if (sharedState.visible) {
        await refresh()
      }
    } catch (e) {
      // ignore
    }
  }

  function goToStep(step) {
    if (step.key === 'register' && !userStore.isLogin) {
      router.push('/register')
      return
    }
    if (step.key === 'ai') {
      router.push({ path: '/', query: { mode: 'ai' } })
      return
    }
    if (step.key === 'buy' && step.productId) {
      router.push(`/product/${step.productId}`)
      return
    }
    if (step.route) {
      router.push(step.route)
    }
  }

  async function dismiss() {
    if (!userStore.isLogin) return
    try {
      await completeOnboarding()
      sharedState.visible = false
      userStore.setUserInfo({ onboardingCompleted: 1 })
    } catch (e) {
      // ignore
    }
  }

  return {
    loading,
    visible: computed(() => sharedState.visible),
    loaded: computed(() => sharedState.loaded),
    steps,
    completedCount,
    totalCount,
    allDone,
    progressPercent,
    tutorialProductId: computed(() => sharedState.tutorialProductId),
    refresh,
    trackStep,
    goToStep,
    dismiss
  }
}
