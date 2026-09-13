import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import NoData from './index'; // Adjust the import path as needed

// Mock the SVG import
jest.mock('../../../assets/images/no-data.svg', () => 'no-data-mock.svg');

describe('NoData Component', () => {
  test('renders with required props', () => {
    render(
      <NoData 
        title="No Data Available" 
        description="There is no data to display" 
      />
    );
    
    // Check if title and description are rendered
    expect(screen.getByText('No Data Available')).toBeInTheDocument();
    expect(screen.getByText('There is no data to display')).toBeInTheDocument();
    
    // Check if image is rendered
    expect(screen.getByRole('img')).toHaveAttribute('src', 'no-data-mock.svg');
    
    // Button should not be visible by default
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  test('renders with button when showButton is true', () => {
    render(
      <NoData 
        title="No Data Available" 
        description="There is no data to display" 
        showButton={true}
        buttonText="Refresh Data"
      />
    );
    
    // Button should be visible
    const button = screen.getByRole('button', { name: 'Refresh Data' });
    expect(button).toBeInTheDocument();
  });

  test('calls buttonCallback when button is clicked', () => {
    // Create a mock function
    const mockCallback = jest.fn();
    
    render(
      <NoData 
        title="No Data Available" 
        description="There is no data to display" 
        showButton={true}
        buttonText="Refresh Data"
        buttonCallback={mockCallback}
      />
    );
    
    // Find and click the button
    const button = screen.getByRole('button', { name: 'Refresh Data' });
    fireEvent.click(button);
    
    // Check if the mock function was called
    expect(mockCallback).toHaveBeenCalledTimes(1);
  });

  test('uses default props when not provided', () => {
    render(
      <NoData 
        title="No Data Available" 
        description="There is no data to display" 
        showButton={true}
      />
    );
    
    // Button should have default text
    const button = screen.getByRole('button', { name: 'Click me' });
    expect(button).toBeInTheDocument();
  });
});