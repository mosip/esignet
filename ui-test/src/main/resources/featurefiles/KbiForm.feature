Feature: Esignet KBI Login Form
  This feature file is for verifying the Knowledge-Based Identity (KBI) login form.
  Covers only the test cases from ES-2058 that don't depend on the exact schema-driven
  field rendering (which is produced at runtime by an external package not vendored
  in this repo) - schema-content-dependent cases (field types, regex validation,
  mandatory-flag/asterisk rendering, per-field inline errors, multi-language labels,
  and schema-reconfiguration cases) are handled separately.
