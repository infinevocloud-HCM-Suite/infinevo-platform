import Swal from 'sweetalert2';
import withReactContent from "sweetalert2-react-content";
import { ERROR_CONST } from '../appConfig/errorConst';
const MySwal = withReactContent(Swal);

function getDescription(key) {
    return ERROR_CONST[key];
}

export function errorMsg(title, desc, isStaticDesc, callback) {

    return MySwal.fire({
        title: title ? title : "Undefined Error Occured",
        text: (isStaticDesc) ? desc : getDescription(desc),
        icon: 'error',
        confirmButtonText: 'OK',
        preConfirm: () => {
            // Execute callback if provided
            if (typeof callback === 'function') {
                return callback();
            }
            return true;
        }
    });
}

export function successMsg(title, desc, isStaticDesc, callback) {
    return MySwal.fire({
        title: title ? title : "Successfull !!!",
        text: (isStaticDesc) ? desc : getDescription(desc),
        icon: 'success',
        confirmButtonText: 'OK',
        preConfirm: () => {
            // Execute callback if provided
            if (typeof callback === 'function') {
                return callback();
            }
            return true;
        }
    });
}