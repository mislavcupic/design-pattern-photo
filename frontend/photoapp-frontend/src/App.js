import React from 'react';
import { Routes, Route, Link, useNavigate } from 'react-router-dom';
import { Navbar, Nav, Container } from 'react-bootstrap';
import ProfilePage from './components/ProfilePage';
import Login from './components/Login';
import Register from './components/Register';
import Upload from './components/Upload';
import DeleteAccount from "./components/DeleteAccount";
import Logout from "./components/Logout";
import './App.css';

import PhotoUploadForm from "./components/PhotoUploadForm"; // Dodatni CSS za specifične stilove

function App() {
    const navigate = useNavigate();

    return (
        <>
            {/* Navigacijski meni */}
            <Navbar bg="dark" variant="dark" expand="lg" className="shadow-sm">
                <Container>
                    <Navbar.Brand as={Link} to="/">Photo Upload App</Navbar.Brand>
                    <Navbar.Toggle aria-controls="basic-navbar-nav" />
                    <Navbar.Collapse id="basic-navbar-nav" className="justify-content-end">
                        <Nav className="ml-auto">
                            <Nav.Item>
                                <Nav.Link as={Link} to="/profile">Profil</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link as={Link} to="/photouploadform">Upload</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link as={Link} to="/login">Prijava</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link as={Link} to="/register">Registracija</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link as={Link} to="/delete-account">Obriši račun</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link as={Link} to="/logout">Odjava</Nav.Link>
                            </Nav.Item>
                        </Nav>
                    </Navbar.Collapse>
                </Container>
            </Navbar>

            {/* Routing: Definiramo koje komponente se prikazuju za svaku rutu */}
            <Container className="mt-4">
                <Routes>
                    <Route path="/" element={<div className="text-center"><h1>Dobrodošli u aplikaciju za upload fotografija</h1><p className="lead">Pregledajte profil, uploadajte nove fotografije i upravljajte svojim računom.</p></div>} />
                    <Route path="/profile" element={<ProfilePage />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    <Route path="/photouploadform" element={<PhotoUploadForm />} />
                    <Route path="/delete-account" element={<DeleteAccount />} />
                    <Route path="/logout" element={<Logout />} />
                    {/*<Route path="/admin" element={<AdminPanel/>} />*/}
                    {/*<Route path="/user-row" element={<UserRow/>} />*/}

                </Routes>
            </Container>
        </>
    );
}

export default App;