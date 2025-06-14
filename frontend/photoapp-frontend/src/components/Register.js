import React, { useState } from "react";
import { useAuth } from "../context/AuthContext"; // Importaj AuthContext da bi koristio prijavu
import { Form, Button, Container, Row, Col, Alert } from "react-bootstrap";
import { getAuth, fetchSignInMethodsForEmail } from "firebase/auth"; // Firebase auth za provjeru korisnika

function Register() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [userPackage, setUserPackage] = useState("FREE");
    const [errorMessage, setErrorMessage] = useState(""); // Za pohranu poruka o grešci

    const { registerWithEmail, loginWithGoogle, loginWithGitHub } = useAuth(); // Importaj funkcije iz AuthContext-a

    const handleRegister = async () => {
        setErrorMessage("");

        try {
            const auth = getAuth();
            const signInMethods = await fetchSignInMethodsForEmail(auth, email);

            if (signInMethods.length > 0) {
                setErrorMessage("Korisnik s tim emailom već postoji. Molimo prijavite se ili povežite račun.");
                return;
            }

            await registerWithEmail(email, password, displayName);
            console.log("✅ Registracija uspješna");

            // TODO: Ako želiš poslati `displayName` i `userPackage` na svoj backend, ovdje to napravi.

        } catch (error) {
            console.error("❌ Greška pri registraciji:", error);
            setErrorMessage("Došlo je do greške pri registraciji. Molimo pokušajte ponovno.");
        }
    };

    const handleGoogleLogin = () => {
        // Prijava putem Google-a
        loginWithGoogle();
    };

    const handleGitHubLogin = () => {
        // Prijava putem GitHub-a
        loginWithGitHub();
    };

    return (
        <Container>
            <Row className="justify-content-md-center" style={{ marginTop: "50px" }}>
                <Col md={6}>
                    <h2 className="text-center">Registration on the PhotoApp</h2>

                    {errorMessage && (
                        <Alert variant="danger" className="mb-3">
                            {errorMessage}
                        </Alert>
                    )}

                    <Form>
                        {/* Display Name */}
                        <Form.Group controlId="formDisplayName">
                            <Form.Label>Ime</Form.Label>
                            <Form.Control
                                type="text"
                                placeholder="Unesite svoje ime"
                                value={displayName}
                                onChange={(e) => setDisplayName(e.target.value)}
                            />
                        </Form.Group>

                        {/* Email */}
                        <Form.Group controlId="formEmail">
                            <Form.Label>Email</Form.Label>
                            <Form.Control
                                type="email"
                                placeholder="Unesite svoj email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                            />
                        </Form.Group>

                        {/* Password */}
                        <Form.Group controlId="formPassword">
                            <Form.Label>Lozinka</Form.Label>
                            <Form.Control
                                type="password"
                                placeholder="Unesite svoju lozinku"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                            />
                        </Form.Group>

                        {/* User Package */}
                        <Form.Group controlId="formUserPackage">
                            <Form.Label>Odaberi paket</Form.Label>
                            <Form.Control
                                as="select"
                                value={userPackage}
                                onChange={(e) => setUserPackage(e.target.value)}
                            >
                                <option value="FREE">FREE</option>
                                <option value="PRO">PRO</option>
                                <option value="GOLD">GOLD</option>
                            </Form.Control>
                        </Form.Group>

                        {/* Register Button */}
                        <Button variant="primary" onClick={handleRegister} style={{ width: "100%" }}>
                            Registration via email
                        </Button>
                    </Form>

                    {/* Social Login Buttons */}
                    <div className="mt-3">
                        <Button variant="outline-danger" onClick={handleGoogleLogin} style={{ width: "100%", marginBottom: "10px" }}>
                            Registration via Google
                        </Button>
                        <Button variant="outline-dark" onClick={handleGitHubLogin} style={{ width: "100%" }}>
                            Registracija via Github
                        </Button>
                    </div>
                </Col>
            </Row>
        </Container>
    );
}

export default Register;
