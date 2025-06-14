// src/firebase.js
import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
    apiKey: "AIzaSyA_6XtbsDR4CT2ocwlBAnJdZJNiUE8x_IU",
    authDomain: "photoapp-c195d.firebaseapp.com",
    projectId: "photoapp-c195d",
    storageBucket: "photoapp-c195d.firebasestorage.app",
    messagingSenderId: "757050624728",
    appId: "1:757050624728:web:e71c3800d10c28d4e4a5ad"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);

export { auth };
