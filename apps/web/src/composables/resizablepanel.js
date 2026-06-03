import { onMounted, ref } from 'vue';

export function useResizablePanel(root) {
    const width = ref(0);
    
    const breakpoints = {
        'sm': 640,
        'md': 768,
        'lg': 1024,
        'xl': 1280,
        '2xl': 1536,
    };

    const resize = () => {
        width.value = root.value.clientWidth;
    };

    onMounted(() => {
        resize(root.value.clientWidth);
    });

    const resizableClasses = (classes) => {
        const result = {};
        const keys = Object.keys(classes);
        for (const [key, value] of Object.entries(classes)) {
            const [min, max] = findMinMax(key, keys);
            const valueList = value.trim().split(/\s+/);
            valueList.forEach((classStr) => {
                if (min !== null && max !== null) {
                    result[classStr] = width.value >= min && width.value < max;
                } else if (min !== null) {
                    result[classStr] = width.value >= min;
                } else if (max !== null) {
                    result[classStr] = width.value < max;
                } else {
                    result[classStr] = true;
                }
            });
        }
        return result;
    };

    function findMinMax(key, keys) {
        const min = key === 'default' ? null : breakpoints[key];
        const ceilBreakpoint = findCeilBreakpoint(key, keys);
        const max = ceilBreakpoint === null ? null : breakpoints[ceilBreakpoint];
        return [min, max];
    }

    function findCeilBreakpoint(key, keys) {
        const order = Object.keys(breakpoints);
        var ceiling = null;
        for (let i = order.length - 1; i >= 0; i--) {
            const orderKey = order[i];
            if (orderKey === key) {
                return ceiling;
            }
            if (keys.includes(orderKey)) {
                ceiling = orderKey;
            }
        }
        return ceiling;
    }

    return { resize, resizableClasses };
}
