import React from 'react';
import { Routes, Route, Link, Navigate } from 'react-router-dom';
import { Navbar, Nav, Container } from 'react-bootstrap';
import { useAuth } from './context/AuthContext'; // DODANO: Da može čitati stvarnu ulogu
import ProfilePage from './components/ProfilePage';
import Login from './components/Login';
import Register from './components/Register';
import DeleteAccount from "./components/DeleteAccount";
import Logout from "./components/Logout";
import AdminDashboard from "./components/AdminDashboard";
import HomePage from "./components/HomePage";
import UserRow from "./components/UserRow";
import './App.css';

function App() {
    const { user, loading } = useAuth(); // Koristimo tvoj AuthContext

    // Čekamo da se uloga učita iz baze da te ne izbaci greškom
    if (loading) return null;

    // Uzimamo ulogu iz objekta koji tvoj AuthContext puni iz Firestore-a
    const userRole = user?.role || localStorage.getItem('role');

    return (
        <>
            <Navbar bg="dark" variant="dark" expand="lg" className="shadow-sm">
                <Container>
                    <Navbar.Brand as={Link} to="/">Naslovna</Navbar.Brand>
                    <Navbar.Toggle aria-controls="basic-navbar-nav" />
                    <Navbar.Collapse id="basic-navbar-nav" className="justify-content-end">
                        <Nav className="ml-auto">
                            <Nav.Item>
                                <Nav.Link as={Link} to="/profile">Profil</Nav.Link>
                            </Nav.Item>

                            {userRole === 'ADMIN' && (
                                <Nav.Item>
                                    <Nav.Link as={Link} to="/admin" className="text-warning fw-bold">Admin Panel</Nav.Link>
                                </Nav.Item>
                            )}

                            {!user ? (
                                <>
                                    <Nav.Item><Nav.Link as={Link} to="/login">Prijava</Nav.Link></Nav.Item>
                                    <Nav.Item><Nav.Link as={Link} to="/register">Registracija</Nav.Link></Nav.Item>
                                </>
                            ) : (
                                <>
                                    <Nav.Item><Nav.Link as={Link} to="/delete-account">Obriši račun</Nav.Link></Nav.Item>
                                    <Nav.Item><Nav.Link as={Link} to="/logout">Odjava</Nav.Link></Nav.Item>
                                </>
                            )}
                        </Nav>
                    </Navbar.Collapse>
                </Container>
            </Navbar>

            <Container className="mt-4">
                <Routes>
                    <Route path="/" element={<HomePage />} />
                    <Route path="/profile" element={<ProfilePage />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    <Route path="/delete-account" element={<DeleteAccount />} />
                    <Route path="/logout" element={<Logout />} />

                    <Route
                        path="/admin"
                        element={userRole === 'ADMIN' ? <AdminDashboard /> : <Navigate to="/" replace />}
                    />
                    <Route path="/user-row" element={<UserRow />} />
                </Routes>
            </Container>
        </>
    );
}

export default App;