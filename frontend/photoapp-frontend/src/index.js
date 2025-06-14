import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { AuthProvider } from "./context/AuthContext";
import { BrowserRouter } from "react-router-dom";  // Dodaj ovo za routing
import 'bootstrap/dist/css/bootstrap.min.css';

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
    // <React.StrictMode>
        <BrowserRouter> {/* Omotaj cijelu aplikaciju u BrowserRouter */}
            <AuthProvider>
                <App />
            </AuthProvider>
        </BrowserRouter>
    //</React.StrictMode>
);