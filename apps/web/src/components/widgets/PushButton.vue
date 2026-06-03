<script setup>
const props = defineProps({
    text: {},
    type: {
        default: 'button',
    }
});

const emit = defineEmits([
    'click',
]);
</script>

<template>
    <button class="bg-orange-dark rounded-lg px-6 py-2 min-w-[140px] font-title text-sm text-white cursor-pointer"
        :type="type"
        @click="emit('click')"
    >{{ text }}</button>
</template>
