import React, { useState, useEffect } from "react";
import { getAuth, deleteUser } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Container, Row, Col, Card, Alert, Spinner } from "react-bootstrap"; // Importiranje React-Bootstrap komponenata
import { FaTrashAlt, FaExclamationTriangle, FaUserCircle, FaCheckCircle, FaInfoCircle } from "react-icons/fa"; // Dodane ikone, uključujući FaInfoCircle
import './css/DeleteAccount.css'; // Importiramo novi CSS

export default function DeleteAccount() {
    const auth = getAuth();
    const navigate = useNavigate();
    const [user, setUser] = useState(auth.currentUser); // Koristimo state za usera
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ type: "", text: "" }); // Za poruke o uspjehu/grešci

    useEffect(() => {
        // Osluškujemo promjene stanja autentifikacije
        const unsubscribe = auth.onAuthStateChanged(currentUser => {
            setUser(currentUser);
            // Ako korisnik nije logiran, preusmjeri ga na login ili register
            if (!currentUser && !isLoading) { // Provjeri isLoading da se ne preusmjeri odmah nakon brisanja
                navigate("/auth/login"); // Možeš i na "/auth/register" po tvojoj logici
            }
        });
        return () => unsubscribe();
    }, [auth, navigate, isLoading]);

    const handleDelete = async () => {
        setMessage({ type: "", text: "" }); // Resetiraj poruke
        setIsLoading(true);

        const confirmed = window.confirm("Jeste li sigurni da želite trajno izbrisati svoj račun? Ova radnja se ne može poništiti.");
        if (!confirmed) {
            setIsLoading(false);
            return;
        }

        try {
            if (user) {
                // Firebase zahtijeva nedavnu prijavu za brisanje korisnika.
                // Ako je prošlo previše vremena, `deleteUser` će baciti grešku `auth/requires-recent-login`.
                // U tom slučaju korisnika treba ponovno autentificirati (re-authenticate).
                // Za jednostavnost, ovdje to ne radimo eksplicitno, ali je važno znati.

                const idToken = await user.getIdToken(true); // Osvježi ID token

                // 1. Pošaljite zahtjev backendu
                if (idToken) {
                    await fetch("http://localhost:8080/auth/delete", {
                        method: "DELETE",
                        headers: {
                            Authorization: `Bearer ${idToken}`,
                        },
                    });
                }

                // 2. Izbriši korisnika s frontenda (Firebase klijent)
                await deleteUser(user);

                // 3. Očisti localStorage i sessionStorage
                localStorage.clear();
                sessionStorage.clear();

                setMessage({ type: "success", text: "Račun je uspješno izbrisan. Preusmjeravam..." });
                setTimeout(() => {
                    navigate("/auth/register"); // Preusmjeri korisnika na register.js
                }, 2000); // Kratka odgoda da korisnik vidi poruku

            } else {
                setMessage({ type: "warning", text: "Niste prijavljeni. Ne možete izbrisati račun." });
                setTimeout(() => {
                    navigate("/auth/login");
                }, 2000);
            }
        } catch (error) {
            console.error("Greška kod brisanja računa:", error);
            let errorMessageText = "Došlo je do greške prilikom brisanja računa.";
            if (error.code === 'auth/requires-recent-login') {
                errorMessageText = "Molimo ponovno se prijavite da biste izbrisali račun (iz sigurnosnih razloga).";
            } else if (error.message.includes("Backend greška")) {
                errorMessageText = error.message; // Prikazuje backend grešku
            } else {
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
                                disabled={isLoading || !user} // Onemogući gumb ako nema korisnika ili se učitava
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