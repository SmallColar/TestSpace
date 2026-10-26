<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' },
  text: { type: String, default: '' }
})

const cls = computed(() => {
  switch (props.status) {
    case 'COMPLETED':
    case 'SUCCESS':
      return 'tag tag-ok'
    case 'FAILED':
    case 'REJECTED':
      return 'tag tag-danger'
    case 'CANCELLED':
      return 'tag'
    case 'RUNNING':
      return 'tag tag-warn'
    default:
      return 'tag'
  }
})
</script>

<template>
  <span :class="cls">{{ text || status }}</span>
</template>
