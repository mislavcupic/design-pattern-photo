import React, { createContext, useContext, useState, useEffect, useMemo, useCallback } from "react";
import { auth, db } from "../components/Firebase"; // DODANO: db import ovdje
import {
    createUserWithEmailAndPassword,
    signInWithEmailAndPassword,
    signOut,
    GoogleAuthProvider,
    GithubAuthProvider,
    signInWithPopup,
    deleteUser
} from "firebase/auth";
// DODANO: Importi za Firestore funkcije
import { doc, getDoc } from "firebase/firestore";

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [idToken, setIdToken] = useState(null);
    const [loading, setLoading] = useState(true);
    const [firstThirdPartyLoginDetected, setFirstThirdPartyLoginDetected] = useState(false);

    const sendIdTokenToBackend = useCallback(async (token) => {
        try {
            const response = await fetch('http://localhost:8080/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ idToken: token }),
            });

            if (response.ok) {
                const data = await response.json();
                if (data.role) {
                    localStorage.setItem('role', data.role);
                    window.dispatchEvent(new Event('storage'));
                }
                console.log("✅ ID token uspješno poslan backendu:", data);
            }
        } catch (error) {
            console.error("⚠️ Greška u komunikaciji s backendom:", error);
        }
    }, []);

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(async (firebaseUser) => {
            if (firebaseUser) {
                const token = await firebaseUser.getIdToken();

                // NOVO: Dohvaćanje uloge izravno iz Firestore-a pri svakoj promjeni stanja
                const userDocRef = doc(db, "users", firebaseUser.uid);
                const userDoc = await getDoc(userDocRef);
                const actualRole = userDoc.exists() ? (userDoc.data().userType || "REGISTERED") : "REGISTERED";

                // Postavljamo prošireni user objekt koji sadrži ulogu
                setUser({
                    ...firebaseUser,
                    role: actualRole
                });

                setIdToken(token);
                localStorage.setItem('idToken', token);
                localStorage.setItem('role', actualRole);
                window.dispatchEvent(new Event('storage'));

                const isNewUser = firebaseUser.metadata.creationTime === firebaseUser.metadata.lastSignInTime;
                const isThirdParty = firebaseUser.providerData.length > 0;

                if (isThirdParty && isNewUser && !firstThirdPartyLoginDetected) {
                    setFirstThirdPartyLoginDetected(true);
                    sendIdTokenToBackend(token);
                }
            } else {
                setUser(null);
                setIdToken(null);
                localStorage.removeItem('idToken');
                localStorage.removeItem('role');
                setFirstThirdPartyLoginDetected(false);
            }
            setLoading(false);
        });

        return () => unsubscribe();
    }, [firstThirdPartyLoginDetected, sendIdTokenToBackend]);

    const loginWithEmail = useCallback(async (email, password) => {
        try {
            const userCredential = await signInWithEmailAndPassword(auth, email, password);
            const token = await userCredential.user.getIdToken();

            // Ovdje ne radimo setUser ručno jer će useEffect iznad to odraditi automatski i povući ulogu
            localStorage.setItem('idToken', token);
            console.log("✅ Prijava uspješna, ID token spremljen.");
        } catch (error) {
            console.error("Greška pri prijavi:", error.message);
            throw error;
        }
    }, []);

    const registerWithEmail = useCallback(async (email, password) => {
        try {
            const userCredential = await createUserWithEmailAndPassword(auth, email, password);
            const token = await userCredential.user.getIdToken();
            localStorage.setItem('idToken', token);
            return userCredential;
        } catch (error) {
            console.error("❌ Greška pri registraciji:", error.message);
            throw error;
        }
    }, []);

    const logout = useCallback(async () => {
        await signOut(auth);
        setUser(null);
        setIdToken(null);
        localStorage.clear();
        window.dispatchEvent(new Event('storage'));
    }, []);

    const loginWithGoogle = useCallback(async () => {
        const provider = new GoogleAuthProvider();
        try { await signInWithPopup(auth, provider); } catch (error) { console.error(error); }
    }, []);

    const loginWithGitHub = useCallback(async () => {
        const provider = new GithubAuthProvider();
        try { await signInWithPopup(auth, provider); } catch (error) { console.error(error); }
    }, []);

    const deleteAccount = useCallback(async () => {
        if (auth.currentUser) {
            await deleteUser(auth.currentUser);
            setUser(null);
            setIdToken(null);
            localStorage.clear();
        }
    }, []);

    const contextValue = useMemo(() => ({
        user, idToken, loading, registerWithEmail, loginWithEmail,
        loginWithGoogle, loginWithGitHub, logout, deleteAccount
    }), [user, idToken, loading, registerWithEmail, loginWithEmail, loginWithGoogle, loginWithGitHub, logout, deleteAccount]);

    return (
        <AuthContext.Provider value={contextValue}>
            {!loading && children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);