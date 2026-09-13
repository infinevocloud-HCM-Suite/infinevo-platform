import moment from "moment/moment";
import { GlobalConst } from "../../../shared/appConfig/globalConst";

export default function Footer() {
    return (<div id="kt_app_footer"
        className="app-footer align-items-center justify-content-center justify-content-md-between flex-column flex-md-row py-3">
        <div className="text-dark order-2 order-md-1">
            <span className="text-muted fw-semibold me-1">{moment().format('YYYY')} &copy;</span>
            <a href="http://infinevocloud.com" target="_blank"
                className="text-gray-800 text-hover-primary" rel="noreferrer">INFINEVOCLOUD</a>
        </div>
        <ul className="menu menu-gray-600 menu-hover-primary fw-semibold order-1">
            <li className="menu-item">
                <a href="http://infinevocloud.com/about.php" target="_blank" className="menu-link px-2" rel="noreferrer">About</a>
            </li>
            <li className="menu-item">
                <a href={`mailto:${GlobalConst.SUPPORT_EMAIL}`} target="_blank" className="menu-link px-2" rel="noreferrer">Support</a>
            </li>
        </ul>
    </div>)
}