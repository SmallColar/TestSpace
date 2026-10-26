<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { store } from './store'
import SideNav from './components/SideNav.vue'

const route = useRoute()
const bare = computed(() => Boolean(route.meta.public))
</script>

<template>
  <router-view v-if="bare" />
  <div v-else class="shell">
    <SideNav />
    <main class="main">
      <header class="topbar"><span>工作台 / {{ route.meta.title || 'TestPilot' }}</span><span>{{ store.user?.nickname || store.user?.username }}　⌄</span></header>
      <div class="content">
      <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: #f6f8fb;
}
.main { flex:1; min-width:0; }
.topbar { height:54px; display:flex; align-items:center; justify-content:space-between; padding:0 28px; background:#fff; border-bottom:1px solid #e7ebf1; color:#68768b; font-size:12px; }
.content {
  flex: 1;
  min-width: 0;
  padding: 24px 28px 48px;
}
</style>
