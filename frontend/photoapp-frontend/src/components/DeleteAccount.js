import React, { useState, useEffect } from "react";
import { getAuth, signOut } from "firebase/auth"; // Uklanjamo deleteUser jer to radi backend
import { useNavigate } from "react-router-dom";
import { Button, Container, Row, Col, Card, Alert, Spinner } from "react-bootstrap";
import { FaTrashAlt, FaExclamationTriangle, FaUserCircle, FaCheckCircle, FaInfoCircle } from "react-icons/fa";
import './css/DeleteAccount.css';

export default function DeleteAccount() {
    const auth = getAuth();
    const navigate = useNavigate();
    const [user, setUser] = useState(auth.currentUser);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ type: "", text: "" });

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(currentUser => {
            setUser(currentUser);
            if (!currentUser && !isLoading) {
                // Ako korisnik nije logiran i nije u procesu brisanja, preusmjeri
                // Ako je currentUser null NAKON USPJEŠNOG brisanja, onda setTimeout radi redirect.
                // Ovaj provjera osigurava da ne preusmjerimo prerano.
                // U ovom scenariju, bolje je pustiti setTimeout da odradi redirect nakon uspjeha.
            }
        });
        return () => unsubscribe();
    }, [auth, navigate, isLoading]);

    const handleDelete = async () => {
        setMessage({ type: "", text: "" });
        setIsLoading(true);

        const confirmed = window.confirm("Jeste li sigurni da želite trajno izbrisati svoj račun? Ova radnja se ne može poništiti.");
        if (!confirmed) {
            setIsLoading(false);
            return;
        }

        try {
            if (user) {
                const idToken = await user.getIdToken(true); // Osvježi ID token

                if (!idToken) { // Dodatna provjera za idToken
                    setMessage({ type: "danger", text: "Nema važećeg tokena. Molimo ponovno se prijavite." });
                    setIsLoading(false);
                    // Možda i redirect na login
                    setTimeout(() => navigate("/auth/login"), 2000);
                    return;
                }

                // 1. Pošaljite zahtjev backendu - OVO JE SADA JEDINI POZIV ZA BRISANJE KORISNIKA
                const response = await fetch("http://localhost:8080/auth/delete", {
                    method: "DELETE",
                    headers: {
                        Authorization: `Bearer ${idToken}`,
                        'Content-Type': 'application/json' // Dodaj Content-Type ako backend očekuje
                    },
                });

                if (!response.ok) {
                    // Ako backend vrati grešku (npr. 401 Unauthorized, 403 Forbidden, 500 Internal Server Error)
                    const errorData = await response.json();
                    let backendErrorMessage = errorData.message || "Nepoznata backend greška.";
                    // Možete dodati i provjeru status koda ako želite specifične poruke
                    if (response.status === 401) {
                        backendErrorMessage = "Autorizacija neuspješna. Molimo prijavite se ponovno.";
                        // Odjavite korisnika na frontendu
                        await signOut(auth);
                        navigate("/auth/login");
                        return; // Prekini daljnje izvršavanje
                    }
                    throw new Error(`Backend greška: ${backendErrorMessage}`);
                }

                // Ako je backend uspješno obrisao korisnika iz Firebase Auth,
                // `user` objekt na frontendu postaje nevažeći.
                // Nema potrebe zvati `deleteUser(user)`! Backend je to već obavio.

                // Očisti lokalno stanje autentifikacije (tokene, cache)
                await signOut(auth); // Ovo će odjaviti korisnika na klijentskoj strani

                // Očisti localStorage i sessionStorage
                localStorage.clear();
                sessionStorage.clear();

                setMessage({ type: "success", text: "Račun je uspješno izbrisan. Preusmjeravam..." });
                setTimeout(() => {
                    navigate("../register"); // Preusmjeri korisnika
                }, 2000);

            } else {
                setMessage({ type: "warning", text: "Niste prijavljeni. Preusmjeravam vas na stranicu za prijavu." });
                setTimeout(() => {
                    navigate("/auth/login");
                }, 2000);
            }
        } catch (error) {
            console.error("Greška kod brisanja računa:", error);
            let errorMessageText = "Došlo je do greške prilikom brisanja računa.";

            if (error.message.includes("Backend greška")) {
                errorMessageText = error.message; // Prikazuje poruku iz backenda
            } else if (error.code === 'auth/requires-recent-login') {
                // Iako backend obavlja brisanje, ova greška bi se mogla pojaviti
                // ako idToken postane nevažeći PRIJE poziva backenda
                errorMessageText = "Molimo ponovno se prijavite da biste izbrisali račun (iz sigurnosnih razloga).";
                await signOut(auth); // Odjavite korisnika na frontendu
                setTimeout(() => navigate("/auth/login"), 2000);
            } else if (error.code === 'auth/network-request-failed') {
                errorMessageText = "Problem s mrežnom vezom. Provjerite svoju internetsku vezu.";
            }
            else {
                errorMessageText = `Greška: ${error.message}`;
            }
            setMessage({ type: "danger", text: errorMessageText });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Container fluid className="delete-account-container">
            <Row className="justify-content-center align-items-center h-100 flex-grow-1">
                <Col xs={10} sm={8} md={6} lg={4}>
                    <Card className="delete-account-card shadow-lg border-0 text-center">
                        <Card.Body className="p-4 p-md-5">
                            <Card.Title className="text-center mb-4 delete-title">
                                <FaTrashAlt className="me-2 text-danger" /> Brisanje računa
                            </Card.Title>

                            {message.text && (
                                <Alert variant={message.type} className={`mb-4 delete-alert ${message.type === 'danger' ? 'shake-animation' : ''}`}>
                                    {message.type === 'danger' ? <FaExclamationTriangle className="me-2" /> : <FaCheckCircle className="me-2" />}
                                    {message.text}
                                </Alert>
                            )}

                            {user ? (
                                <div className="user-info-section mb-4">
                                    <p className="logged-in-text">
                                        <FaUserCircle className="me-2 text-primary" /> Prijavljeni ste kao:
                                    </p>
                                    <p className="user-detail-line">
                                        <span className="detail-label">Ime:</span> <span className="detail-value">{user.displayName || "N/A"}</span>
                                    </p>
                                    <p className="user-detail-line">
                                        <span className="detail-label">Email:</span> <span className="detail-value">{user.email || "N/A"}</span>
                                    </p>
                                    <hr className="divider"/>
                                    <p className="warning-text">
                                        Ova radnja je trajna i ne može se poništiti. Svi vaši podaci bit će izbrisani.
                                    </p>
                                </div>
                            ) : (
                                <Alert variant="info" className="delete-alert">
                                    <FaInfoCircle className="me-2" /> Niste prijavljeni. Preusmjeravam vas na stranicu za prijavu.
                                </Alert>
                            )}

                            <Button
                                variant="outline-danger"
                                onClick={handleDelete}
                                className="w-100 delete-btn"
                                disabled={isLoading || !user}
                            >
                                {isLoading ? (
                                    <>
                                        <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2" />
                                        Brisanje računa...
                                    </>
                                ) : (
                                    <>
                                        <FaTrashAlt className="me-2" /> Izbriši račun
                                    </>
                                )}
                            </Button>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}