package com.infinevo.core.report.source;

/**
 * The query hints every report source sets, so a result is read from PostgreSQL in batches rather than
 * all at once (W-23.1).
 *
 * <p>Without a fetch size the PostgreSQL driver reads the whole result into memory before handing
 * back the first row, whatever the caller does with it — so a lazy {@code Stream} over an unhinted
 * query streams nothing. The driver honours the fetch size only inside a transaction, which
 * {@code ExportServiceImpl} opens around the read.
 */
final class Streaming {

    static final String FETCH_SIZE = "org.hibernate.fetchSize";
    static final String READ_ONLY = "org.hibernate.readOnly";

    /** Rows per round trip. */
    static final int FETCH_ROWS = 500;

    private Streaming() {}
}
