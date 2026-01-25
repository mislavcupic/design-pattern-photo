const API_BASE_URL = 'http://localhost:8080/api/admin';

const getHeaders = () => {
    // KLJUČNO: Ovdje mora biti ID Token (JWT), a ne Custom Token
    const token = localStorage.getItem('idToken');
    return {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };
};

const AdminService = {
    getAllUsers: async () => {
        const response = await fetch(`${API_BASE_URL}/users/all`, { headers: getHeaders() });
        if (!response.ok) throw new Error('Neuspjelo dohvaćanje korisnika');
        return response.json();
    },

    getAllUserPackages: async () => {
        const response = await fetch(`${API_BASE_URL}/user-packages/all`, { headers: getHeaders() });
        if (!response.ok) throw new Error('Neuspjelo dohvaćanje paketa');
        return response.json();
    },

    updateUserRole: async (uid, newRole) => {
        const response = await fetch(`${API_BASE_URL}/users/${uid}/role`, {
            method: 'PUT',
            headers: getHeaders(),
            body: JSON.stringify({ role: newRole })
        });
        if (!response.ok) throw new Error('Greška pri promjeni uloge');
        return response.json();
    }
};

export default AdminService;