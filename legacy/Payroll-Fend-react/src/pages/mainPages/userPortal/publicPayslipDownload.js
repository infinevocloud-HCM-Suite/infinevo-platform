import React, { useState, useEffect } from 'react';
import { useParams, useLocation } from 'react-router-dom';
import 'bootstrap/dist/css/bootstrap.min.css';
import axios from 'axios';
import { GlobalConst } from '../../../shared/appConfig/globalConst';
import { errorMsg } from '../../../shared/helpers/msgHelper';
import Loader from '../../../shared/components/loaders/fullPageLoader';
import { PrinterOutlined, DownloadOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import PayslipDocument from './components/payslipDocument';
import { loadHtml2Pdf } from './payslipGenerator';

const PublicPayslipDownload = () => {
  const { payrunId, employeeId } = useParams();
  const location = useLocation();
  
  const [isLoading, setIsLoading] = useState(true);
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadComplete, setDownloadComplete] = useState(false);
  const [payslipData, setPayslipData] = useState(null);
  const [error, setError] = useState(null);

  // Extract orgId and token from search params
  const searchParams = new URLSearchParams(location.search);
  const orgId = searchParams.get('orgId');
  const token = searchParams.get('token');

  useEffect(() => {
    if (!payrunId || !employeeId || !orgId || !token) {
      setError('Invalid download link parameters.');
      setIsLoading(false);
      return;
    }
    fetchPublicPayslipData();
  }, [payrunId, employeeId, orgId, token]);

  const fetchPublicPayslipData = async () => {
    try {
      setIsLoading(true);
      setError(null);

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/public/payslips/${payrunId}/${employeeId}?orgId=${orgId}&token=${token}`
      );

      if (response.data && response.data.data) {
        setPayslipData(response.data.data);
      } else {
        throw new Error('Invalid response format');
      }
    } catch (err) {
      console.error('Error fetching public payslip:', err);
      setError(err.response?.data?.message || 'Failed to verify download link. It may have expired or is invalid.');
    } finally {
      setIsLoading(false);
    }
  };

  const downloadPDF = async () => {
    if (isDownloading) return;
    try {
      setIsDownloading(true);
      const element = document.getElementById('payslip-to-print');
      if (!element) {
        throw new Error('Payslip element not found in DOM');
      }

      // Load html2pdf dynamically
      const html2pdf = await loadHtml2Pdf();

      const opt = {
        margin:       10,
        filename:     `payslip_${payslipData?.payPeriod || 'payslip'}.pdf`,
        image:        { type: 'jpeg', quality: 0.98 },
        html2canvas:  { scale: 2, useCORS: true },
        jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
      };

      await html2pdf().set(opt).from(element).save();
      setDownloadComplete(true);
    } catch (err) {
      console.error("PDF generation failed:", err);
      errorMsg("Error", "Failed to download PDF. Please try printing it instead.", false);
    } finally {
      setIsDownloading(false);
    }
  };

  // Trigger automatic download after data loads
  useEffect(() => {
    if (!isLoading && payslipData && !error) {
      const timer = setTimeout(() => {
        downloadPDF();
      }, 1000); // 1-second delay for full browser layout rendering
      return () => clearTimeout(timer);
    }
  }, [isLoading, payslipData, error]);

  if (isLoading) {
    return (
      <div className="d-flex flex-column justify-content-center align-items-center" style={{ height: '100vh', backgroundColor: '#f8f9fa' }}>
        <Loader />
        <h5 className="mt-4 text-secondary">Verifying secure download link...</h5>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container mt-5">
        <div className="alert alert-danger text-center shadow-sm p-4" style={{ borderRadius: '10px' }}>
          <h4 className="alert-heading text-danger">Verification Failed</h4>
          <p className="mt-3">{error}</p>
          <hr />
          <p className="mb-0 text-muted">
            For security, payslip links expire or can only be accessed with a valid signature. 
            Please check the link in your email or log in to the employee portal.
          </p>
        </div>
      </div>
    );
  }

  if (!payslipData) {
    return (
      <div className="container mt-5">
        <div className="alert alert-warning text-center shadow-sm p-4" style={{ borderRadius: '10px' }}>
          <h4>Payslip Details Not Found</h4>
          <p>We verified the link, but no payslip details could be retrieved.</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Utility Bar */}
      <div className="d-flex justify-content-between align-items-center mx-4 mt-3 no-print p-3 bg-light rounded shadow-sm border">
        <div>
          {isDownloading ? (
            <span className="text-primary fw-bold">
              <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              Generating PDF and starting download...
            </span>
          ) : downloadComplete ? (
            <span className="text-success fw-bold">
              ✓ Payslip downloaded successfully! Check your browser downloads.
            </span>
          ) : (
            <span className="text-muted">
              Preparing your automatic download...
            </span>
          )}
        </div>
        <div style={{ display: 'flex', gap: '16px' }}>
          <Tooltip title="Download PDF Manual">
            <DownloadOutlined
              className={`ant-icon-hover ${isDownloading ? 'text-muted' : ''}`}
              style={{ fontSize: '20px', color: isDownloading ? '#bfbfbf' : '#52c41a', cursor: isDownloading ? 'not-allowed' : 'pointer' }}
              onClick={downloadPDF}
            />
          </Tooltip>
          <Tooltip title="Print Payslip">
            <PrinterOutlined
              className="ant-icon-hover"
              style={{ fontSize: '20px', color: '#1890ff', cursor: 'pointer' }}
              onClick={() => window.print()}
            />
          </Tooltip>
        </div>
      </div>

      {/* Styled Payslip Document */}
      <PayslipDocument payslipData={payslipData} />
    </div>
  );
};

export default PublicPayslipDownload;
