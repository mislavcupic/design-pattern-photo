// import React, { useEffect, useState } from 'react';
// import { auth } from './Firebase'; // Prilagodi putanju do Firebase konfiguracije
// import { Table, Container, Row, Col, Card, Spinner, Alert } from 'react-bootstrap';
// import UserRow from './UserRow'; // Pretpostavljam da UserRow postoji i ispravno je importan
// import './css/AdminPanel.css';
//
// const BASE_URL = 'http://localhost:8080';
//
// const AdminPanel = () => {
//     const [users, setUsers] = useState([]);
//     const [isLoading, setIsLoading] = useState(true);
//     const [error, setError] = useState(null);
//
//     const fetchWithAuth = async (url, options = {}) => {
//         try {
//             let currentUser = auth.currentUser;
//
//             if (!currentUser) {
//                 throw new Error("Korisnik nije autentificiran");
//             }
//
//             const idToken = await currentUser.getIdToken(true);
//
//             if (!idToken) {
//                 throw new Error("Neispravan ID token");
//             }
//
//             const response = await fetch(url, {
//                 ...options,
//                 headers: {
//                     ...options.headers,
//                     Authorization: `Bearer ${idToken}`,
//                 },
//             });
//
//             if (!response.ok) {
//                 const errorText = await response.text();
//                 throw new Error(`Greška: ${response.status} - ${errorText}`);
//             }
//
//             return response;
//         } catch (error) {
//             console.error("fetchWithAuth error:", error);
//             throw error;
//         }
//     };
//
//     useEffect(() => {
//         const fetchUsers = async () => {
//             setIsLoading(true);
//             setError(null);
//             try {
//                 const response = await fetchWithAuth(`${BASE_URL}/api/admin/users/all`); // Promijenjena putanja
//                 const data = await response.json();
//                 setUsers(data);
//             } catch (err) {
//                 setError(err.message);
//             } finally {
//                 setIsLoading(false);
//             }
//         };
//
//         fetchUsers();
//     }, []);
//
//     const handleUpdateUser = async (firebaseUid, newUserType, newUserPackage) => {
//         try {
//             const response = await fetchWithAuth(`${BASE_URL}/api/admin/users/update`, { // Pretpostavljeni endpoint za ažuriranje
//                 method: 'PUT',
//                 headers: {
//                     'Content-Type': 'application/json',
//                 },
//                 body: JSON.stringify({ firebaseUid, userType: newUserType, userPackage: newUserPackage }),
//             });
//
//             if (!response.ok) {
//                 const errorData = await response.json();
//                 throw new Error(`Greška: ${response.status} - ${errorData?.message || response.statusText}`);
//             }
//
//             // Nakon uspješnog ažuriranja, ponovno dohvati korisnike
//             const usersResponse = await fetchWithAuth(`${BASE_URL}/api/admin/users/all`);
//             const usersData = await usersResponse.json();
//             setUsers(usersData);
//             alert('Korisnik uspješno ažuriran.');
//
//         } catch (err) {
//             setError(err.message);
//             alert(`Greška: ${err.message}`);
//         }
//     };
//
//     if (isLoading) {
//         return (
//             <Container className="mt-5">
//                 <Row className="justify-content-center">
//                     <Col md={8} className="text-center">
//                         <Spinner animation="border" role="status">
//                             <span className="sr-only">Učitavanje korisnika...</span>
//                         </Spinner>
//                     </Col>
//                 </Row>
//             </Container>
//         );
//     }
//
//     if (error) {
//         return (
//             <Container className="mt-5">
//                 <Row className="justify-content-center">
//                     <Col md={8} className="text-center">
//                         <Alert variant="danger">{error}</Alert>
//                     </Col>
//                 </Row>
//             </Container>
//         );
//     }
//
//     return (
//         <Container className="mt-4">
//             <Row>
//                 <Col md={12}>
//                     <Card className="shadow-sm">
//                         <Card.Header className="bg-white">
//                             <Card.Title className="mb-0">Administratorski panel</Card.Title>
//                         </Card.Header>
//                         <Card.Body className="p-3">
//                             <Table striped bordered hover responsive>
//                                 <thead>
//                                 <tr>
//                                     <th>Firebase UID</th>
//                                     <th>Email</th>
//                                     <th>Tip korisnika</th>
//                                     <th>Paket</th>
//                                     <th>Akcije</th>
//                                 </tr>
//                                 </thead>
//                                 <tbody>
//                                 {users.map(user => (
//                                     <UserRow
//                                         key={user?.firebaseUid}
//                                         user={user}
//                                         onUpdateUser={handleUpdateUser}
//                                     />
//                                 ))}
//                                 </tbody>
//                             </Table>
//                             {users.length === 0 && <p className="text-muted">Nema pronađenih korisnika.</p>}
//                         </Card.Body>
//                     </Card>
//                 </Col>
//             </Row>
//         </Container>
//     );
// };
//
// export default AdminPanel;