import React, { useState, useEffect } from "react";
import { getAuth, onAuthStateChanged, signOut } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Spinner, Container } from "react-bootstrap";  // Importiraj Bootstrap komponente

function Logout() {
    const [loading, setLoading] = useState(true);
    const [user, setUser] = useState(null);
    const navigate = useNavigate();
    const auth = getAuth();

    useEffect(() => {
        // Provjera statusa prijave korisnika
        const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
            if (currentUser) {
                setUser(currentUser);  // Postavi korisnika ako je prijavljen
                setLoading(false);  // Završavamo učitavanje
            } else {
                setUser(null);  // Ako nije prijavljen, postavi user na null
                setLoading(false);  // Završavamo učitavanje
                navigate("/login");  // Preusmjeri na login stranicu
            }
        });

        return () => unsubscribe();  // Očisti listener kada komponenta bude unmounted
    }, [auth, navigate]);

    const handleLogout = async () => {
        if (user) {
            try {
                // Ako postoji prijavljeni korisnik, pošaljemo zahtjev backendu za odjavu
                const idToken = await user.getIdToken();

                const response = await fetch("http://localhost:8080/auth/logout", {
                    method: "POST",
                    headers: {
                        Authorization: `Bearer ${idToken}`,  // Pošaljemo token za autentifikaciju
                    },
                });

                if (response.ok) {
                    console.log("Odjava uspješna!");
                    // Nakon uspješne odjave na backendu, odjavljujemo korisnika iz Firebasea
                    await signOut(auth);
                    navigate("/login");  // Preusmjeri na login nakon uspješne odjave
                } else {
                    console.error("Greška pri odjavi na backendu.");
                    alert("Došlo je do greške pri odjavi.");
                }
            } catch (error) {
                console.error("Greška pri odjavi:", error);
                alert("Greška pri odjavi.");
            }
        } else {
            // Ako korisnik nije prijavljen, odmah ga preusmjeravamo na login
            navigate("/login");
        }
    };

    // Prikazujemo Loading dok se provodi provjera prijavljenog korisnika
    if (loading) {
        return (
            <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: "100vh" }}>
                <Spinner animation="border" variant="primary" />
            </Container>
        );
    }

    return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: "100vh" }}>
            <Button variant="danger" onClick={handleLogout}>
                Logout
            </Button>
        </Container>
    );
}

export default Logout;
