import { render, screen, fireEvent } from '@testing-library/react';
import Input from '../../components/Input';
import { useTranslation } from 'react-i18next';

jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));

describe('Input Component', () => {
  const mockChangeHandler = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    useTranslation.mockReturnValue({
      t: (key) => `translated_${key}`,
    });
  });

  test('renders input with correct attributes and calls handleChange', () => {
    render(
      <Input
        handleChange={mockChangeHandler}
        value="test-value"
        labelText="Test Label"
        labelFor="test-id"
        id="test-id"
        name="test-name"
        type="text"
        placeholder="Test Placeholder"
        isRequired={true}
        customClass=" custom-css"
      />
    );

    const input = screen.getByPlaceholderText('Test Placeholder');

    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'text');
    expect(input).toHaveAttribute('name', 'test-name');
    expect(input).toHaveAttribute('id', 'test-id');
    expect(input).toHaveAttribute('required');
    expect(input).toHaveClass('custom-css');
    expect(input).toHaveAttribute('title', 'translated_vid_info');

    // simulate input change
    fireEvent.change(input, { target: { value: 'new-value' } });
    expect(mockChangeHandler).toHaveBeenCalled();
  });

  test('uses default tooltip if none provided', () => {
    render(
      <Input
        handleChange={mockChangeHandler}
        value=""
        labelText="Another Label"
        labelFor="another-id"
        id="another-id"
        name="another-name"
        type="text"
      />
    );

    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('title', 'translated_vid_info');
  });

  test('uses custom tooltip message when provided', () => {
    render(
      <Input
        handleChange={mockChangeHandler}
        value=""
        labelText="Tooltip Label"
        labelFor="tooltip-id"
        id="tooltip-id"
        name="tooltip-name"
        type="text"
        tooltipMsg="custom_tooltip"
      />
    );

    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('title', 'translated_custom_tooltip');
  });
});
