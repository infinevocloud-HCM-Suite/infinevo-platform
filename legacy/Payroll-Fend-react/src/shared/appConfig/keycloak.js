import Keycloak from "keycloak-js";
const keycloak = new Keycloak({
 url: "http://localhost:8080",
 realm: "payroll-dev",
 clientId: "payroll-fe",
});

export default keycloak;