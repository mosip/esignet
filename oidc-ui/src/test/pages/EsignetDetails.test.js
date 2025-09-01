import React from 'react';
import { render, screen } from '@testing-library/react';
import EsignetDetailsPage from '../../pages/EsignetDetails';

// Mock the EsignetDetails component
jest.mock('../../components/EsignetDetails', () => {
  function EsignetDetails() {
    return (
      <div data-testid="EsignetDetailsComponent">Mocked EsignetDetails</div>
    );
  }
  return EsignetDetails;
});

describe('EsignetDetailsPage', () => {
  it('renders the EsignetDetails component', () => {
    render(<EsignetDetailsPage />);
    const component = screen.getByTestId('EsignetDetailsComponent');

    expect(component).toBeInTheDocument();
    expect(component).toHaveTextContent('Mocked EsignetDetails');
  });
});
