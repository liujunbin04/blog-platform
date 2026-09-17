<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'content' | 'workspace' | 'auth'
  }>(),
  { variant: 'content' },
)
</script>

<template>
  <main class="app-shell" :class="`app-shell--${variant}`">
    <slot />
  </main>
</template>

<style scoped>
.app-shell {
  width: 100%;
  margin: 0 auto;
  padding: 28px 24px 64px;
}

.app-shell--content {
  max-width: var(--blog-content-width);
}

.app-shell--workspace {
  max-width: var(--blog-workspace-width);
  padding: 18px 18px 40px;
}

.app-shell--auth {
  max-width: 1040px;
  padding-top: 48px;
}

@media (max-width: 767px) {
  .app-shell {
    padding: 18px 14px 48px;
  }
}
</style>
