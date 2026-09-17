<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string
  }>(),
  { label: 'CONTEXT RAIL' },
)
</script>

<template>
  <aside class="context-rail">
    <div class="rail-label">{{ label }}</div>
    <div class="rail-line" />
    <slot />
  </aside>
</template>

<style scoped>
.context-rail {
  min-width: 0;
  padding: 24px 20px;
  border-left: 1px solid #b8cbc2;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
}

.rail-label {
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.rail-line {
  height: 1px;
  margin: 12px 0 18px;
  background: #b8cbc2;
}

@media (max-width: 1199px) {
  .context-rail {
    border-top: 1px solid #b8cbc2;
    border-left: 0;
  }
}
</style>
