package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * W-21 spec section 7 — a store with no employee lands under the tenant; 10 MB plus one byte is
 * refused; {@code application/zip} is refused; each of the five types is accepted. Plus the rules
 * that make those true: the bytes are checked against the name, the employee is looked up in the
 * bound tenant, and the blob is written before the row.
 *
 * <p>No Spring context and no storage — {@code docs/CONVENTIONS.md} section 3. The round trip against
 * Azurite is {@code DocumentUploadIT}.
 */
class DocumentServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<String> ALL_TYPES = List.of("pdf", "jpg", "png", "xlsx", "csv");

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] XLSX = {'P', 'K', 0x03, 0x04, 0x14, 0, 0x06, 0};
    private static final byte[] CSV = "employee_number,name\nA-001,Asha\n".getBytes(StandardCharsets.UTF_8);

    private DocumentRepository documents;
    private EmployeeRepository employees;
    private RecordingBlobStorage blobs;
    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        employees = mock(EmployeeRepository.class);
        blobs = spy(new RecordingBlobStorage());
        when(documents.saveAndFlush(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        service = service(ALL_TYPES);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    // ── where it lands

    @Test
    @DisplayName("With no employee the path is {tenantId}/tenant/{kind}/{id} - an export lands under the tenant")
    void noEmployeeLandsUnderTheTenant() {
        UUID id = service.store(DocumentKind.EXPORT, null, "employees.csv", in(CSV));

        assertThat(blobs.uploads).hasSize(1);
        assertThat(blobs.uploads.get(0).path()).isEqualTo(TENANT + "/tenant/EXPORT/" + id);
        assertThat(blobs.uploads.get(0).container()).isEqualTo("documents");

        Document row = savedRow();
        assertThat(row.getId()).isEqualTo(id);
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getEmployeeId()).isNull();
        assertThat(row.getBlobPath()).isEqualTo(TENANT + "/tenant/EXPORT/" + id);
    }

    @Test
    @DisplayName("With an employee in the bound tenant the path is {tenantId}/{employeeId}/{kind}/{id}")
    void employeeDocumentLandsUnderTheEmployee() {
        UUID employee = UUID.randomUUID();
        when(employees.findByIdAndTenantIdAndDeletedFalse(employee, TENANT))
                .thenReturn(Optional.of(mock(Employee.class)));

        UUID id = service.store(DocumentKind.EMPLOYEE_DOCUMENT, employee, "offer.pdf", in(PDF));

        assertThat(blobs.uploads.get(0).path()).isEqualTo(TENANT + "/" + employee + "/EMPLOYEE_DOCUMENT/" + id);
        assertThat(savedRow().getEmployeeId()).isEqualTo(employee);
    }

    @Test
    @DisplayName("An employee not live in the bound tenant is a field error, and nothing is written")
    void employeeOfAnotherTenantIsRefused() {
        UUID elsewhere = UUID.randomUUID();
        when(employees.findByIdAndTenantIdAndDeletedFalse(elsewhere, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, elsewhere, "offer.pdf", in(PDF)))
                .isInstanceOfSatisfying(DocumentService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("employeeId"));
        assertNothingWritten();
    }

    @Test
    @DisplayName("The row records the name, type, size and SHA-256 of what was uploaded")
    void rowDescribesTheFile() {
        service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF));

        Document row = savedRow();
        assertThat(row.getFileName()).isEqualTo("offer.pdf");
        assertThat(row.getContentType()).isEqualTo("application/pdf");
        assertThat(row.getSizeBytes()).isEqualTo(PDF.length);
        assertThat(row.getChecksumSha256())
                .isEqualTo(DocumentServiceImpl.sha256Hex(PDF))
                .hasSize(64);
        assertThat(row.getKind()).isEqualTo(DocumentKind.EMPLOYEE_DOCUMENT);
        assertThat(blobs.uploads.get(0).contentType()).isEqualTo("application/pdf");
        assertThat(blobs.uploads.get(0).content()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("The blob is written before the row, and a failed upload leaves no row")
    void blobBeforeRow() {
        service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF));

        InOrder order = inOrder(blobs, documents);
        order.verify(blobs).upload(any(), any(), any(byte[].class), any());
        order.verify(documents).saveAndFlush(any(Document.class));

        DocumentRepository untouched = mock(DocumentRepository.class);
        DocumentServiceImpl noStorage = new DocumentServiceImpl(
                untouched, employees, new UnconfiguredBlobStorage(), "documents", 1024, ALL_TYPES);
        assertThatThrownBy(() -> noStorage.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF)))
                .isInstanceOf(DocumentService.StorageUnavailableException.class);
        verify(untouched, never()).saveAndFlush(any());
    }

    // ── size

    @Test
    @DisplayName("Exactly 10 MB is accepted")
    void exactlyTheLimitIsAccepted() {
        byte[] tenMegabytes = padded(PDF, (int) DocumentServiceImpl.DEFAULT_MAX_BYTES);

        service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "big.pdf", in(tenMegabytes));

        assertThat(savedRow().getSizeBytes()).isEqualTo(DocumentServiceImpl.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("10 MB plus one byte is refused, and nothing is written")
    void oneByteOverTheLimitIsRefused() {
        byte[] tooBig = padded(PDF, (int) DocumentServiceImpl.DEFAULT_MAX_BYTES + 1);

        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "big.pdf", in(tooBig)))
                .isInstanceOf(DocumentService.TooLargeException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("An empty file is refused")
    void emptyFileIsRefused() {
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "empty.csv", in(new byte[0])))
                .isInstanceOf(DocumentService.ValidationException.class);
        assertNothingWritten();
    }

    // ── type

    static Stream<Arguments> acceptedTypes() {
        return Stream.of(
                Arguments.of("proof.pdf", PDF, "application/pdf"),
                Arguments.of("receipt.jpg", JPG, "image/jpeg"),
                Arguments.of("receipt.JPEG", JPG, "image/jpeg"),
                Arguments.of("scan.png", PNG, "image/png"),
                Arguments.of("sheet.xlsx", XLSX, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                Arguments.of("export.csv", CSV, "text/csv"));
    }

    @ParameterizedTest(name = "{0} is accepted as {2}")
    @MethodSource("acceptedTypes")
    @DisplayName("Each of the five types is accepted")
    void eachOfTheFiveTypesIsAccepted(String fileName, byte[] content, String contentType) {
        service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, fileName, in(content));

        assertThat(savedRow().getContentType()).isEqualTo(contentType);
    }

    @Test
    @DisplayName("A zip is refused - application/zip is not one of the five")
    void zipIsRefused() {
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "archive.zip", in(XLSX)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("A name with no extension is refused")
    void noExtensionIsRefused() {
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "README", in(CSV)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "trailing.", in(CSV)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
    }

    @Test
    @DisplayName("Content that is not what the name says is refused - a PNG called .pdf is not a PDF")
    void contentMustMatchTheName() {
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "proof.pdf", in(PNG)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "scan.png", in(JPG)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("A csv carrying a NUL byte is refused - it is a binary file wearing a text name")
    void binaryCsvIsRefused() {
        byte[] binary = {'a', ',', 'b', 0, 'c'};

        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "data.csv", in(binary)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
    }

    @Test
    @DisplayName("Configuration can narrow the accepted types")
    void configurationNarrowsTheTypes() {
        DocumentServiceImpl pdfOnly = service(List.of("pdf"));

        assertThatThrownBy(() -> pdfOnly.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "scan.png", in(PNG)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
    }

    @Test
    @DisplayName("The configured list is read as the comma-separated string application.yml writes")
    void configuredStringIsSplit() {
        DocumentServiceImpl fromYaml = new DocumentServiceImpl(
                documents, employees, blobs, "documents", DocumentServiceImpl.DEFAULT_MAX_BYTES, " pdf , png ");

        fromYaml.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "scan.png", in(PNG));
        assertThat(savedRow().getContentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> fromYaml.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "sheet.xlsx", in(XLSX)))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertThatThrownBy(() -> new DocumentServiceImpl(
                        documents, employees, blobs, "documents", DocumentServiceImpl.DEFAULT_MAX_BYTES, " , "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Configuration naming a type the store does not know refuses to start")
    void unknownConfiguredTypeRefusesToStart() {
        assertThatThrownBy(() -> service(List.of("pdf", "exe"))).isInstanceOf(IllegalArgumentException.class);
    }

    // ── the request itself

    @Test
    @DisplayName("Only the last path segment of the file name is kept")
    void pathIsStrippedFromTheName() {
        service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "C:\\fakepath\\proof.pdf", in(PDF));
        assertThat(savedRow().getFileName()).isEqualTo("proof.pdf");
    }

    @Test
    @DisplayName("No kind, no name or no content is a field error, and nothing is written")
    void missingPartsAreFieldErrors() {
        assertThatThrownBy(() -> service.store(null, null, "proof.pdf", in(PDF)))
                .isInstanceOfSatisfying(DocumentService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("kind"));
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "  ", in(PDF)))
                .isInstanceOfSatisfying(DocumentService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("fileName"));
        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "proof.pdf", null))
                .isInstanceOfSatisfying(DocumentService.ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("file"));
        assertNothingWritten();
    }

    @Test
    @DisplayName("With no tenant bound nothing is read or written")
    void noTenantNoStore() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "proof.pdf", in(PDF)))
                .isInstanceOf(IllegalStateException.class);
        assertNothingWritten();
    }

    // ── read and delete

    @Test
    @DisplayName("A document not live in the bound tenant is not found, for read and for delete")
    void unknownDocumentIsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(DocumentService.NotFoundException.class);
        assertThatThrownBy(() -> service.delete(missing)).isInstanceOf(DocumentService.NotFoundException.class);
    }

    @Test
    @DisplayName("Delete is soft - the row is flagged and saved, and the blob is not touched")
    void deleteIsSoft() {
        UUID id = UUID.randomUUID();
        Document document = new Document(
                id, TENANT, null, DocumentKind.EXPORT, "e.csv", "text/csv", 3, "documents", "p", "x".repeat(64), "t");
        when(documents.findByIdAndTenantIdAndDeletedFalse(id, TENANT)).thenReturn(Optional.of(document));

        service.delete(id);

        assertThat(document.isDeleted()).isTrue();
        verify(documents).save(document);
        assertThat(blobs.uploads).isEmpty();
    }

    // ── files the platform generates (decision D2)

    @Test
    @DisplayName("storeFile streams a generated file: same path, type check and checksum as an upload")
    void storeFileWritesAGeneratedFile(@TempDir Path dir) throws Exception {
        Path export = Files.write(dir.resolve("e.csv"), CSV);

        UUID id = service.storeFile(DocumentKind.EXPORT, null, "employees.csv", export);

        assertThat(blobs.uploads.get(0).path()).isEqualTo(TENANT + "/tenant/EXPORT/" + id);
        assertThat(blobs.uploads.get(0).content()).isEqualTo(CSV);
        Document row = savedRow();
        assertThat(row.getSizeBytes()).isEqualTo(CSV.length);
        assertThat(row.getChecksumSha256()).isEqualTo(DocumentServiceImpl.sha256Hex(CSV));
        assertThat(Files.exists(export)).as("the caller owns the file").isTrue();
    }

    @Test
    @DisplayName("A generated export may pass 10 MB - the system limit is its own")
    void systemKindsHaveTheHigherLimit(@TempDir Path dir) throws Exception {
        Path big = Files.write(dir.resolve("big.csv"), padded(CSV, (int) DocumentServiceImpl.DEFAULT_MAX_BYTES + 1));

        service.storeFile(DocumentKind.EXPORT, null, "big.csv", big);

        assertThat(savedRow().getSizeBytes()).isEqualTo(DocumentServiceImpl.DEFAULT_MAX_BYTES + 1);
    }

    @Test
    @DisplayName("An uploadable kind keeps 10 MB even through storeFile - the limit follows the kind, not the method")
    void uploadableKindsKeepTheUploadLimit(@TempDir Path dir) throws Exception {
        Path big = Files.write(dir.resolve("big.csv"), padded(CSV, (int) DocumentServiceImpl.DEFAULT_MAX_BYTES + 1));

        assertThatThrownBy(() -> service.storeFile(DocumentKind.EMPLOYEE_DOCUMENT, null, "big.csv", big))
                .isInstanceOf(DocumentService.TooLargeException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("A generated file over the system limit is refused, and nothing is written")
    void systemLimitIsEnforced(@TempDir Path dir) throws Exception {
        DocumentServiceImpl small = new DocumentServiceImpl(
                documents, employees, blobs, "documents", DocumentServiceImpl.DEFAULT_MAX_BYTES, 16, ALL_TYPES);
        Path export = Files.write(dir.resolve("e.csv"), CSV);

        assertThatThrownBy(() -> small.storeFile(DocumentKind.EXPORT, null, "e.csv", export))
                .isInstanceOf(DocumentService.TooLargeException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("storeFile checks the content against the name, as store does")
    void storeFileChecksTheContent(@TempDir Path dir) throws Exception {
        Path fake = Files.write(dir.resolve("fake.pdf"), PNG);

        assertThatThrownBy(() -> service.storeFile(DocumentKind.EXPORT, null, "fake.pdf", fake))
                .isInstanceOf(DocumentService.UnsupportedTypeException.class);
        assertNothingWritten();
    }

    // ── reading the bytes back

    @Test
    @DisplayName("open returns the metadata and the stored bytes of a live document")
    void openReturnsTheBytes() throws Exception {
        UUID id = service.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF));
        Document row = savedRow();
        when(documents.findByIdAndTenantIdAndDeletedFalse(id, TENANT)).thenReturn(Optional.of(row));

        DocumentService.DocumentContent content = service.open(id);

        assertThat(content.metadata().id()).isEqualTo(id);
        try (InputStream bytes = content.content()) {
            assertThat(bytes.readAllBytes()).isEqualTo(PDF);
        }
    }

    @Test
    @DisplayName("open on a document not live in the bound tenant is not found")
    void openUnknownIsNotFound() {
        assertThatThrownBy(() -> service.open(UUID.randomUUID())).isInstanceOf(DocumentService.NotFoundException.class);
    }

    // ── kinds

    @Test
    @DisplayName("Payslips and exports are written by the platform and never uploadable")
    void systemKindsAreNotUploadable() {
        assertThat(Arrays.stream(DocumentKind.values()).filter(DocumentKind::isUploadable))
                .containsExactlyInAnyOrder(
                        DocumentKind.EMPLOYEE_DOCUMENT,
                        DocumentKind.LEAVE_ATTACHMENT,
                        DocumentKind.REIMBURSEMENT_RECEIPT,
                        DocumentKind.INVESTMENT_PROOF);
    }

    // ── helpers

    private DocumentServiceImpl service(List<String> types) {
        return new DocumentServiceImpl(
                documents, employees, blobs, "documents", DocumentServiceImpl.DEFAULT_MAX_BYTES, types);
    }

    private Document savedRow() {
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documents).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private void assertNothingWritten() {
        assertThat(blobs.uploads).isEmpty();
        verify(documents, never()).saveAndFlush(any());
    }

    private static InputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    /** {@code head} followed by spaces, to exactly {@code size} bytes. */
    private static byte[] padded(byte[] head, int size) {
        byte[] out = new byte[size];
        Arrays.fill(out, (byte) ' ');
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    /** An in-memory store that records what it was asked to write. */
    static class RecordingBlobStorage implements BlobStorage {

        record Upload(String container, String path, byte[] content, String contentType) {}

        final List<Upload> uploads = new ArrayList<>();

        @Override
        public void upload(String container, String path, byte[] content, String contentType) {
            uploads.add(new Upload(container, path, content, contentType));
        }

        @Override
        public void upload(String container, String path, java.nio.file.Path file, String contentType) {
            try {
                uploads.add(new Upload(container, path, java.nio.file.Files.readAllBytes(file), contentType));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public InputStream open(String container, String path) {
            return uploads.stream()
                    .filter(u -> u.container().equals(container) && u.path().equals(path))
                    .findFirst()
                    .map(u -> (InputStream) new ByteArrayInputStream(u.content()))
                    .orElseThrow();
        }
    }
}
