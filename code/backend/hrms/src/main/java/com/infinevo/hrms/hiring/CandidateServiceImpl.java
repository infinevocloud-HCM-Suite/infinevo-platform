package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link CandidateService} (W-112).
 */
@Service
@Transactional
public class CandidateServiceImpl implements CandidateService {

    private final CandidateRepository candidateRepository;

    @Autowired
    public CandidateServiceImpl(CandidateRepository candidateRepository) {
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidateRepository must not be null");
    }

    @Override
    public CandidateResponse createCandidate(UUID tenantId, CandidateRequest request, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        String email = request.email().trim().toLowerCase();
        if (candidateRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new HiringConflictException("Candidate with email already exists: " + email);
        }

        Candidate candidate = new Candidate();
        candidate.setTenantId(tenantId);
        candidate.setFirstName(request.firstName().trim());
        candidate.setLastName(request.lastName().trim());
        candidate.setEmail(email);
        candidate.setPhone(request.phone());
        candidate.setCurrentCompany(request.currentCompany());
        candidate.setCurrentTitle(request.currentTitle());
        candidate.setExperienceYears(request.experienceYears());
        candidate.setNoticePeriodDays(request.noticePeriodDays());
        candidate.setResumeDocumentId(request.resumeDocumentId());
        candidate.setCreatedBy(createdBy != null ? createdBy : "system");
        candidate.setUpdatedBy(candidate.getCreatedBy());

        Candidate saved = candidateRepository.save(candidate);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(UUID tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        Candidate candidate = candidateRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Candidate not found: " + id));
        return toResponse(candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> listCandidates(UUID tenantId, String query) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<Candidate> candidates = candidateRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            return candidates.stream()
                    .filter(c -> c.getFirstName().toLowerCase().contains(q)
                            || c.getLastName().toLowerCase().contains(q)
                            || c.getEmail().toLowerCase().contains(q)
                            || (c.getCurrentCompany() != null
                                    && c.getCurrentCompany().toLowerCase().contains(q))
                            || (c.getCurrentTitle() != null
                                    && c.getCurrentTitle().toLowerCase().contains(q)))
                    .map(this::toResponse)
                    .toList();
        }
        return candidates.stream().map(this::toResponse).toList();
    }

    private CandidateResponse toResponse(Candidate c) {
        return new CandidateResponse(
                c.getId(),
                c.getFirstName(),
                c.getLastName(),
                c.getEmail(),
                c.getPhone(),
                c.getCurrentCompany(),
                c.getCurrentTitle(),
                c.getExperienceYears(),
                c.getNoticePeriodDays(),
                c.getResumeDocumentId(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
