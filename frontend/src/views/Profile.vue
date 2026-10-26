<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { store } from '../store'

const err = ref('')
const okMsg = ref('')
const busy = ref(false)

const info = ref({ username: '', nickname: '', avatar: '', role: '' })
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })

const roleText = { ADMIN: '管理员', USER: '普通用户' }

function initial() {
  return (info.value.nickname || info.value.username || '?').slice(0, 1).toUpperCase()
}

async function load() {
  try {
    const u = await api.profile()
    info.value = { ...u }
  } catch (e) {
    err.value = e.message
  }
}

async function saveProfile() {
  err.value = ''
  okMsg.value = ''
  busy.value = true
  try {
    const u = await api.updateProfile({
      nickname: info.value.nickname,
      avatar: info.value.avatar
    })
    store.user = u
    info.value = { ...u }
    okMsg.value = '资料已保存。'
  } catch (e) {
    err.value = e.message
  } finally {
    busy.value = false
  }
}

async function savePassword() {
  err.value = ''
  okMsg.value = ''
  if (!pwd.value.oldPassword || !pwd.value.newPassword) {
    err.value = '请填写原密码和新密码'
    return
  }
  if (pwd.value.newPassword.length < 6) {
    err.value = '新密码至少 6 位'
    return
  }
  if (pwd.value.newPassword !== pwd.value.confirm) {
    err.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    await api.changePassword({
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword
    })
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
    okMsg.value = '密码已修改，下次登录请使用新密码。'
  } catch (e) {
    err.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>个人中心</h2>
        <p class="sub">查看和修改你的账号信息。</p>
      </div>
    </div>

    <p v-if="err" class="alert">{{ err }}</p>
    <p v-if="okMsg" class="alert notice">{{ okMsg }}</p>

    <div class="cols">
      <div class="panel">
        <div class="panel-head"><h3>基本资料</h3></div>
        <div class="panel-body">
          <div class="profile-top">
            <span class="avatar">{{ initial() }}</span>
            <div>
              <div class="uname">{{ info.nickname || info.username }}</div>
              <div class="row" style="gap: 6px; margin-top: 4px">
                <span class="tag" :class="info.role === 'ADMIN' ? 'tag-brand' : ''">
                  {{ roleText[info.role] || info.role }}
                </span>
                <span class="muted mono small">ID {{ info.id }}</span>
              </div>
            </div>
          </div>

          <div class="field" style="margin-top: 20px">
            <label>用户名</label>
            <input class="input" :value="info.username" disabled />
            <span class="hint">用户名是登录凭证，不支持修改。</span>
          </div>

          <div class="field" style="margin-top: 14px">
            <label>昵称</label>
            <input v-model="info.nickname" class="input" placeholder="展示用昵称" />
          </div>

          <div class="field" style="margin-top: 14px">
            <label>头像地址（选填）</label>
            <input v-model="info.avatar" class="input" placeholder="https://…" />
            <span class="hint">留空时使用用户名首字母作为头像。</span>
          </div>

          <div class="row" style="margin-top: 20px; justify-content: flex-end">
            <button class="btn btn-primary" :disabled="busy" @click="saveProfile">保存资料</button>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><h3>修改密码</h3></div>
        <div class="panel-body">
          <div class="field">
            <label>原密码</label>
            <input v-model="pwd.oldPassword" type="password" class="input"
                   placeholder="请输入原密码" autocomplete="current-password" />
          </div>
          <div class="field" style="margin-top: 14px">
            <label>新密码</label>
            <input v-model="pwd.newPassword" type="password" class="input"
                   placeholder="至少 6 位" autocomplete="new-password" />
          </div>
          <div class="field" style="margin-top: 14px">
            <label>确认新密码</label>
            <input v-model="pwd.confirm" type="password" class="input"
                   placeholder="再次输入新密码" autocomplete="new-password" />
          </div>
          <div class="row" style="margin-top: 20px; justify-content: flex-end">
            <button class="btn btn-primary" :disabled="busy" @click="savePassword">修改密码</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.alert {
  margin-bottom: 14px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
}
.notice { background: var(--brand-soft); color: var(--brand-dark); }

.cols { display: grid; grid-template-columns: 1.2fr 1fr; gap: 16px; align-items: start; }
.profile-top { display: flex; align-items: center; gap: 14px; }
.avatar {
  width: 54px; height: 54px; flex: none;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid; place-items: center;
  font-size: 20px; font-weight: 600;
}
.uname { font-size: 15px; font-weight: 600; }
.small { font-size: 12px; }
@media (max-width: 900px) {
  .cols { grid-template-columns: 1fr; }
}
</style>
