package s15_integrated_harness;

import harness.Permission;

/** Keeps the s15 entry point focused on wiring, not policy details. */
final class PermissionDefaults {
    private PermissionDefaults() {
    }

    static Permission.Policy policy() {
        return Permission.rules(Permission.defaultRules());
    }
}
