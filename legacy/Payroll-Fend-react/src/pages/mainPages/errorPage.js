import React from "react";
import { useRouteError } from 'react-router-dom';
import _ from 'lodash';
import { Link } from 'react-router-dom';
import pageNotFoundGraphic from '../../assets/images/404.svg';
import { GlobalConst } from "../../shared/appConfig/globalConst";

export default function ErrorPage() {
    const error = useRouteError();
    const getErrorImage = (statusCode) => {
        let image = '';
        switch (statusCode) {
            case 404:
                image = pageNotFoundGraphic;
                break;
            default:
                image = pageNotFoundGraphic;
                break;
        }
        return <img src={image} alt={'error'} className="img-fluid" />
    }

    return (
        <div className="container">
            <div className="d-flex align-items-center justify-content-center" style={{ height: '100vh' }}>
                <div className="p-3" style={{ width: 400 }}>
                    <div className="text-center">
                        {(!_.isEmpty(error) && error.status) && getErrorImage(error.status)}
                        <h1 className="mt-5">Oops! <i>{error.statusText || error.message}</i></h1>
                        <p className="mb-4">Sorry, we did not find what you were looking for.</p>
                        <div className=""><Link to={GlobalConst.BASE_URL} className="link-underline link-underline-opacity-0">goto homepage</Link></div>
                    </div>
                </div>
            </div>
        </div>
    );
}