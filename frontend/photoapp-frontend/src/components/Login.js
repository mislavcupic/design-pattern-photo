import React, { useState, useEffect } from "react";
import { auth } from "./Firebase"; // Import Firebase auth
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Form, Container, Row, Col, Spinner, Alert } from "react-bootstrap";
import { useAuth } from "../context/AuthContext"; // Importuj useAuth

function Login() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [user, setUser] = useState(null);  // Skladištimo korisnika
    const [isLoading, setIsLoading] = useState(false);  // Za kontrolu učitavanja
    const [error, setError] = useState(""); // Za praćenje grešaka
    const navigate = useNavigate();
   // const { loginAnonymously } = useAuth();  // Uzimamo loginAnonymously iz konteksta

    useEffect(() => {
        // Provjera prijavljenog korisnika pri učitavanju stranice
        const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
            if (currentUser) {
                setUser(currentUser);  // Ako je korisnik prijavljen, postavljamo ga u state
            } else {
                setUser(null);  // Ako korisnik nije prijavljen, postavljamo null
            }
        });

        return () => unsubscribe();  // Očisti listener pri unmountu komponente
    }, [user]);

    const handleLogin = async () => {
        setIsLoading(true);
        try {
            // Prijava korisnika putem Firebase auth
            const userCredential = await signInWithEmailAndPassword(auth, email, password);

            // Dohvati ID token nakon uspješne prijave
            const idToken = await userCredential.user.getIdToken();

            // Pohrani ID token u localStorage
            localStorage.setItem("idToken", idToken);
            console.log("✅ Token:", idToken);

            // Pošaljite login podatke prema backendu
            const response = await fetch("http://localhost:8080/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    email: email,
                    password: password,
                    idToken: idToken, // Važno: šaljemo ID token
                }),
            });

            if (response.ok) {
                const data = await response.json();
                console.log('✅ Login successful:', data);
                alert("Prijavljen!");
                navigate("/profile");  // Preusmjeri korisnika na profilnu stranicu
            } else {
                const errorData = await response.text();
                console.error('❌ Login failed:', errorData);
                alert("Login nije uspio");
            }
        } catch (error) {
            console.error('💥 Error during login:', error);
            alert("Greška pri prijavi");
        } finally {
            setIsLoading(false);
        }
    };

    const handleLogout = async () => {
        try {
            // Izvršavamo signOut iz Firebase-a
            await signOut(auth);
            localStorage.removeItem("idToken");  // Uklonimo token iz localStorage
            setUser(null);  // Resetiraj korisnika
            alert("Odjavljen!");
            navigate("/login");  // Preusmjeri na login stranicu
        } catch (error) {
            console.error('💥 Error during logout:', error);
            alert("Greška pri odjavi");
        }
    };

    // const handleAnonymousLogin = async () => {
    //     try {
    //         setIsLoading(true);
    //         await loginAnonymously();  // Pozivanje anonimne prijave iz konteksta
    //         navigate("/profile");  // Preusmjeri korisnika na profilnu stranicu
    //     } catch (error) {
    //         setError("Greška pri anonimnoj prijavi: " + error.message);
    //         console.error("Greška pri anonimnoj prijavi:", error);
    //     } finally {
    //         setIsLoading(false);
    //     }
    // };

    return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: "100vh" }}>
            <Row className="w-100">
                <Col md={6} lg={4} className="mx-auto">
                    <div className="text-center mb-4">
                        <h2>Prijava</h2>
                    </div>

                    {error && <Alert variant="danger">{error}</Alert>} {/* Prikazivanje greške ako postoji */}

                    {user ? (
                        <div className="text-center">
                            <p>Dobrodošli, {user.displayName || user.email}!</p>
                            <Button variant="danger" onClick={handleLogout}>Odjava</Button>
                        </div>
                    ) : (
                        <div>
                            <Form>
                                <Form.Group controlId="email" className="mb-3">
                                    <Form.Label>Email</Form.Label>
                                    <Form.Control
                                        type="email"
                                        placeholder="Unesite email"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                    />
                                </Form.Group>

                                <Form.Group controlId="password" className="mb-3">
                                    <Form.Label>Lozinka</Form.Label>
                                    <Form.Control
                                        type="password"
                                        placeholder="Unesite lozinku"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                    />
                                </Form.Group>

                                <Button
                                    variant="primary"
                                    onClick={handleLogin}
                                    style={{ width: "100%" }}
                                    disabled={isLoading}
                                >
                                    {isLoading ? (
                                        <Spinner animation="border" size="sm" />
                                    ) : (
                                        "Prijava"
                                    )}
                                </Button>
                            </Form>

                            {/*<div className="mt-3 text-center">*/}
                            {/*    <Button*/}
                            {/*        variant="secondary"*/}
                            {/*        onClick={handleAnonymousLogin}*/}
                            {/*        style={{ width: "100%" }}*/}
                            {/*        disabled={isLoading}*/}
                            {/*    >*/}
                            {/*        {isLoading ? (*/}
                            {/*            <Spinner animation="border" size="sm" />*/}
                            {/*        ) : (*/}
                            {/*            "Anonimna prijava"*/}
                            {/*        )}*/}
                            {/*    </Button>*/}
                            {/*</div>*/}
                        </div>
                    )}
                </Col>
            </Row>
        </Container>
    );
}

export default Login;
