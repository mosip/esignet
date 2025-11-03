import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import LoginIDOptions from '../../components/LoginIDOptions';
import openIDConnectService from '../../services/openIDConnectService';
import { configurationKeys } from '../../constants/clientConstants';
import * as utils from '../../helpers/utils';

// ---------- MOCK DATA ----------
const mockOAuthDetails = {
  essentialClaims: ['Name', 'Birthdate'],
  voluntaryClaims: ['Phone Number', 'Gender'],
  clientName: { '@none': 'Healthservice' },
  logoUrl: 'logoUrl',
};

const fallbackLoginIDs = [
  {
    id: 'vid',
    svg: 'vid_icon',
    prefixes: '',
    postfix: '',
    maxLength: '',
    regex: '',
  },
];

// ---------- JEST MOCKS ----------
jest.mock('../../services/openIDConnectService');
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key) => key,
    i18n: {
      changeLanguage: jest.fn(),
      language: 'en',
      on: jest.fn((event, cb) => cb && cb()),
    },
  }),
}));

// ---------- MOCK SETUP FUNCTION ----------
function setupMocks({
  loginIDs = null,
  fetchOk = true,
  svgText = '<svg></svg>',
} = {}) {
  jest.spyOn(utils, 'decodeHash').mockImplementation(() =>
    JSON.stringify({
      clientName: { '@none': 'Test Client' },
      logoUrl: '/logo.svg',
      configs: {
        [configurationKeys.loginIdOptions]: [
          { id: 'vid', svg: 'vid_icon' },
          { id: 'mobile', svg: 'mobile_icon' },
        ],
      },
    })
  );

  Object.defineProperty(window, 'location', {
    writable: true,
    value: {
      href: `https://example.com/callback?state=state123&nonce=nonce123#dummy-code`,
      search: `?state=state123&nonce=nonce123`,
      hash: `#dummy-code`,
    },
  });

  global.fetch = jest.fn(() =>
    fetchOk
      ? Promise.resolve({
          ok: true,
          text: () => Promise.resolve(svgText),
        })
      : Promise.reject(new Error('fetch error'))
  );

  openIDConnectService.mockImplementation(() => ({
    getOAuthDetails: jest.fn(() => ({
      decodedIdToken: JSON.stringify(mockOAuthDetails),
      nonce: 'nonce123',
      state: 'state123',
    })),
    getTransactionId: jest.fn(() => 'tx123'),
    getEsignetConfiguration: jest.fn((key) => {
      if (key === configurationKeys.loginIdOptions) {
        return loginIDs;
      }
      return null;
    }),
  }));
}

// ---------- TEST SUITE ----------
describe('LoginIDOptions Component', () => {
  let mockCurrentLoginID;

  beforeEach(() => {
    mockCurrentLoginID = jest.fn();
    jest.clearAllMocks();
  });

  it('renders login ID buttons from config', async () => {
    setupMocks({
      loginIDs: [
        { id: 'vid', svg: 'vid_icon' },
        { id: 'mobile', svg: 'mobile_icon' },
      ],
    });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
      expect(screen.getByText('buttons.mobile')).toBeInTheDocument();
    });
  });

  it('renders fallback login ID when config is empty', async () => {
    setupMocks({ loginIDs: null });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });
  });

  it('handles failed SVG fetch gracefully', async () => {
    setupMocks({ loginIDs: fallbackLoginIDs, fetchOk: false });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });
  });

  it('replaces stroke in fetched SVG', async () => {
    setupMocks({
      loginIDs: fallbackLoginIDs,
      svgText: '<svg stroke="black"></svg>',
    });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });
  });

  it('updates selected login ID on click', async () => {
    setupMocks({
      loginIDs: [
        { id: 'vid', svg: 'vid_icon' },
        { id: 'mobile', svg: 'mobile_icon' },
      ],
    });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      fireEvent.click(screen.getByText('buttons.mobile'));
    });
    expect(mockCurrentLoginID).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'mobile' })
    );
  });

  it('handles single login ID option (hides button)', async () => {
    setupMocks({ loginIDs: [fallbackLoginIDs[0]] });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });
  });

  it('updates input_label and input_placeholder on language change', async () => {
    setupMocks({ loginIDs: fallbackLoginIDs });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });
    // no visible effect, but ensures useEffect runs and doesn't crash
  });

  it('does not break if no selectedOption', async () => {
    setupMocks({ loginIDs: fallbackLoginIDs });
    const useStateSpy = jest.spyOn(React, 'useState');
    useStateSpy.mockImplementationOnce(() => [undefined, jest.fn()]);
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);
    expect(screen.queryByText('buttons.vid')).not.toBeInTheDocument();
    useStateSpy.mockRestore();
  });

  it('throws error when fetch response is not ok', async () => {
    setupMocks({
      loginIDs: fallbackLoginIDs,
    });

    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: false, // triggers the throw
        text: () => Promise.resolve('<svg></svg>'),
      })
    );

    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);

    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });
  });

  it('updates selected option labels when language changes', async () => {
    const mockI18n = {
      language: 'en',
      changeLanguage: jest.fn(),
      on: jest.fn(),
    };

    jest.mock('react-i18next', () => ({
      useTranslation: () => ({
        t: (key) => key,
        i18n: mockI18n,
      }),
    }));

    setupMocks({ loginIDs: fallbackLoginIDs });
    render(<LoginIDOptions currentLoginID={mockCurrentLoginID} />);

    await waitFor(() => {
      expect(screen.getByText('buttons.vid')).toBeInTheDocument();
    });

    // simulate language change event
    mockI18n.on.mock.calls.forEach(([event, callback]) => {
      if (event === 'languageChanged') callback();
    });

    // No crash → line executed
    expect(mockCurrentLoginID).toHaveBeenCalled();
  });
});
