import React, { useState, useEffect } from "react";
import { getAuth, onAuthStateChanged, signOut } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Spinner, Container, Alert, Card, Row, Col } from "react-bootstrap";  // Importiraj Bootstrap komponente
import { FaSignOutAlt, FaInfoCircle } from "react-icons/fa"; // Dodane ikone
import './css/Logout.css'; // Importiramo novi CSS

function Logout() {
    const [loading, setLoading] = useState(true);
    const [user, setUser] = useState(null);
    const [error, setError] = useState(null); // State za greške
    const navigate = useNavigate();
    const auth = getAuth();

    useEffect(() => {
        const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
            if (currentUser) {
                setUser(currentUser);
                setLoading(false);
            } else {
                setUser(null);
                setLoading(false);
                navigate("/login"); // Preusmjeri na login stranicu ako nema korisnika
            }
        });

        return () => unsubscribe();
    }, [auth, navigate]);

    const handleLogout = async () => {
        setError(null); // Resetiraj greške
        setLoading(true); // Prikaži spinner dok se odjavljuje
        try {
            if (user) {
                const idToken = await user.getIdToken();

                const response = await fetch("http://localhost:8080/auth/logout", {
                    method: "POST",
                    headers: {
                        Authorization: `Bearer ${idToken}`,
                    },
                });

                if (response.ok) {
                    console.log("Odjava uspješna na backendu.");
                    await signOut(auth); // Odjava iz Firebasea
                    navigate("/login");
                } else {
                    const errorText = await response.text();
                    console.error("Greška pri odjavi na backendu:", errorText);
                    setError(`Greška pri odjavi: ${errorText || 'Nepoznata greška'}`);
                    setLoading(false); // Prestani s učitavanjem ako je greška
                }
            } else {
                // Ako korisnik nije prijavljen, odmah ga preusmjeravamo
                navigate("/login");
            }
        } catch (error) {
            console.error("Greška pri odjavi:", error);
            setError(`Greška pri odjavi: ${error.message}`);
            setLoading(false); // Prestani s učitavanjem ako je greška
        }
    };

    if (loading) {
        return (
            <Container fluid className="logout-container loading-state">
                <Spinner animation="border" role="status" className="logout-spinner" />
                <p className="loading-text">Provjeravam status prijave...</p>
            </Container>
        );
    }

    return (
        <Container fluid className="logout-container">
            <Row className="justify-content-center align-items-center h-100 flex-grow-1">
                <Col md={6} lg={4}>
                    <Card className="logout-card shadow-lg border-0 text-center">
                        <Card.Body className="p-4 p-md-5">
                            {error && (
                                <Alert variant="danger" className="shake-animation logout-alert mb-4">
                                    <FaInfoCircle className="me-2" />{error}
                                </Alert>
                            )}

                            {user ? (
                                <>
                                    <h4 className="logout-message mb-4">Jeste li sigurni da se želite odjaviti?</h4>
                                    <Button
                                        variant="outline-danger"
                                        onClick={handleLogout}
                                        className="w-100 logout-btn"
                                        disabled={loading}
                                    >
                                        {loading ? (
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
                                </>
                            ) : (
                                <Alert variant="info" className="logout-alert">
                                    <FaInfoCircle className="me-2" />Niste prijavljeni. Preusmjeravam vas na stranicu za prijavu.
                                </Alert>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}

export default Logout;