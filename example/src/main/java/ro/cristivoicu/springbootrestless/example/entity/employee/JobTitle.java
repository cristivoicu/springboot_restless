package ro.cristivoicu.springbootrestless.example.entity.employee;

/**
 * A fixed career ladder, ordered lowest to highest - the actual point of the {@code promote}
 * write action ({@link EmployeeRestlessResource#getCustomWriteActions()}): a full-replace
 * {@code PUT} on {@link Employee#getJobTitle()} could set this field to any value at all,
 * including a demotion or a same-rank no-op change dressed up as a "promotion". {@link
 * #isPromotionFrom(JobTitle)} is the one rule an intent-carrying command can enforce that a
 * field-level write never could: the transition itself, not just the destination value, is what's
 * validated. New employees default to {@link #ASSOCIATE} (see {@code EmployeeCreateDataSource}) -
 * {@code null} is reserved for "not yet on the ladder at all", distinct from "at the bottom of
 * it".
 */
public enum JobTitle {
    ASSOCIATE, ENGINEER, SENIOR_ENGINEER, STAFF_ENGINEER, PRINCIPAL_ENGINEER;

    /**
     * Whether {@code this} is strictly higher on the ladder than {@code current} - {@code null}
     * (not yet on the ladder) counts as lower than every real rank, so a first promotion from
     * "unset" is always legal.
     */
    public boolean isPromotionFrom(JobTitle current) {
        return current == null || this.ordinal() > current.ordinal();
    }
}
