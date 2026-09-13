const employeeData = {
  employees: [
    {
      "basicDetails": {
        "firstName": "Rahul",
        "middleName": "Kumar",
        "lastName": "Sharma",
        "employeeId": "EMP001",
        "dateOfJoining": "2022-05-15",
        "workEmail": "rahul.sharma@example.com",
        "mobileNumber": "9876543210",
        "gender": "Male",
        "WorkLocationName": "Head Office (Patna)",
        "organizationName": "Tech Solutions Inc",
        "addressLine1": "123 Tech Park",
        "addressLine2": "Sector 5",
        "state": "Bihar",
        "city": "Patna",
        "pinCode": "800001",
        "departmentName": "IT",
        "designationName": "Senior Developer",
        "isDirector": false,
        "enablePortalAccess": true,
        "statutoryComponents": {
          "professionalTax": true,
          "pfAccountNumber": "MH/PNB/1234567/000/1234567",
          "uan": "123456789012",
          "employeesProvidentFund": true,
          "employeePensionScheme": true,
          "epsAtActualPfWages": false
        }
      },
      "salaryDetails": {
        "annualCTC": 1200000,
        "basicPercent": 50,
        "hraPercent": 50,
        "conveyance": 24000,
        "basic": 600000,
        "hra": 300000,
        "fixedAllowance": 276000,
        "monthlyCTC": 100000,
        "annualCTCComputed": 1200000,
        "basicMonthly": 50000,
        "hraMonthly": 25000,
        "conveyanceMonthly": 2000,
        "fixedMonthly": 23000,
        "conveyanceDisplay": 24000
      },
      "personalDetails": {
        "dateOfBirth": "1990-08-25",
        "age": 33,
        "fatherName": "Rajesh Sharma",
        "panNumber": "ABCDE1234F",
        "differentlyAbledType": "None",
        "personalEmail": "rahul.personal@example.com",
        "residentialAddress": {
          "addressLine1": "456 Residential Lane",
          "addressLine2": "Gandhi Nagar",
          "city": "Patna",
          "state": "Bihar",
          "pincode": "800002",
          "age": ""
        }
      },
      "paymentInformation": {
        "paymentMode": "bankTransfer",
        "accountHolderName": "Rahul Kumar Sharma",
        "bankName": "State Bank of India",
        "accountNumber": "12345678901234",
        "confirmAccountNumber": "12345678901234",
        "ifsc": "SBIN0000123",
        "accountType": "savings"
      }
    },
    {
      "basicDetails": {
        "firstName": "Priya",
        "middleName": "",
        "lastName": "Patel",
        "employeeId": "EMP002",
        "dateOfJoining": "2021-11-10",
        "workEmail": "priya.patel@example.com",
        "mobileNumber": "8765432109",
        "gender": "Female",
        "WorkLocationName": "MAGARPATTA",
        "organizationName": "Tech Solutions Inc",
        "addressLine1": "456 Corporate Tower",
        "addressLine2": "Magarpatta City",
        "state": "Maharashtra",
        "city": "Pune",
        "pinCode": "411028",
        "departmentName": "HR",
        "designationName": "HR Manager",
        "isDirector": false,
        "enablePortalAccess": true,
        "statutoryComponents": {
          "professionalTax": true,
          "pfAccountNumber": "MH/PNB/7654321/000/7654321",
          "uan": "987654321098",
          "employeesProvidentFund": true,
          "employeePensionScheme": false,
          "epsAtActualPfWages": false
        }
      },
      "salaryDetails": {
        "annualCTC": 1500000,
        "basicPercent": 50,
        "hraPercent": 40,
        "conveyance": 36000,
        "basic": 750000,
        "hra": 300000,
        "fixedAllowance": 414000,
        "monthlyCTC": 125000,
        "annualCTCComputed": 1500000,
        "basicMonthly": 62500,
        "hraMonthly": 25000,
        "conveyanceMonthly": 3000,
        "fixedMonthly": 34500,
        "conveyanceDisplay": 36000
      },
      "personalDetails": {
        "dateOfBirth": "1988-03-17",
        "age": 35,
        "fatherName": "Sanjay Patel",
        "panNumber": "EFGHI5678J",
        "differentlyAbledType": "None",
        "personalEmail": "priya.personal@example.com",
        "residentialAddress": {
          "addressLine1": "789 Green Valley",
          "addressLine2": "Wakad",
          "city": "Pune",
          "state": "Maharashtra",
          "pincode": "411057",
          "age": ""
        }
      },
      "paymentInformation": {
        "paymentMode": "directDeposit",
        "accountHolderName": "Priya Patel",
        "bankName": "HDFC Bank",
        "accountNumber": "98765432109876",
        "confirmAccountNumber": "98765432109876",
        "ifsc": "HDFC0000987",
        "accountType": "current"
      }
    },
    {
      "basicDetails": {
        "firstName": "Amit",
        "middleName": "Singh",
        "lastName": "Verma",
        "employeeId": "EMP003",
        "dateOfJoining": "2023-01-05",
        "workEmail": "amit.verma@example.com",
        "mobileNumber": "7654321098",
        "gender": "Male",
        "WorkLocationName": "KESHAV CHOWK",
        "organizationName": "Tech Solutions Inc",
        "addressLine1": "789 Business Plaza",
        "addressLine2": "Keshav Chowk",
        "state": "Delhi",
        "city": "New Delhi",
        "pinCode": "110001",
        "departmentName": "Finance",
        "designationName": "Finance Analyst",
        "isDirector": true,
        "enablePortalAccess": false,
        "statutoryComponents": {
          "professionalTax": false,
          "pfAccountNumber": "DL/PNB/9876543/000/9876543",
          "uan": "567890123456",
          "employeesProvidentFund": false,
          "employeePensionScheme": false,
          "epsAtActualPfWages": false
        }
      },
      "salaryDetails": {
        "annualCTC": 1800000,
        "basicPercent": 60,
        "hraPercent": 50,
        "conveyance": 48000,
        "basic": 1080000,
        "hra": 540000,
        "fixedAllowance": 132000,
        "monthlyCTC": 150000,
        "annualCTCComputed": 1800000,
        "basicMonthly": 90000,
        "hraMonthly": 45000,
        "conveyanceMonthly": 4000,
        "fixedMonthly": 11000,
        "conveyanceDisplay": 48000
      },
      "personalDetails": {
        "dateOfBirth": "1985-12-30",
        "age": 38,
        "fatherName": "Vikram Verma",
        "panNumber": "JKLMN9012O",
        "differentlyAbledType": "Other",
        "personalEmail": "amit.personal@example.com",
        "residentialAddress": {
          "addressLine1": "321 Elite Apartments",
          "addressLine2": "Connaught Place",
          "city": "New Delhi",
          "state": "Delhi",
          "pincode": "110002",
          "age": ""
        }
      },
      "paymentInformation": {
        "paymentMode": "cheque",
        "accountHolderName": "Amit Singh Verma",
        "bankName": "ICICI Bank",
        "accountNumber": "56789012345678",
        "confirmAccountNumber": "56789012345678",
        "ifsc": "ICIC0000567",
        "accountType": "savings"
      }
    }
  ]
};

export default employeeData;