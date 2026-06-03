<script setup>
import { inject, onMounted, ref } from 'vue';
import { useClient } from '../../composables/client.js';
import { DocumentFunctions } from '../../dlb-lib/util/DocumentFunctions.js';
import { User } from '../../dlb-lib/model/User.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

const state = inject('state');
const client = useClient();

const username = ref('');
const password = ref('');
const remember = ref(false);
const errors = ref({});
const errorMessage = ref('');

const inputs = {};

onMounted(() => {
    inputs.username = document.getElementsByName('username')[0];
    inputs.password = document.getElementsByName('password')[0];
    inputs.username.focus();
});

function onLoginClick() {
    const input = validateInput();
    if (input === false) {
        return;
    }
    client.login(input.username, input.password)
    .then((json) => {
        onLoginSuccess(json);
    })
    .catch((error) => {
        inputs.username.focus();
        if (error instanceof Response && (error.status === 400 || error.status === 401)) {
            errorMessage.value = 'The username or password is incorrect.';
        } else {
            errorMessage.value = 'An unknown error has occured.';
        }
    });
}

function validateInput() {
    errors.value = {};
    errorMessage.value = '';
    const input = {
        username: username.value.trim(),
        password: password.value,
    }
    if (input.password.length == 0) {
        errors.value['password'] = true;
        inputs.password.focus();
    }
    if (input.username.length == 0) {
        errors.value['username'] = true;
        inputs.username.focus();
    }
    if (Object.keys(errors.value).length > 0) {
        return false;
    } else {
        return input;
    }
}

function onLoginSuccess(responseJson) {
    console.log("The onLoginSuccess function has been called.");
    
    const user = new User(
        responseJson.user, 
        responseJson.roles, 
        responseJson.accessToken, 
        responseJson.accessTokenExpiresIn, 
        responseJson.refreshToken,
        responseJson.refreshTokenExpiresIn
    );
    
    const cookieExpiration = remember.value ? 365 : null;
    DocumentFunctions.setCookie('user.name', user.name, cookieExpiration);
    DocumentFunctions.setCookie('user.roles', user.roles, cookieExpiration);
    DocumentFunctions.setCookie('user.accessToken', user.accessToken, cookieExpiration);
    DocumentFunctions.setCookie('user.accessTokenExpiresIn', user.accessTokenExpiresIn, cookieExpiration);
    DocumentFunctions.setCookie('user.refreshToken', user.refreshToken, cookieExpiration);
    DocumentFunctions.setCookie('user.refreshTokenExpiresIn', user.refreshTokenExpiresIn, cookieExpiration);
    
    // App.vue defines that if state.user is set, switch to the MainPage.vue
    state.value.user = user;
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center">
        <img class="block pt-24 w-[300px] sm:w-[690px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <div class="mt-3 font-title font-bold">Web Client Test Application</div>

        <div class="w-full px-4 my-6">
            <div class="sm:w-[448px] bg-box rounded-2xl px-5 py-5 mx-auto">
                <form @submit.prevent>
                    <div class="sm:flex sm:items-center mt-2">
                        <label class="font-title font-bold text-right sm:basis-[130px] sm:pr-4" for="username">Username:</label>
                        <TextInput class="block w-full sm:basis-0 sm:grow mt-2 sm:mt-0" type="text" name="username" placeholder="Username..." :error="errors.username" v-model="username"></TextInput>
                    </div>
                    <div class="sm:flex sm:items-center mt-6">
                        <label class="font-title font-bold text-right sm:basis-[130px] sm:pr-4" for="password">Password:</label>
                        <TextInput class="block w-full sm:basis-0 sm:grow mt-2 sm:mt-0" type="password" name="password" placeholder="Password..." :error="errors.password" v-model="password"></TextInput>
                    </div>
                    <div class="flex items-center mt-2">
                        <label class="font-title font-light text-right grow pr-2" for="remember">Remember me?</label>
                        <input type="checkbox" name="remember" v-model="remember" />
                    </div>
                    <div class="text-right mt-4">
                        <PushButton text="Log in" type="submit" @click="onLoginClick" />
                    </div>
                    <div v-if="errorMessage" class="bg-white font-title font-bold text-sm text-red-500 rounded-3xl px-4 py-2 mt-4">{{ errorMessage }}</div>
                </form>
            </div>
        </div>
    </div>
</template>
