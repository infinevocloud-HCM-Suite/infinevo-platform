import React from "react";
import { Container, Row, Col, Card, Button } from "react-bootstrap";
import { Pie, Bar } from "react-chartjs-2";
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  BarElement,
} from "chart.js";
 
// Register required Chart.js components
ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, BarElement);
 
import "./style.css";
 
const Home = () => {
  const pieData = {
    labels: ["Research & Development", "Professional Services", "Management", "Sales", "Administration"],
    datasets: [
      {
        data: [8, 5, 3, 3, 1],
        backgroundColor: ["#4caf50", "#2196f3", "#ff9800", "#f44336", "#9c27b0"],
      },
    ],
  };
 
  const barData = {
    labels: ["Sep 2024", "Oct 2024", "Nov 2024", "Dec 2024", "Jan 2024", "Feb 2025"],
    datasets: [
      {
        label: "Leave Analysis",
        data: [3.567, 1.567, 0.90, 5.890, 10.98, 14.8125],
        backgroundColor: "#2196f3",
      },
    ],
  };
 
  return (
<Container fluid className="dashboard-container">
<Row className="mb-3">
<Col className="text-center">
<h2>Timesheet Management System</h2>
</Col>
</Row>
<Row className="mb-3">
<Col>
<Card className="stat-card">
<Card.Body>
<h5>Project</h5>
<h2>0</h2>
</Card.Body>
</Card>
</Col>
<Col>
<Card className="stat-card">
<Card.Body>
<h5>Timesheets</h5>
<h2>19</h2>
</Card.Body>
</Card>
</Col>
<Col>
<Card className="stat-card">
<Card.Body>
<h5>Employee</h5>
<h2>2</h2>
</Card.Body>
</Card>
</Col>
<Col>
<Card className="stat-card">
<Card.Body>
<h5>Attendance</h5>
<h2>0</h2>
</Card.Body>
</Card>
</Col>
<Col>
<Button variant="primary" className="checkin-button">
            Log In
</Button>
</Col>
</Row>
 
      <Row>
<Col md={4}>
<Card className="data-card">
<Card.Body>
<h5>Leave Requests</h5>
<h3>5</h3>
<p>Today: 3</p>
<p>This Month: 5</p>
</Card.Body>
</Card>
<Card className="data-card mt-3">
<Card.Body>
<h5>Leave Allocation Requests</h5>
<h3>5</h3>
</Card.Body>
</Card>
<Card className="data-card mt-3">
<Card.Body>
<h5>Reports</h5>
<h3>17</h3>
</Card.Body>
</Card>
</Col>
<Col md={4}>
<Card className="chart-card">
<Card.Header>Departments</Card.Header>
<Card.Body>
<Pie data={pieData} />
</Card.Body>
</Card>
</Col>
<Col md={4}>
<Card className="chart-card">
<Card.Header>Monthly Leave Analysis</Card.Header>
<Card.Body>
<Bar data={barData} />
</Card.Body>
</Card>
</Col>
</Row>
</Container>
  );
};
 
export default Home;