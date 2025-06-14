import React from "react";
import { getAuth, deleteUser } from "firebase/auth";
import { useNavigate } from "react-router-dom";
import { Button, Container, Row, Col, Card } from "react-bootstrap"; // Importiranje React-Bootstrap komponenata

export default function DeleteAccount() {
    const auth = getAuth();
    const navigate = useNavigate();
    const user = auth.currentUser;

    const handleDelete = async () => {
        const confirmed = window.confirm("Are you sure you want to delete your account?");
        if (!confirmed) return;

        try {
            if (user) {
                const idToken = await user.getIdToken(true);  // Refresh ID token

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
            }

            // 3. Očisti localStorage i sessionStorage
            localStorage.clear();
            sessionStorage.clear();

            // 4. Preusmjeri korisnika na register.js
            navigate("/auth/register");

        } catch (error) {
            console.error("Greška kod brisanja računa:", error);
            alert("Greška kod brisanja računa: " + error.message);
        }
    };

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col xs={12} md={6}>
                    <Card>
                        <Card.Body>
                            {user ? (
                                <>
                                    <h5 className="mb-3">Logged in as:</h5>
                                    <p><strong>Name:</strong> {user.displayName || "N/A"}</p>
                                    <p><strong>Email:</strong> {user.email || "N/A"}</p>
                                </>
                            ) : (
                                <p>User not logged in</p>
                            )}
                            <Button
                                variant="danger"
                                size="lg"
                                block
                                onClick={handleDelete}
                            >
                                Delete Account
                            </Button>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}
