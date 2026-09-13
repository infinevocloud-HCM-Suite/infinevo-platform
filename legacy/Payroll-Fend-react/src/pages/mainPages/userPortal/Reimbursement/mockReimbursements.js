const mockReimbursements = [
  {
    id: 1,
    requestDate: "05-Aug-2026",
    reimbursementType: "Medical",
    description: "Medicine Bills for hospitalization",
    requestedAmount: 5000,
    approvedAmount: null,
    status: "PENDING",
    remarks: "-",
    paymentStatus: "UNPAID"
  },
  {
    id: 2,
    requestDate: "01-Aug-2026",
    reimbursementType: "Travel",
    description: "Client Visit – Pune to Mumbai",
    requestedAmount: 3000,
    approvedAmount: 2500,
    status: "APPROVED",
    remarks: "Eligible amount",
    paymentStatus: "PAID"
  },
  {
    id: 3,
    requestDate: "25-Jul-2026",
    reimbursementType: "Food",
    description: "Team lunch during client meeting",
    requestedAmount: 1500,
    approvedAmount: null,
    status: "REJECTED",
    remarks: "Not as per policy",
    paymentStatus: "UNPAID"
  },
  {
    id: 4,
    requestDate: "20-Jul-2026",
    reimbursementType: "Internet",
    description: "Broadband bill – July 2026",
    requestedAmount: 999,
    approvedAmount: 999,
    status: "APPROVED",
    remarks: "Approved as per policy",
    paymentStatus: "PAID"
  },
  {
    id: 5,
    requestDate: "15-Jul-2026",
    reimbursementType: "Fuel",
    description: "Fuel expenses for office commute",
    requestedAmount: 2200,
    approvedAmount: 2000,
    status: "APPROVED",
    remarks: "Approved up to limit",
    paymentStatus: "UNPAID"
  },
  {
    id: 6,
    requestDate: "10-Jul-2026",
    reimbursementType: "Other",
    description: "Office stationery purchase",
    requestedAmount: 800,
    approvedAmount: null,
    status: "PENDING",
    remarks: "-",
    paymentStatus: "UNPAID"
  }
];

export default mockReimbursements;
