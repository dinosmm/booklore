package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {
        BookloreApplication.class
})
@Transactional
@TestPropertySource(properties = {
        "logging.level.root=debug",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.task.scan-library-cron=*/1 * * * * *",
        "app.task.process-bookdrop-cron=*/1 * * * * *",
        "app.features.oidc-enabled=false",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false"
})
@Import(BookRepositoryDataJpaTest.TestConfig.class)
class BookRepositoryDataJpaTest {

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.boot.test.context.TestConfiguration
    public static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public TaskCronService taskCronService() {
            return mock(TaskCronService.class);
        }
    }

    @Test
    void contextLoads() {
        assertThat(bookRepository).isNotNull();
    }

    @Test
    void findAllWithMetadataByIds_executesAgainstJpaMetamodel() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);
        entityManager.flush();

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);
        entityManager.flush();

        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName("test.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(500L)
                .initialHash("hash1")
                .currentHash("hash1")
                .addedOn(Instant.now())
                .build();
        entityManager.persist(bookFile);
        entityManager.flush();

        entityManager.clear();

        Optional<BookEntity> result = bookRepository.findByIdForKoboDownload(1L);

        TestTransaction.end();

        assertThat(result).isPresent();

        BookEntity bookEntity = result.get();
        assertThat(bookEntity.getId()).isEqualTo(book.getId());
        assertThat(bookEntity.getPrimaryBookFile()).isNotNull();
    }

    @Test
    void countByLibraryId_countsOnlyBooksWithBookFiles() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Count Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB))
                .build();
        entityManager.persist(library);

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/count/path")
                .build();
        entityManager.persist(libraryPath);

        BookEntity fileBackedBook = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(fileBackedBook);

        BookEntity filelessBook = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(filelessBook);

        BookEntity deletedFileBackedBook = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(true)
                .build();
        entityManager.persist(deletedFileBackedBook);

        entityManager.persist(BookFileEntity.builder()
                .book(fileBackedBook)
                .fileName("one.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(123L)
                .initialHash("h1")
                .currentHash("h1")
                .addedOn(Instant.now())
                .build());

        entityManager.persist(BookFileEntity.builder()
                .book(deletedFileBackedBook)
                .fileName("two.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(456L)
                .initialHash("h2")
                .currentHash("h2")
                .addedOn(Instant.now())
                .build());

        entityManager.flush();

        long count = bookRepository.countByLibraryId(library.getId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void hasDigitalFile_spec_excludesBooksWithOnlyNonBookFiles() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Spec Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB))
                .build();
        entityManager.persist(library);

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/spec/path")
                .build();
        entityManager.persist(libraryPath);

        BookEntity bookWithBookFile = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(bookWithBookFile);

        BookEntity bookWithOnlyAdditionalFile = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(bookWithOnlyAdditionalFile);

        BookEntity filelessBook = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(filelessBook);

        entityManager.persist(BookFileEntity.builder()
                .book(bookWithBookFile)
                .fileName("primary.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(123L)
                .initialHash("bk1")
                .currentHash("bk1")
                .addedOn(Instant.now())
                .build());

        entityManager.persist(BookFileEntity.builder()
                .book(bookWithOnlyAdditionalFile)
                .fileName("notes.txt")
                .fileSubPath("")
                .isBookFormat(false)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(12L)
                .initialHash("add1")
                .currentHash("add1")
                .addedOn(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        long count = bookRepository.count(AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.inLibrary(library.getId()),
                AppBookSpecification.hasDigitalFile()
        ));

        assertThat(count).isEqualTo(1);
    }

    @Test
    void findAllWithMetadata_queries_excludeBooksWithoutBookFormatFiles() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Metadata Query Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB))
                .build();
        entityManager.persist(library);

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/metadata/query/path")
                .build();
        entityManager.persist(libraryPath);

        BookEntity included = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(included);

        BookEntity additionalOnly = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(additionalOnly);

        BookEntity fileless = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(fileless);

        entityManager.persist(BookFileEntity.builder()
                .book(included)
                .fileName("included.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(120L)
                .initialHash("inc1")
                .currentHash("inc1")
                .addedOn(Instant.now())
                .build());

        entityManager.persist(BookFileEntity.builder()
                .book(additionalOnly)
                .fileName("cover.jpg")
                .fileSubPath("")
                .isBookFormat(false)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(10L)
                .initialHash("add2")
                .currentHash("add2")
                .addedOn(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findAllWithMetadata())
                .extracting(BookEntity::getId)
                .containsExactly(included.getId());
        assertThat(bookRepository.findAllWithMetadataByLibraryId(library.getId()))
                .extracting(BookEntity::getId)
                .containsExactly(included.getId());
        assertThat(bookRepository.findAllWithMetadataPage(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(bookRepository.findAllWithMetadataByLibraryIdPaged(library.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

}
