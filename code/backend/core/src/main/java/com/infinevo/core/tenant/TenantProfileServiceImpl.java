package com.infinevo.core.tenant;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TenantProfileService} over {@code core.tenant} (W-73.1).
 *
 * <p>Plain JDBC, as {@link TenantServiceImpl} writes the row: there is no tenant entity, and the two columns
 * {@code V160} adds do not justify one.
 *
 * <p><strong>The logo document is looked up in the bound tenant.</strong> The foreign key on
 * {@code logo_document_id} is checked by PostgreSQL as the table owner, with row security off, so the database
 * would accept a tenant pointing at another tenant's file. The lookup here runs as {@code app_user} under
 * row-level security, so another tenant's document is simply not found — the same answer as a document that
 * never existed, so the answer cannot be used to enumerate ids.
 *
 * <p>The link service arrives through a provider because a context without the document store (the guard test
 * slices) must still start; it then answers with no link, and the header shows initials.
 */
@Service
public class TenantProfileServiceImpl implements TenantProfileService {

    private static final Logger log = LoggerFactory.getLogger(TenantProfileServiceImpl.class);

    /** Spec section 2: the longest tagline. {@code ck_tenant_tagline_length} is the backstop. */
    static final int TAGLINE_MAX_LENGTH = 80;

    /** Spec section 2: the largest logo. The client resizes before upload; this refuses what slips past. */
    static final long LOGO_MAX_BYTES = 512L * 1024;

    /** The image types a header can show. SVG is not a {@code DocumentType}, so it is not accepted here. */
    static final Set<String> LOGO_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    private static final int MAX_ACTOR = 100;

    private final JdbcTemplate jdbcTemplate;
    private final Supplier<DocumentLinkService> linkService;

    @Autowired
    public TenantProfileServiceImpl(JdbcTemplate jdbcTemplate, ObjectProvider<DocumentLinkService> linkService) {
        this(jdbcTemplate, linkService::getIfAvailable);
    }

    TenantProfileServiceImpl(JdbcTemplate jdbcTemplate, Supplier<DocumentLinkService> linkService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.linkService = Objects.requireNonNull(linkService, "linkService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public TenantProfileResponse current() {
        UUID tenantId = TenantContext.require();
        TenantBranding branding = read(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
        return toResponse(branding);
    }

    @Override
    @Transactional
    public TenantProfileResponse update(TenantProfileRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();

        String tagline = cleanTagline(request.tagline());
        UUID logoDocumentId = request.logoDocumentId();
        if (logoDocumentId != null) {
            requireLogo(tenantId, logoDocumentId);
        }

        int updated = jdbcTemplate.update(
                "UPDATE core.tenant SET tagline = ?, logo_document_id = ?, updated_at = now(), updated_by = ?"
                        + " WHERE tenant_id = ?",
                tagline,
                logoDocumentId,
                currentActor(),
                tenantId);
        if (updated != 1) {
            throw new TenantNotFoundException(tenantId);
        }
        log.info("Tenant {} profile updated: tagline {}, logo {}", tenantId, tagline != null, logoDocumentId);
        return current();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantBranding> branding() {
        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            return Optional.empty();
        }
        return read(tenantId.get());
    }

    /** The three columns in one query, under the bound tenant's row-level security. */
    private Optional<TenantBranding> read(UUID tenantId) {
        List<TenantBranding> rows = jdbcTemplate.query(
                "SELECT name, tagline, logo_document_id FROM core.tenant WHERE tenant_id = ?",
                (rs, rowNum) -> {
                    UUID logoId = rs.getObject("logo_document_id", UUID.class);
                    return new TenantBranding(rs.getString("name"), rs.getString("tagline"), logoId, logoUrl(logoId));
                },
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * A signed link to the logo, or {@code null}: no logo, no link service in this runtime, or a logo row that
     * is gone (soft-deleted since it was chosen). None of those takes the header down.
     */
    private String logoUrl(UUID logoDocumentId) {
        if (logoDocumentId == null) {
            return null;
        }
        DocumentLinkService links = linkService.get();
        if (links == null) {
            return null;
        }
        try {
            return links.signedLink(logoDocumentId, LOGO_LINK_TTL).url();
        } catch (DocumentService.NotFoundException e) {
            log.warn("Tenant logo document {} is no longer live; the header falls back to initials", logoDocumentId);
            return null;
        }
    }

    private static TenantProfileResponse toResponse(TenantBranding branding) {
        return new TenantProfileResponse(
                branding.name(), branding.tagline(), branding.logoDocumentId(), branding.logoUrl());
    }

    /** Trimmed; blank is "no tagline"; over the limit is refused. */
    static String cleanTagline(String tagline) {
        if (tagline == null) {
            return null;
        }
        String trimmed = tagline.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > TAGLINE_MAX_LENGTH) {
            throw new IllegalArgumentException("tagline must be at most " + TAGLINE_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    /** The document must be live in the bound tenant, a {@code TENANT_LOGO}, an image, and under the cap. */
    private void requireLogo(UUID tenantId, UUID logoDocumentId) {
        List<LogoRow> rows = jdbcTemplate.query(
                "SELECT kind, content_type, size_bytes FROM core.document"
                        + " WHERE id = ? AND tenant_id = ? AND is_deleted = false",
                (rs, rowNum) ->
                        new LogoRow(rs.getString("kind"), rs.getString("content_type"), rs.getLong("size_bytes")),
                logoDocumentId,
                tenantId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No document " + logoDocumentId + " in this tenant");
        }
        LogoRow row = rows.get(0);
        if (!DocumentKind.TENANT_LOGO.name().equals(row.kind())) {
            throw new IllegalArgumentException("logoDocumentId must name a TENANT_LOGO document, not " + row.kind());
        }
        if (!LOGO_CONTENT_TYPES.contains(row.contentType())) {
            throw new IllegalArgumentException("The logo must be a PNG or JPG image");
        }
        if (row.sizeBytes() > LOGO_MAX_BYTES) {
            throw new IllegalArgumentException("The logo must be at most " + LOGO_MAX_BYTES + " bytes");
        }
    }

    private record LogoRow(String kind, String contentType, long sizeBytes) {}

    /** The authenticated subject for {@code updated_by}; {@code system} when there is none. */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
