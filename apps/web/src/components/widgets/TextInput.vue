<script setup>
const input = defineModel();

const props = defineProps([
    'name',
    'type',
    'error',
    'placeholder',
]);
</script>

<template>
    <input class="font-title bg-white p-3 rounded-md border-[2px] border-solid border-gray-300 text-sm min-w-0" :class="{'border-red-500': error}" :name="name" :type="type" :placeholder="placeholder" v-model="input"></input>
</template>
