import React, { useEffect, useState } from 'react';
import axios from 'axios';
import _ from 'lodash';
import { Upload } from 'antd';
import ImgCrop from 'antd-img-crop';
import { FiSave } from "react-icons/fi";
import { GlobalConst } from '../../../../shared/appConfig/globalConst';
import { errorMsg, successMsg } from '../../../../shared/helpers/msgHelper';
import { getUserImagePath } from '../../../../shared/helpers/mainHelper';

const EditProfileDetailsTab = ({ userData, setUserData, getUserDetails }) => {


    const [updatingRecord, setUpdatingRecord] = useState(false);
    const [fileList, setFileList] = useState([]);


    const handleNameChange = (e) => {
        setUserData(oldData => ({
            ...oldData,
            name: e.target.value
        }))
    }

    useEffect(() => {
        if (!_.isEmpty(userData.userImage)) {
            setFileList([
                {
                    uid: '-1',
                    name: 'image.png',
                    status: 'done',
                    url: userData.userImage,
                },
            ])
        }
    }, [userData]);

    const handleSaveClick = () => {
        setUpdatingRecord(true);
        const postData = {
            "userNicename": userData.name,
            "userProfileImg": userData.userImage
        }
        axios
            .put(`${GlobalConst.API_URL}/api/v1/entrance/user/update-profile`, postData)
            .then((op) => {
                if (!_.isEmpty(op) && !_.isEmpty(op.data) && op.data.message === 'PROFILE_UPDATED_SUCCESSFULLY') {
                    successMsg("Profile updated successfully", "Your profile has been successfully updated.", true);
                }
                else {
                    errorMsg("Unable to update profile", `We are facing some issues while updating your profile details, please try after sometime or if the issues is still persisting then please contact the helpdesk.`, true);
                }
            })
            .catch(e => {
                if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
                    errorMsg("Unable to update profile", e.response.data.err_msg, false);
                }
                else {
                    errorMsg(e.code, e.message, true);
                }
            })
            .finally(() => {
                setUpdatingRecord(false);
                getUserDetails(true);
            })
    }
    const onChange = ({ fileList: newFileList }) => {
        // If there's a new file to upload (checking for originFileObj)
        if (newFileList.length > 0 && newFileList[newFileList.length - 1].originFileObj) {
            const file = newFileList[newFileList.length - 1].originFileObj;
            // Update status to uploading
            const updatedFileList = newFileList.map(item => {
                if (item.uid === newFileList[newFileList.length - 1].uid) {
                    return { ...item, status: 'uploading' };
                }
                return item;
            });
            setFileList(updatedFileList);
            // Create form data
            const formData = new FormData();
            formData.append('image', file);

            // Upload using Axios
            axios.post(`${GlobalConst.API_URL}/api/v1/entrance/upload-image`, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })
                .then(response => {
                    // On successful upload
                    if (!_.isEmpty(response) && !_.isEmpty(response.data) && !_.isEmpty(response.data.message) && response.data.message === 'IMAGE_UPLOADED') {
                        // Update user data with new image URL
                        setUserData(prevData => ({
                            ...prevData,
                            userImage: getUserImagePath(response.data.result)
                        }));

                        // Update file list with status done and URL
                        const successFileList = [{
                            uid: '-1',
                            name: file.name,
                            status: 'done',
                            url: getUserImagePath(response.data.result)
                        }];
                        setFileList(successFileList);
                    } else {
                        handleUploadError("Invalid response format");
                    }
                })
                .catch(error => {
                    handleUploadError(error);
                });
        } else {
            // Handle when user removes the image
            if (newFileList.length === 0) {
                setUserData(prevData => ({
                    ...prevData,
                    userImage: ''
                }));
            }

            const lastImage = (newFileList.length > 1) ? newFileList.at(-1) : newFileList;
            setFileList(lastImage);
        }
    };

    // Helper function to handle upload errors
    const handleUploadError = (error) => {
        // Update file list to show error
        setFileList(prevList =>
            prevList.map(file => ({
                ...file,
                status: 'error',
                error: error.message || "Upload failed"
            }))
        );

        // Show error message
        errorMsg(
            "Image Upload Failed",
            "There was an error uploading your image. Please try again later.",
            true
        );
    };

    const onPreview = async (file) => {
        let src = file.url;
        if (!src) {
            src = await new Promise((resolve) => {
                const reader = new FileReader();
                reader.readAsDataURL(file.originFileObj);
                reader.onload = () => resolve(reader.result);
            });
        }
        const image = new Image();
        image.src = src;
        const imgWindow = window.open(src);
        imgWindow?.document.write(image.outerHTML);
    };


    return (
        <div className="card mb-5 mb-xl-10" id="kt_profile_details_view">
            <div className="card-header">
                <div className="card-title m-0">
                    <h3 className="fw-bold m-0">Edit Profile Details</h3>
                </div>
            </div>

            <div className="card-body p-9">
                <div className="row mb-7">
                    <label className="col-lg-4 fw-semibold text-muted">Full Name</label>
                    <div className="col-lg-8">
                        <input type="text" name="fname" className="form-control form-control-lg form-control-solid mb-3 mb-lg-0" placeholder="Full Name" value={userData.name} onChange={handleNameChange} />
                    </div>
                </div>

                <div className="row mb-7">
                    <label className="col-lg-4 fw-semibold text-muted">Profile Image</label>
                    <div className="col-lg-8 fv-row">
                        <ImgCrop rotationSlider>
                            <Upload
                                listType="picture-card"
                                maxCount={1}
                                fileList={fileList}
                                onChange={onChange}
                                onPreview={onPreview}
                                beforeUpload={() => {
                                    return false;
                                }}
                            >
                                Click here to upload
                            </Upload>
                        </ImgCrop>
                        <div className="form-text">Allowed file types: png, jpg, jpeg.</div>
                    </div>
                </div>
                <div className="card-footer py-6 px-9">
                    <div className="row">
                        <div className="col-lg-4"></div>
                        <div className="col-lg-8">
                            <button type="button" onClick={handleSaveClick} className="btn btn-sm btn-primary" disabled={updatingRecord} data-kt-indicator={(updatingRecord) ? "on" : "off"}>
                                <span className="indicator-label"><FiSave className="me-2" />Save Changes</span>
                                <span className="indicator-progress">Saving...
                                    <span className="spinner-border spinner-border-sm align-middle ms-2"></span></span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default EditProfileDetailsTab;