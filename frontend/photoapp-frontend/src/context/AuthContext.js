import React, { createContext, useContext, useState, useEffect } from "react";
import { auth } from "../components/Firebase";
import { createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, GoogleAuthProvider, GithubAuthProvider, signInWithPopup, signInAnonymously, deleteUser, updateProfile } from "firebase/auth";

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [idToken, setIdToken] = useState(null);
    const [firstThirdPartyLoginDetected, setFirstThirdPartyLoginDetected] = useState(false);

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(async (firebaseUser) => {
            if (firebaseUser) {
                const token = await firebaseUser.getIdToken();
                setUser(firebaseUser);
                setIdToken(token);

                if (firebaseUser.providerData.length > 0 && firebaseUser.metadata.creationTime === firebaseUser.metadata.lastSignInTime && !firstThirdPartyLoginDetected) {
                    setFirstThirdPartyLoginDetected(true);
                    console.log("✅ Prva prijava third-party korisnika detektirana.");
                    sendIdTokenToBackend(token);
                }
            } else {
                setUser(null);
                setIdToken(null);
                setFirstThirdPartyLoginDetected(false);
            }
        });

        return () => unsubscribe();
    }, [firstThirdPartyLoginDetected]);

    const sendIdTokenToBackend = async (token) => {
        try {
            const response = await fetch('http://localhost:8080/auth/login', { // Prilagodite putanju vašem backend login endpointu
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ idToken: token }),
            });

            if (response.ok) {
                const data = await response.json();
                console.log("✅ ID token uspješno poslan backendu:", data);
                // Ovdje možete obraditi odgovor s backenda (npr., pohraniti custom token)
            } else {
                console.error("❌ Greška prilikom slanja ID tokena backendu:", response.status);
                // Obradite grešku
            }
        } catch (error) {
            console.error("⚠️ Došlo je do pogreške prilikom komunikacije s backendom:", error);
            // Obradite grešku
        }
    };

    const logout = async () => {
        await signOut(auth);
        setUser(null);
        setIdToken(null);
        setFirstThirdPartyLoginDetected(false);
    };

    const deleteAccount = async () => {
        if (auth.currentUser) {
            await deleteUser(auth.currentUser);
            setUser(null);
            setIdToken(null);
            setFirstThirdPartyLoginDetected(false);
        }
    };

    // Prijava putem emaila
    const loginWithEmail = async (email, password) => {
        try {
            const userCredential = await signInWithEmailAndPassword(auth, email, password);
            const token = await userCredential.user.getIdToken();
            setUser(userCredential.user);
            setIdToken(token);
            console.log("✅ Prijava putem emaila uspješna", token);
        } catch (error) {
            console.error("Greška prilikom prijave putem emaila: ", error.code);
            console.error("Greška detalji:", error.message);
        }
    };

    const registerWithEmail = async (email, password, displayName) => {
        try {
            const userCredential = await createUserWithEmailAndPassword(auth, email, password);
            console.log("display name?", displayName)
            await updateProfile(userCredential.user, {
                        displayName: displayName
                    });
            const token = await userCredential.user.getIdToken();
            console.log("USER NAME",userCredential.user)
            setUser(userCredential.user);
            setIdToken(token);
            console.log("✅ Registracija putem emaila uspješna, user:", userCredential.user);
            return userCredential;
        } catch (error) {
            console.error("❌ Greška pri registraciji:", error.code, error.message);
            throw error;
        }
    };

    // Prijava putem Google-a
    const loginWithGoogle = async () => {
        const provider = new GoogleAuthProvider();
        try {
            const result = await signInWithPopup(auth, provider);
            const token = await result.user.getIdToken();
            setUser(result.user);
            setIdToken(token);
            console.log("Prijava putem Google-a uspješna, token:", token);
            // Za prvu prijavu, onAuthStateChanged će ovo detektirati i poslati token
        } catch (error) {
            console.error("Greška pri prijavi putem Google-a:", error);
        }
    };

    // Prijava putem GitHub-a
    const loginWithGitHub = async () => {
        const provider = new GithubAuthProvider();
        try {
            const result = await signInWithPopup(auth, provider);
            const token = await result.user.getIdToken();
            setUser(result.user);
            setIdToken(token);
            console.log("Prijava putem GitHub-a uspješna, token:", token);
            // Za prvu prijavu, onAuthStateChanged će ovo detektirati i poslati token
        } catch (error) {
            console.error("Greška pri prijavi putem GitHub-a:", error);
        }
    };


    return (
        <AuthContext.Provider value={{ user, idToken, registerWithEmail, loginWithEmail, loginWithGoogle, loginWithGitHub, logout, deleteAccount }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);

// import React, { createContext, useContext, useState, useEffect } from "react";
// import { auth } from "../components/Firebase"; // Importaj auth iz Firebase
// import { createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, GoogleAuthProvider, GithubAuthProvider, signInWithPopup, signInAnonymously, deleteUser } from "firebase/auth";
//
// const AuthContext = createContext(null);
//
// export const AuthProvider = ({ children }) => {
//     const [user, setUser] = useState(null);
//     const [idToken, setIdToken] = useState(null);
//
//     useEffect(() => {
//         const unsubscribe = auth.onAuthStateChanged(async (firebaseUser) => {
//             if (firebaseUser) {
//                 const token = await firebaseUser.getIdToken();
//                 setUser(firebaseUser);
//                 setIdToken(token);
//             } else {
//                 setUser(null);
//                 setIdToken(null);
//             }
//         });
//
//         return () => unsubscribe();
//     }, []);
//
//     const logout = async () => {
//         await signOut(auth);
//         setUser(null);
//         setIdToken(null);
//     };
//
//     const deleteAccount = async () => {
//         if (auth.currentUser) {
//             await deleteUser(auth.currentUser);
//             setUser(null);
//             setIdToken(null);
//         }
//     };
//
//     // Prijava putem emaila
//     const loginWithEmail = async (email, password) => {
//         try {
//             const userCredential = await signInWithEmailAndPassword(auth, email, password);
//             const token = await userCredential.user.getIdToken();
//             setUser(userCredential.user);
//             setIdToken(token);
//             console.log("✅ Prijava uspješna", token);
//         } catch (error) {
//             console.error("Greška prilikom prijave: ", error.code); // Prikazujemo kod greške
//             console.error("Greška detalji:", error.message);
//         }
//
//     };
//     const registerWithEmail = async (email, password) => {
//         try {
//             const userCredential = await createUserWithEmailAndPassword(auth, email, password);
//             const token = await userCredential.user.getIdToken();
//             setUser(userCredential.user);
//             setIdToken(token);
//             console.log("✅ Registracija putem emaila uspješna, token:", token);
//             return userCredential;
//         } catch (error) {
//             console.error("❌ Greška pri registraciji:", error.code, error.message);
//             throw error; // Propusti grešku dalje ako je želiš obraditi iz komponente
//         }
//     };
//     // Prijava putem Google-a
//     const loginWithGoogle = async () => {
//         const provider = new GoogleAuthProvider();
//         try {
//             const result = await signInWithPopup(auth, provider);
//             const token = await result.user.getIdToken();
//             setUser(result.user);
//             setIdToken(token);
//             console.log("Prijava putem Google-a uspješna, token:", token);
//         } catch (error) {
//             console.error("Greška pri prijavi putem Google-a:", error);
//         }
//     };
//
//     // Prijava putem GitHub-a
//     const loginWithGitHub = async () => {
//         const provider = new GithubAuthProvider();
//         try {
//             const result = await signInWithPopup(auth, provider);
//             const token = await result.user.getIdToken();
//             setUser(result.user);
//             setIdToken(token);
//             console.log("Prijava putem GitHub-a uspješna, token:", token);
//         } catch (error) {
//             console.error("Greška pri prijavi putem GitHub-a:", error);
//         }
//     };
//
//     // Anonimna prijava
//     const loginAnonymously = async () => {
//         try {
//             const result = await signInAnonymously(auth);
//             const token = await result.user.getIdToken();
//             setUser(result.user);
//             setIdToken(token);
//             console.log("Anonimna prijava uspješna, token:", token);
//         } catch (error) {
//             console.error("Greška pri anonimnoj prijavi:", error);
//         }
//     };
//
//     return (
//         <AuthContext.Provider value={{ user, idToken, registerWithEmail, loginWithEmail, loginWithGoogle, loginWithGitHub, loginAnonymously, logout, deleteAccount }}>
//             {children}
//         </AuthContext.Provider>
//     );
// };
//
// export const useAuth = () => useContext(AuthContext);
