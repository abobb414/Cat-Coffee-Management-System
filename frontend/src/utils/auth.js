const TOKEN_KEY = 'cat-coffee-token'
const REFRESH_TOKEN_KEY = 'cat-coffee-refresh-token'
const USER_KEY = 'cat-coffee-user'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const getRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY) || ''

export const getUserInfo = () => {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}

export const setUserInfo = (userInfo) => {
  localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
}

export const setAuth = ({ accessToken, refreshToken, userInfo }) => {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
}

export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const hasPermission = (permission) => {
  const user = getUserInfo()
  return !!user?.permissions?.includes(permission)
}

export const hasRole = (role) => {
  const user = getUserInfo()
  return !!user?.roles?.includes(role)
}

export const getHomePath = () => {
  const user = getUserInfo()
  const permissions = user?.permissions || []
  const roles = user?.roles || []

  // 顾客角色先落地到自己的业务页
  if (roles.includes('user') && permissions.includes('reservation:read')) {
    return '/reservations'
  }
  // 经营看板优先级高于会员积分：staff/admin 同时持有 points:read，
  // 若把 points 放在前面，管理员和店员登录后会被抢跳到会员积分页而不是看板。
  if (permissions.includes('dashboard:view')) {
    return '/dashboard'
  }
  if (permissions.includes('points:read')) {
    return '/member/points'
  }
  if (permissions.includes('cat:read')) {
    return '/cats'
  }
  if (permissions.includes('drink:read')) {
    return '/drinks'
  }
  if (permissions.includes('reservation:read')) {
    return '/reservations'
  }
  if (permissions.includes('order:read')) {
    return '/orders'
  }
  return '/login'
}
