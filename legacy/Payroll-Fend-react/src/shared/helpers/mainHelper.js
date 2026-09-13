import _ from 'lodash';
import { Avatar } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { GlobalConst } from '../appConfig/globalConst';


export function getName(userData) {
    if (_.isEmpty(userData)) {
        return 'Anonymous'
    }
    // if(!_.isEmpty(userData) && !_.isEmpty(userData.userDetails) )
    // let returnText = (userData.userDetails.employeeData && userData.userDetails.employeeData.personal && userData.userDetails.employeeData.personal.firstName) ? `${userData.userDetails.employeeData.personal.firstName} ${userData.userDetails.employeeData.personal.lastName}` : userData.userDetails.employeeData.personal.empId;
    return userData.userDetails.name;
}

export function getEmail(userData) {
    if (_.isEmpty(userData)) {
        return 'example@example.com'
    }
    // let returnText = (userData.userDetails.user && userData.userDetails.user.email) ? `${userData.userDetails.user.email}` : userData.userDetails.employeeData.personal.empId;
    return userData.userDetails.email;
}

export function getUserDisplayChar(user) {
    // Check if the input is empty (null or undefined)
    if (_.isEmpty(user)) {
        return '';
    }
    let text = (user.preferred_username) ? user.preferred_username : user.email;
    // Extract the first character of the text and capitalize it
    const firstInitial = _.upperCase(_.head(text));
    return firstInitial;
}

export function getAvatar(userObject, size, addClass) {
    if (!_.isEmpty(userObject.userDetails)) {
        return <Avatar style={{ backgroundColor: '#266FF2', color: '#ffffff' }} className={addClass} size={size ? size : 'default'}><span className='text-white'>{getUserDisplayChar(userObject.userDetails)}</span></Avatar>;
    }
    else {
        // console.log("I am in the else block");
        return <Avatar icon={<UserOutlined />} />;
    }
}

export function getUserCharacter(userName, email, size, addClass) {
    if (_.isEmpty(userName)) {
        return '';
    }
    let text = (userName) ? userName : email;
    console.log("Text", text);
    // Extract the first character of the text and capitalize it
    const firstInitial = _.upperCase(_.head(text));
    return <Avatar style={{ backgroundColor: '#266FF2', color: '#ffffff' }} className={addClass} size={size ? size : 'default'}>{firstInitial}</Avatar>;
}

export function getTimeZone() {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
};

export function checkEvenOdd(num) {
    return num % 2 === 0 ? "even" : "odd";
}


export function getUserImagePath(imgURL) {
    let userImagePath = '';
    if (!_.isEmpty(imgURL) && !_.isEmpty(imgURL.path)) {
        let splitPath = imgURL.path.split('public');
        return `${GlobalConst.API_URL}${splitPath[1]}`;
    }
    return userImagePath;
}
