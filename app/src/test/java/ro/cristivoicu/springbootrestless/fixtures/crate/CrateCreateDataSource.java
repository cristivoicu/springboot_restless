package ro.cristivoicu.springbootrestless.fixtures.crate;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

@Component
public class CrateCreateDataSource extends CreateDataSource<Crate, Long, CrateCreateModel> {

    private final PalletRepository palletRepository;

    public CrateCreateDataSource(CrateRepository repository, PalletRepository palletRepository) {
        super(repository);
        this.palletRepository = palletRepository;
    }

    @Override
    public Crate create(CrateCreateModel createDto) {
        Crate crate = new Crate();
        crate.setName(createDto.getName());
        if (createDto.getPalletId() != null) {
            // getReference, not findById: a managed proxy is exactly what keeps "pallet" lazy -
            // CrateMapper reading through it inside the write's own transaction is the point.
            crate.setPallet(palletRepository.getReferenceById(createDto.getPalletId()));
        }
        return specificationRepository.save(crate);
    }
}
