import noData from '../../../assets/images/no-data.svg';

export default function NoData({ title, description, showButton, buttonText, btnIcon, buttonCallback, btnClasses }) {

    return (
        <div className="d-flex align-items-center justify-content-center flex-column">
            <img src={noData} className='w-200px' alt={'No Data Found'}/>
            <h3>{title}</h3>
            <p>{description}</p>
            {showButton && <button className={btnClasses} onClick={buttonCallback}>
                {btnIcon}
                {buttonText}
            </button>}
        </div>
    );
}