import { render, screen, fireEvent } from '@testing-library/react';
import Login from '../../components/Login';

// Mock Input component
jest.mock('../../components/Input', () => {
  const MockInput = (props) => (
    <input
      data-testid={`input-${props.id}`}
      id={props.id}
      placeholder={props.placeholder}
      value={props.value}
      onChange={props.handleChange}
    />
  );
  MockInput.displayName = 'MockInput';
  return MockInput;
});

// Mock FormAction component
jest.mock('../../components/FormAction', () => {
  const MockFormAction = (props) => (
    <button onClick={props.handleSubmit} id={props.id}>
      {props.text}
    </button>
  );
  MockFormAction.displayName = 'MockFormAction';
  return MockFormAction;
});

// Mock FormExtra component
jest.mock('../../components/FormExtra', () => {
  const MockFormExtra = () => (
    <div data-testid="form-extra">FormExtra Content</div>
  );
  MockFormExtra.displayName = 'MockFormExtra';
  return MockFormExtra;
});

// Mock loginFields
jest.mock('../../constants/formFields', () => ({
  loginFields: [
    {
      id: 'email',
      labelText: 'Email',
      labelFor: 'email',
      name: 'email',
      type: 'email',
      isRequired: true,
      placeholder: 'Enter your email',
    },
    {
      id: 'password',
      labelText: 'Password',
      labelFor: 'password',
      name: 'password',
      type: 'password',
      isRequired: true,
      placeholder: 'Enter your password',
    },
  ],
}));

describe('Login Component', () => {
  test('renders input fields based on loginFields', () => {
    render(<Login />);

    expect(screen.getByPlaceholderText('Enter your email')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText('Enter your password')
    ).toBeInTheDocument();
  });

  test('renders FormExtra and FormAction', () => {
    render(<Login />);
    expect(screen.getByTestId('form-extra')).toBeInTheDocument();
    expect(screen.getByText('Login')).toBeInTheDocument();
  });

  test('updates state on input change', () => {
    render(<Login />);

    const emailInput = screen.getByPlaceholderText('Enter your email');
    fireEvent.change(emailInput, { target: { value: 'user@example.com' } });
    expect(emailInput.value).toBe('user@example.com');
  });

  test('handles form submit', () => {
    render(<Login />);

    const loginButton = screen.getByText('Login');
    fireEvent.click(loginButton);

    // Since authenticateUser is empty, we just make sure no crash occurs
    expect(screen.getByText('Login')).toBeInTheDocument();
  });
});
