package com.infinevo.payroll.taxdeclaration.deductions;

import com.infinevo.payroll.taxdeclaration.exception.ReferenceDataMissingException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Reader for statutory Section 6A investment catalogue from {@code reference.section6a_item_master} (W-32.3).
 */
@Component
public class Section6AItemReader {

    private final JdbcTemplate jdbcTemplate;

    public record Section6AItem(
            UUID id,
            String sectionCode,
            String category,
            String name,
            String description,
            BigDecimal maxLimit,
            String categoryGroupCode,
            boolean is80c,
            boolean is80d,
            boolean isOtherSection,
            boolean isAllowedInNewRegime,
            int displayOrder,
            boolean isActive) {}

    private static final RowMapper<Section6AItem> ROW_MAPPER = (rs, rowNum) -> mapItem(rs);

    public Section6AItemReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Section6AItem> activeItems(String regime) {
        if ("NEW".equalsIgnoreCase(regime)) {
            String sql =
                    """
                    SELECT id, section_code, category, name, description, max_limit, category_group_code,
                           is_80c, is_80d, is_other_section, is_allowed_in_new_regime, display_order, is_active
                      FROM reference.section6a_item_master
                     WHERE is_active = true AND is_allowed_in_new_regime = true
                     ORDER BY display_order ASC
                    """;
            return jdbcTemplate.query(sql, ROW_MAPPER);
        } else {
            String sql =
                    """
                    SELECT id, section_code, category, name, description, max_limit, category_group_code,
                           is_80c, is_80d, is_other_section, is_allowed_in_new_regime, display_order, is_active
                      FROM reference.section6a_item_master
                     WHERE is_active = true
                     ORDER BY display_order ASC
                    """;
            return jdbcTemplate.query(sql, ROW_MAPPER);
        }
    }

    public Optional<Section6AItem> findById(UUID itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        String sql =
                """
                SELECT id, section_code, category, name, description, max_limit, category_group_code,
                       is_80c, is_80d, is_other_section, is_allowed_in_new_regime, display_order, is_active
                  FROM reference.section6a_item_master
                 WHERE id = ?
                """;
        List<Section6AItem> items = jdbcTemplate.query(sql, ROW_MAPPER, itemId);
        return items.stream().findFirst();
    }

    public Section6AItem require(UUID itemId) {
        return findById(itemId)
                .orElseThrow(() -> new WindowValidationException("Section 6A item not found: " + itemId));
    }

    public BigDecimal groupCap(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return null;
        }
        String sql =
                """
                SELECT max_limit
                  FROM reference.section6a_item_master
                 WHERE category_group_code = ? AND is_active = true
                 ORDER BY display_order ASC
                """;
        List<BigDecimal> limits = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), groupCode);
        if (limits.isEmpty()) {
            throw new ReferenceDataMissingException("reference.section6a_item_master", groupCode);
        }
        BigDecimal first = limits.get(0);
        if (first == null) {
            throw new ReferenceDataMissingException("reference.section6a_item_master", groupCode);
        }
        for (BigDecimal limit : limits) {
            if (limit == null || first.compareTo(limit) != 0) {
                throw new IllegalStateException(String.format(
                        "Group '%s' has conflicting caps among its members: %s vs %s", groupCode, first, limit));
            }
        }
        return first;
    }

    private static Section6AItem mapItem(ResultSet rs) throws SQLException {
        return new Section6AItem(
                rs.getObject("id", UUID.class),
                rs.getString("section_code"),
                rs.getString("category"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("max_limit"),
                rs.getString("category_group_code"),
                rs.getBoolean("is_80c"),
                rs.getBoolean("is_80d"),
                rs.getBoolean("is_other_section"),
                rs.getBoolean("is_allowed_in_new_regime"),
                rs.getInt("display_order"),
                rs.getBoolean("is_active"));
    }
}
