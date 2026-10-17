<script setup>
import { computed, inject } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// The menu items of the management pages, for PageHeader's menu bar: Project Management (the
// project selection) and, for admins, User Management (the Users page). `active` names the
// current page. On a phone the items have short labels ("Projects", "Users"), so the bar fits.

defineProps({
    active: { type: String, required: true }, // 'projects' | 'users'
});

const state = inject('state');
const isAdmin = computed(() => !!state.value.user?.roles?.includes('admin'));

const items = computed(() => [
    { id: 'projects', label: 'Project Management', short: 'Projects', icon: 'fa-solid fa-folder-open', page: null },
    ...(isAdmin.value ? [{ id: 'users', label: 'User Management', short: 'Users', icon: 'fa-solid fa-users', page: 'users' }] : []),
]);

function open(item) {
    state.value.adminPage = item.page;
}
</script>

<template>
    <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="flex items-center gap-2 px-3 sm:px-4 font-title text-sm border-r border-orange-dark whitespace-nowrap transition-colors"
        :class="item.id === active ? 'bg-orange-dark text-white cursor-default' : 'text-orange-light hover:text-white hover:bg-orange-dark cursor-pointer'"
        :aria-current="item.id === active ? 'page' : undefined"
        :data-test="'menu-' + item.id"
        @click="open(item)"
    >
        <FontAwesomeIcon :icon="item.icon" class="opacity-80" />
        <span class="sm:hidden">{{ item.short }}</span>
        <span class="hidden sm:inline">{{ item.label }}</span>
    </button>
</template>
