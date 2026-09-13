import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import 'bootstrap/dist/css/bootstrap.min.css';
import axios from 'axios';
import { GlobalConst } from '../../../shared/appConfig/globalConst';
import { errorMsg } from '../../../shared/helpers/msgHelper';
import { getDecodedToken } from '../../../shared/helpers/tokenHelper';
import Loader from '../../../shared/components/loaders/fullPageLoader';
import { PrinterOutlined, ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import PayslipDocument from './components/payslipDocument';

// Helper to dynamically load html2pdf.js from CDN
export const loadHtml2Pdf = async () => {
  if (window.html2pdf) return window.html2pdf;
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js';
    script.onload = () => resolve(window.html2pdf);
    script.onerror = reject;
    document.head.appendChild(script);
  });
};

const Payslip = () => {
  const { payrunId } = useParams();
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [isDownloading, setIsDownloading] = useState(false);
  const [payslipData, setPayslipData] = useState(null);
  const [error, setError] = useState(null);

  const employeeId = getDecodedToken()?.sub;
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchPayslipData();
  }, [payrunId, employeeId, organizationId]);

  const fetchPayslipData = async () => {
    try {
      setIsLoading(true);
      setError(null);

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/payrun-employees/${payrunId}/${employeeId}`,
        {
          headers: {
            'organizationId': organizationId,
            'Authorization': `Bearer ${localStorage.getItem("__t")}`
          }
        }
      );

      if (response.data && response.data.data) {
        setPayslipData(response.data.data);
      } else {
        throw new Error('Invalid response format');
      }
    } catch (error) {
      console.error('Error fetching payslip data:', error);
      setError('Failed to load payslip data. Please try again.');
      errorMsg("Error", "Failed to load payslip data", false);
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
    } catch (err) {
      console.error("PDF generation failed:", err);
      errorMsg("Error", "Failed to download PDF. Please try printing it instead.", false);
    } finally {
      setIsDownloading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ height: '100vh' }}>
        <Loader />
      </div>
    );
  }

  if (error) {
    return (
      <div className="container mt-5">
        <div className="alert alert-danger text-center">
          <h4>Error Loading Payslip</h4>
          <p>{error}</p>
          <button
            className="btn btn-primary mt-3"
            onClick={() => navigate(-1)}
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  if (!payslipData) {
    return (
      <div className="container mt-5">
        <div className="alert alert-warning text-center">
          <h4>No Payslip Data Found</h4>
          <p>Unable to load payslip information.</p>
          <button
            className="btn btn-primary mt-3"
            onClick={() => navigate(-1)}
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Utility Bar */}
      <div className="d-flex justify-content-between align-items-center mx-4 mt-3 no-print">
        <div>
          {isDownloading && (
            <span className="text-success fw-bold animate-pulse">
              <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              Generating PDF...
            </span>
          )}
        </div>
        <div style={{ display: 'flex', gap: '16px' }}>
          <Tooltip title="Download PDF">
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
          <Tooltip title="Go Back">
            <ArrowLeftOutlined
              className="ant-icon-hover"
              style={{ fontSize: '20px', color: '#8c8c8c', cursor: 'pointer' }}
              onClick={() => navigate(-1)}
            />
          </Tooltip>
        </div>
      </div>

      {/* Styled Payslip Document */}
      <PayslipDocument payslipData={payslipData} />
    </div>
  );
};

export default Payslip;