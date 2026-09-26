package ro.cristivoicu.springbootrestless.example.entity.department;

import dev.cerbos.sdk.CerbosBlockingClient;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosResourceAttributesMapper;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultPatchDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultSoftDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Optional;

/**
 * Stage 3 proof (runtime registration): a second entity exposed purely by writing its
 * Mapper/resource bean - no hand-written controllers, no hand-written {@code *DataSource}
 * classes at all.
 * <p>
 * Stage 1 proof (default CUD) + later step (default read): every verb is the framework's
 * default, reflection-based implementation. Department declares no {@code *DataSource} classes
 * of its own - only the response {@link DepartmentMapper} stays hand-written, deliberately (see
 * {@link Mapper}'s javadoc).
 * <p>
 * No longer the compile-time-generated tier's live example (see {@code Project}'s javadoc for
 * why): {@code policies/department.yaml} needs a real guard too, so this moved onto the "manual,
 * runtime defaults, no codegen" tier it was always demonstrating on the CUD side anyway - only
 * {@link #getAuthorizationGuard()} is new here.
 * <p>
 * Also this app's one demo of opt-in {@code PATCH} ({@link #getPatchDataSource()}, reflective
 * default) and of {@link ro.cristivoicu.springbootrestless.datasource.SoftDeletable} ({@link
 * #deleteDataSource} below is a {@code DefaultSoftDeleteDataSource}, not the hard-deleting
 * default) - both entirely opt-in additions on top of the same manual tier, no new files beyond
 * {@link DepartmentPatchModel} and {@link Department} implementing the marker interface.
 */
@Component
@RestlessResource(basePath = "/departments")
public class DepartmentRestlessResource extends RestlessResourceHandler<Department, Long> {

    private final CreateDataSource<Department, Long, DepartmentCreateModel> createDataSource;
    private final ReadDataSource<Department, Long, DepartmentSearchDto> readDataSource;
    private final UpdateDataSource<Department, Long, DepartmentUpdateModel> updateDataSource;
    private final DeleteDataSource<Department, Long, ?> deleteDataSource;
    private final PatchDataSource<Department, Long, DepartmentPatchModel> patchDataSource;
    private final DepartmentMapper mapper;
    private final CerbosBlockingClient cerbosClient;

    public DepartmentRestlessResource(DepartmentRepository repository, DepartmentMapper mapper,
                                       CerbosBlockingClient cerbosClient) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Department.class, DepartmentCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, DepartmentSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, DepartmentUpdateModel.class);
        this.deleteDataSource = new DefaultSoftDeleteDataSource<>(repository, Long.class);
        this.patchDataSource = new DefaultPatchDataSource<>(repository, DepartmentPatchModel.class);
        this.mapper = mapper;
        this.cerbosClient = cerbosClient;
    }

    @Override
    protected CreateDataSource<Department, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Department, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Department, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Department, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Department, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Department, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Department, ?> getSelectMapper() {
        return mapper;
    }

    // getSpecification() intentionally not overridden: RestlessResourceHandler's default
    // (equality-match on populated DepartmentSearchDto fields) is behavior-identical to the
    // hand-written StringUtils.hasText(name) check this used to have.

    @Override
    public Optional<PatchDataSource<Department, Long, ?>> getPatchDataSource() {
        return Optional.of(patchDataSource);
    }

    /**
     * {@code policies/department.yaml}: admin has full control; every other authenticated role
     * (manager, employee) can read but not write. No condition needed - it's a role-only policy,
     * not scoped to any resource attribute - so {@link CerbosResourceAttributesMapper#none()} is
     * enough; there's nothing for {@code scope()}'s query plan to restrict on either.
     */
    @Override
    protected AuthorizationGuard<Department> getAuthorizationGuard() {
        return new CerbosAuthorizationGuard<>(cerbosClient, "department", Department::getId,
                CerbosResourceAttributesMapper.none());
    }
}
