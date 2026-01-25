import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Spinner, Alert, Badge, Card } from 'react-bootstrap';
import AdminService from '../service/AdminService';
import { useAuth } from '../context/AuthContext'; // DODANO: Da izvučemo user i loading
import './css/AdminDashboard.css';

const AdminDashboard = () => {
    // Izvlačimo user i loading stanje iz tvog AuthContexta
    const { user, loading: authLoading } = useAuth();

    const [users, setUsers] = useState([]);
    const [userPackages, setUserPackages] = useState({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchData = useCallback(async () => {
        // PROVJERA: Nemoj uopće pokretati fetch ako podaci o korisniku još nisu stigli
        if (!user) return;

        setLoading(true);
        setError(null);
        try {
            const usersData = await AdminService.getAllUsers();
            const packagesData = await AdminService.getAllUserPackages();

            setUsers(usersData);

            const pkgMap = {};
            packagesData.forEach(pkg => {
                const uid = pkg.firebaseUid || pkg.uid || pkg.userUid;
                pkgMap[uid] = pkg;
            });
            setUserPackages(pkgMap);
        } catch (err) {
            console.error("Greška u Dashboardu:", err);
            setError("Neuspjelo dohvaćanje podataka. Provjerite jeste li admin.");
        } finally {
            setLoading(false);
        }
    }, [user]); // Reagira na promjenu 'user' objekta

    useEffect(() => {
        // Pokrećemo fetch samo kada AuthContext javi da više nije 'loading'
        if (!authLoading && user) {
            fetchData();
        }
    }, [authLoading, user, fetchData]);

    const handleRoleChange = async (uid, currentRole) => {
        const newRole = currentRole === 'ADMIN' ? 'REGISTERED' : 'ADMIN';
        if (!window.confirm(`Želite li promijeniti ulogu korisniku u ${newRole}?`)) return;

        try {
            await AdminService.updateUserRole(uid, newRole);
            alert("Uloga uspješno promijenjena!");
            fetchData();
        } catch (err) {
            if (err.message.includes("JSON")) {
                fetchData();
            } else {
                alert("Greška pri promjeni uloge: " + err.message);
            }
        }
    };

    // PRVA LINIJA OBRANE: Ako se autentifikacija još učitava, stani ovdje
    if (authLoading) {
        return <div className="text-center mt-5"><Spinner animation="border" variant="primary" /></div>;
    }

    if (error) return <Alert variant="danger" className="mt-3">{error}</Alert>;

    return (
        <Card className="shadow-sm mt-4">
            <Card.Header as="h5" className="bg-primary text-white d-flex justify-content-between align-items-center">
                Upravljanje Korisnicima
                <Button variant="light" size="sm" onClick={fetchData}>Osvježi podatke</Button>
            </Card.Header>
            <Card.Body>
                <Table striped bordered hover responsive>
                    <thead>
                    <tr>
                        <th>Email</th>
                        <th>UID</th>
                        <th>Uloga</th>
                        <th>Paket</th>
                        <th>Uploads</th>
                        <th>Akcije</th>
                    </tr>
                    </thead>
                    <tbody>
                    {users.map((u) => {
                        const uid = u.firebaseUid || u.uid;
                        const pkg = userPackages[uid];
                        const currentRole = u.userType || u.role || 'REGISTERED';

                        return (
                            <tr key={uid}>
                                <td>{u.email}</td>
                                <td><small className="text-muted">{uid}</small></td>
                                <td>
                                    <Badge bg={currentRole === 'ADMIN' ? 'danger' : 'info'}>
                                        {currentRole}
                                    </Badge>
                                </td>
                                <td>{pkg ? pkg.currentPackage : 'Nema podataka'}</td>
                                <td>{pkg ? (pkg.remainingUploads ?? 0) : '-'}</td>
                                <td>
                                    <Button
                                        variant={currentRole === 'ADMIN' ? "secondary" : "warning"}
                                        size="sm"
                                        onClick={() => handleRoleChange(uid, currentRole)}
                                    >
                                        {currentRole === 'ADMIN' ? 'Ukloni Admina' : 'Postavi Admina'}
                                    </Button>
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </Table>
            </Card.Body>
        </Card>
    );
};

export default AdminDashboard;