export const mockOAuthDetails = {
  essentialClaims: ["Name", "Birthdate"],
  voluntaryClaims: ["Phone Number", "Gender"],
  clientName: { "@none": "Healthservice" },
  logoUrl: "logoUrl",
};

export const mockAuthService = {
  getClaimDetails: jest.fn(),
  prepareSignupRedirect: jest.fn(),
};

export const mockResponse = {
  response: {
    claimStatus: [
      { claim: "Name", available: true, verified: true },
      { claim: "Gender", available: false, verified: false },
    ],
    profileUpdateRequired: true,
    consentAction: "CAPTURE",
  },
  errors: [],
};
