import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import PopoverContainer from '../../common/Popover';

// ✅ Mock implementation of @radix-ui/react-popover
jest.mock('@radix-ui/react-popover', () => ({
  __esModule: true,
  Root: ({ children }) => <div>{children}</div>,
  Trigger: ({ children }) => <div>{children}</div>,
  Portal: ({ children }) => <div>{children}</div>,
  Content: ({ children }) => <div>{children}</div>,
  Arrow: () => <div>Mock Arrow</div>,
}));

describe('PopoverContainer', () => {
  it('renders with provided props', () => {
    const child = <button>Trigger Button</button>;
    const content = 'Popover Content';
    const position = 'top';
    const contentSize = 'large';
    const contentClassName = 'custom-class';

    render(
      <PopoverContainer
        child={child}
        content={content}
        position={position}
        contentSize={contentSize}
        contentClassName={contentClassName}
      />
    );

    // Assert that the trigger button is rendered
    expect(screen.getByText('Trigger Button')).toBeInTheDocument();

    // Simulate hover to trigger popover
    fireEvent.mouseEnter(screen.getByText('Trigger Button'));

    // Assert that the popover content is rendered
    expect(screen.getByText('Popover Content')).toBeInTheDocument();
    expect(screen.getByText('Mock Arrow')).toBeInTheDocument();

    // Example: Check for specific CSS classes or styles applied
    expect(screen.getByText('Popover Content')).toHaveClass('large');
  });

  it('does not render popover initially', () => {
    const child = <button>Trigger Button</button>;
    const content = '';

    render(
      <PopoverContainer
        child={child}
        content={content}
        position="top"
        contentSize="medium"
        contentClassName="initially-hidden"
      />
    );

    // Assert that the popover content is not initially rendered
    expect(screen.queryByText('Popover Content')).not.toBeInTheDocument();
  });
});
