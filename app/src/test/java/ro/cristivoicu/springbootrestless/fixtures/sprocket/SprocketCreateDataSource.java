package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

/**
 * Escape-hatch proof: referenced via {@code @RestlessEntity(createDataSource =
 * SprocketCreateDataSource.class)} on {@link Sprocket}, so the generated {@code
 * SprocketRestlessResource} injects this hand-written bean instead of generating a {@code
 * DefaultCreateDataSource} call - something the default (a plain field-by-field copy) can't
 * express: defaulting a blank description instead of leaving it blank.
 */
@Component
public class SprocketCreateDataSource extends CreateDataSource<Sprocket, Long, SprocketCreateModel> {

    private static final String DEFAULT_DESCRIPTION = "No description provided";

    protected SprocketCreateDataSource(SprocketRepository repository) {
        super(repository);
    }

    @Override
    public Sprocket create(SprocketCreateModel createDto) {
        Sprocket sprocket = new Sprocket();
        sprocket.setName(createDto.getName());
        sprocket.setDescription(createDto.getDescription() == null || createDto.getDescription().isBlank()
                ? DEFAULT_DESCRIPTION
                : createDto.getDescription());
        return specificationRepository.save(sprocket);
    }
}
