import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { store } from './store'
import './style.css'

store.bootstrap().finally(() => {
  createApp(App).use(router).mount('#app')
})
