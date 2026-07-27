package utils;

/**
 * Tracks optional biometric steps that depend on env-specific config values.
 */
public final class BiometricStepContext {

	private static final ThreadLocal<Boolean> OPTIONAL_STEP_SKIPPED = ThreadLocal.withInitial(() -> false);

	private BiometricStepContext() {
	}

	public static void markOptionalStepSkipped() {
		OPTIONAL_STEP_SKIPPED.set(true);
	}

	public static void clearOptionalStepSkipped() {
		OPTIONAL_STEP_SKIPPED.set(false);
	}

	public static boolean wasOptionalStepSkipped() {
		return OPTIONAL_STEP_SKIPPED.get();
	}
}
