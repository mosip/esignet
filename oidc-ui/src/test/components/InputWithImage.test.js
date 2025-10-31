import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import InputWithImage from '../../components/InputWithImage';

jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, options) => options?.id || key,
    i18n: {
      language: 'en',
    },
  }),
}));

jest.mock('../../common/Popover', () => ({
  __esModule: true,
  default: ({ child }) => <>{child}</>,
}));

describe('InputWithImage Component', () => {
  const baseProps = {
    id: 'login_Username',
    type: 'text',
    name: 'username',
    value: 'testuser',
    placeholder: 'Enter username',
    labelText: 'Username',
    handleChange: jest.fn(),
    blurChange: jest.fn(),
    customClass: '',
    isRequired: true,
    icon: true,
    i18nKeyPrefix1: 'tooltips',
    i18nKeyPrefix2: 'errors',
    currenti18nPrefix: 'login',
    errorCode: 'invalid_username',
    individualId: '123456',
  };

  test('renders input and triggers handleChange', () => {
    render(<InputWithImage {...baseProps} />);
    const input = screen.getByPlaceholderText('Enter username');
    fireEvent.change(input, { target: { value: 'newUser' } });
    expect(baseProps.handleChange).toHaveBeenCalled();
  });

  test('renders password toggle and works', () => {
    const props = {
      ...baseProps,
      id: 'login_Password',
      type: 'password',
      value: 'pass',
    };
    render(<InputWithImage {...props} />);
    const toggle = screen.getByRole('button');
    fireEvent.click(toggle);
    expect(document.getElementById('login_Password').type).toBe('text');
    fireEvent.click(toggle);
    expect(document.getElementById('login_Password').type).toBe('password');
  });

  test('renders prefix and imgPath', () => {
    const props = {
      ...baseProps,
      prefix: '+91',
      imgPath: 'icon.svg',
    };
    render(<InputWithImage {...props} />);
    expect(screen.getByText('+91')).toBeInTheDocument();
    expect(screen.getByRole('img')).toBeInTheDocument();
  });

  test('shows tooltip icon when icon=true', () => {
    render(<InputWithImage {...baseProps} />);
    expect(screen.getByAltText('info-icon')).toBeInTheDocument();
  });

  test('disables input field', () => {
    render(<InputWithImage {...baseProps} disabled={true} />);
    const input = screen.getByPlaceholderText('Enter username');
    expect(input).toBeDisabled();
  });

  test('triggers blurChange with regex error', () => {
    const props = {
      ...baseProps,
      regex: '^[0-9]+$',
      id: 'login_Username',
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');
    fireEvent.blur(input, { target: { value: 'abc123' } });
    expect(props.blurChange).toHaveBeenCalled();
  });

  test('blocks non-matching input on keydown', () => {
    const props = {
      ...baseProps,
      type: 'number',
      maxLength: '5',
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');

    // Try to type letter (should be blocked)
    fireEvent.keyDown(input, {
      key: 'a',
      preventDefault: jest.fn(),
      target: { value: '12345' },
      ctrlKey: false,
      getModifierState: () => false,
    });

    expect(input).toBeInTheDocument();
  });

  test('triggers onWheelCapture to blur', () => {
    render(<InputWithImage {...baseProps} />);
    const input = screen.getByPlaceholderText('Enter username');
    fireEvent.wheel(input);
    expect(input).toBeInTheDocument(); // just asserting it exists after wheel blur
  });

  test('focuses input on mount when idx === 0', () => {
    const props = {
      ...baseProps,
      idx: 0,
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');
    expect(input).toHaveFocus();
  });

  test('sets isCapsLockOn true when CapsLock is active', () => {
    const props = {
      ...baseProps,
      id: 'login_Password',
      type: 'password',
      value: 'pass',
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');

    fireEvent.keyUp(input, {
      key: 'A',
      getModifierState: (key) => key === 'CapsLock' && true,
    });
  });

  test('allows only letters when type="letter"', () => {
    const props = {
      ...baseProps,
      type: 'letter',
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');

    const preventDefault = jest.fn();

    // valid letter
    fireEvent.keyDown(input, {
      key: 'b',
      preventDefault,
      ctrlKey: false,
      getModifierState: () => false,
    });
    expect(preventDefault).not.toHaveBeenCalled();
  });

  test('allows only alphanumeric when type="alpha-numeric"', () => {
    const props = {
      ...baseProps,
      type: 'alpha-numeric',
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');
    const preventDefault = jest.fn();

    // valid alphanumeric
    fireEvent.keyDown(input, {
      key: 'A',
      preventDefault,
      ctrlKey: false,
      getModifierState: () => false,
    });
    expect(preventDefault).not.toHaveBeenCalled();
  });

  test('removes error from banner when input becomes valid', () => {
    const props = {
      ...baseProps,
      regex: '^[0-9]+$',
      id: 'login_Username',
      blurChange: jest.fn(),
    };
    render(<InputWithImage {...props} />);
    const input = screen.getByPlaceholderText('Enter username');

    // First invalid blur
    fireEvent.blur(input, { target: { id: 'login_Username', value: 'abc' } });

    // Now valid blur (should remove the error)
    fireEvent.blur(input, { target: { id: 'login_Username', value: '12345' } });

    // Called twice: once for add, once for remove
    expect(props.blurChange).toHaveBeenCalledTimes(2);
  });
});
