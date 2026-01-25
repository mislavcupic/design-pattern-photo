import React, { useState, useEffect } from "react";
import { auth } from "./Firebase"; // Import Firebase auth
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Form, Container, Row, Col, Spinner, Alert, Card } from "react-bootstrap"; // Dodan Card
import { useAuth } from "../context/AuthContext"; // Importuj useAuth
import { FaSignInAlt, FaUserCircle, FaSignOutAlt, FaExclamationCircle } from "react-icons/fa"; // Dodane ikone
import './css/Login.css'; // Importiramo novi CSS

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
    }, []); // Uklonjen 'user' iz dependency arraya da se ne re-runna nepotrebno

    const handleLogin = async () => {
        setError("");
        setIsLoading(true);
        try {
            const userCredential = await signInWithEmailAndPassword(auth, email, password);
            const idToken = await userCredential.user.getIdToken();
            // 1. Spremi token (to već radiš)
            localStorage.setItem("idToken", idToken);



            // 3. Javi aplikaciji da osvježi Navbar
            window.dispatchEvent(new Event('storage'));
            const response = await fetch("http://localhost:8080/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    email: email,
                    password: password,
                    idToken: idToken,
                }),
            });

            if (response.ok) {
                const data = await response.json();
                console.log("Podaci s backenda:", data);

                // Spremi token
                localStorage.setItem("idToken", data.accessToken);

                // ISPRAVNA PUTANJA: data -> user -> userType
                if (data.user && data.user.userType) {
                    const role = data.user.userType;
                    localStorage.setItem("role", role); // Spremamo "ADMIN" pod ključ "role"
                    console.log("Uspješno spremljena uloga:", role);
                }

                // Obavijesti App.js da osvježi Navbar
                window.dispatchEvent(new Event('storage'));
                navigate("/profile");
            } else {
                const errorText = await response.text();
                setError(errorText || "Login nije uspio. Provjerite email i lozinku.");
            }
        } catch (error) {
            console.error('💥 Error during login:', error);
            setError("Greška pri prijavi: " + error.message);
        } finally {
            setIsLoading(false);
        }
    };
    const handleLogout = async () => {
        setError(""); // Clear previous errors
        setIsLoading(true); // Maybe show a spinner for logout too
        try {
            await signOut(auth);
            localStorage.removeItem("idToken");
            setUser(null);
            navigate("/login");
        } catch (error) {
            console.error('💥 Error during logout:', error);
            setError("Greška pri odjavi: " + error.message);
        } finally {
            setIsLoading(false); // Hide spinner
        }
    };

    // const handleAnonymousLogin = async () => {
    //     try {
    //         setIsLoading(true);
    //         await loginAnonymously();
    //         navigate("/profile");
    //     } catch (error) {
    //         setError("Greška pri anonimnoj prijavi: " + error.message);
    //         console.error("Greška pri anonimnoj prijavi:", error);
    //     } finally {
    //         setIsLoading(false);
    //     }
    // };

    return (
        <Container fluid className="login-container">
            {/* Dodana klasa 'flex-grow-1' za Row */}
            <Row className="justify-content-center align-items-center h-100 flex-grow-1">
                <Col xs={10} sm={8} md={6} lg={4}> {/* Prilagođene veličine kolone za responsivnost */}
                    <Card className="login-card shadow-lg border-0">
                        <Card.Body className="p-4 p-md-5">
                            <Card.Title className="text-center mb-4 login-title">
                                <FaSignInAlt className="me-2 text-primary" /> Prijava
                            </Card.Title>

                            {error && (
                                <Alert variant="danger" className="shake-animation login-alert">
                                    <FaExclamationCircle className="me-2" />{error}
                                </Alert>
                            )}

                            {user ? (
                                <div className="text-center logged-in-state">
                                    <p className="welcome-text">
                                        <FaUserCircle className="me-2 text-primary" />Dobrodošli, <span className="user-email-display">{user.email}</span>!
                                    </p>
                                    <Button variant="outline-danger" onClick={handleLogout} className="w-100 logout-btn" disabled={isLoading}>
                                        {isLoading ? (
                                            <>
                                                <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                                Odjavljujem se...
                                            </>
                                        ) : (
                                            <>
                                                <FaSignOutAlt className="me-2" /> Odjava
                                            </>
                                        )}
                                    </Button>
                                </div>
                            ) : (
                                <Form>
                                    <Form.Group controlId="email" className="mb-3">
                                        <Form.Label className="form-label-custom">Email</Form.Label>
                                        <Form.Control
                                            type="email"
                                            placeholder="vas.email@primjer.com"
                                            value={email}
                                            onChange={(e) => setEmail(e.target.value)}
                                            className="form-control-custom"
                                            disabled={isLoading}
                                        />
                                    </Form.Group>

                                    <Form.Group controlId="password" className="mb-4">
                                        <Form.Label className="form-label-custom">Lozinka</Form.Label>
                                        <Form.Control
                                            type="password"
                                            placeholder="••••••••"
                                            value={password}
                                            onChange={(e) => setPassword(e.target.value)}
                                            className="form-control-custom"
                                            disabled={isLoading}
                                        />
                                    </Form.Group>

                                    <Button
                                        variant="outline-primary"
                                        onClick={handleLogin}
                                        className="w-100 login-btn"
                                        disabled={isLoading || !email || !password}
                                    >
                                        {isLoading ? (
                                            <>
                                                <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                                Prijavljujem se...
                                            </>
                                        ) : (
                                            <>
                                                <FaSignInAlt className="me-2" /> Prijava
                                            </>
                                        )}
                                    </Button>
                                </Form>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}

export default Login;