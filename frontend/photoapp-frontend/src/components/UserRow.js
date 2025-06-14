import React, { useState, useContext } from 'react';
import { Button, Form } from 'react-bootstrap';
import PropTypes from 'prop-types'; // Dodaj ovaj import

const UserRow = ({ user, onUpdateUser }) => {
    console.log('User prop in UserRow:', user);

    const [userType, setUserType] = useState(user?.userType);
    const [userPackage, setUserPackage] = useState(user?.userPackage);
    const isAdmin = true; // **MAKNI OVO U PRAVOJ APLIKACIJI**

    const handleTypeChange = (event) => {
        setUserType(event.target.value);
    };

    const handlePackageChange = (event) => {
        setUserPackage(event.target.value);
    };

    const handleSave = () => {
        onUpdateUser(user?.firebaseUid, userType, userPackage);
    };

    return (
        <tr>
            <td>{user?.firebaseUid}</td>
            <td>{user?.email}</td>
            <td>
                {isAdmin ? (
                    <Form.Control size="sm" as="select" value={userType} onChange={handleTypeChange}>
                        <option value="REGISTERED">REGISTERED</option>
                        <option value="ADMIN">ADMIN</option>
                    </Form.Control>
                ) : (
                    <span>{userType}</span>
                )}
            </td>
            <td>
                <Form.Control size="sm" as="select" value={userPackage} onChange={handlePackageChange}>
                    <option value="FREE">FREE</option>
                    <option value="PRO">PRO</option>
                    <option value="GOLD">GOLD</option>
                </Form.Control>
            </td>
            <td>
                <Button variant="outline-primary" onClick={handleSave} size="sm">
                    Spremi
                </Button>
            </td>
        </tr>
    );
};

UserRow.propTypes = {
    user: PropTypes.shape({
        firebaseUid: PropTypes.string.isRequired,
        email: PropTypes.string.isRequired,
        userType: PropTypes.string.isRequired,
        userPackage: PropTypes.string.isRequired,
    }).isRequired,
    onUpdateUser: PropTypes.func.isRequired, // Definiramo da je onUpdateUser funkcija i obavezna
};

export default UserRow;