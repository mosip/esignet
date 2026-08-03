package keymanager

// ResolveSignKeyAlias exposes (*Service).resolveSignKeyAlias as a method
// expression so keymanager_test can exercise the hierarchy resolution logic
// directly, precisely, without going through the full GenerateMasterKey flow.
var ResolveSignKeyAlias = (*Service).resolveSignKeyAlias
