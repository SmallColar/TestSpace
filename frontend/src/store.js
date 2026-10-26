import { reactive } from 'vue'
import { api, setToken, getToken } from './api'

export const store = reactive({
  user: null,
  ready: false,

  get isAdmin() {
    return this.user && this.user.role === 'ADMIN'
  },

  async bootstrap() {
    if (!getToken()) {
      this.ready = true
      return
    }
    try {
      this.user = await api.me()
    } catch (e) {
      this.user = null
    } finally {
      this.ready = true
    }
  },

  async login(username, password) {
    const res = await api.login({ username, password })
    setToken(res.token)
    this.user = res.user || (await api.me())
    return this.user
  },

  logout() {
    setToken('')
    this.user = null
    location.hash = '#/login'
  }
})
