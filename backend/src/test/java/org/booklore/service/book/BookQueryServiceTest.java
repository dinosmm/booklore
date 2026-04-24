package org.booklore.service.book;

import org.booklore.mapper.v2.BookMapperV2;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookQueryServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookMapperV2 bookMapperV2;

    @Mock
    private ContentRestrictionService contentRestrictionService;

    @InjectMocks
    private BookQueryService bookQueryService;

    @Test
    void getAllBooksByLibraryIdsPaged_recomputesTotalWhenUserHasRestrictions() {
        Pageable pageable = PageRequest.of(0, 2);
        Set<Long> libraryIds = Set.of(10L);
        Long userId = 7L;

        BookEntity b1 = BookEntity.builder().id(1L).build();
        BookEntity b2 = BookEntity.builder().id(2L).build();
        BookEntity b3 = BookEntity.builder().id(3L).build();

        Page<BookEntity> repositoryPage = new PageImpl<>(List.of(b1, b2), pageable, 3);
        when(bookRepository.findAllWithMetadataByLibraryIdsPage(eq(libraryIds), eq(pageable))).thenReturn(repositoryPage);
        when(bookRepository.findAllWithMetadataByLibraryIds(eq(libraryIds))).thenReturn(List.of(b1, b2, b3));

        when(contentRestrictionService.applyRestrictions(eq(List.of(b1, b2)), eq(userId))).thenReturn(List.of(b1));
        when(contentRestrictionService.applyRestrictions(eq(List.of(b1, b2, b3)), eq(userId))).thenReturn(List.of(b1, b3));
        when(contentRestrictionService.hasRestrictions(eq(userId))).thenReturn(true);

        when(bookMapperV2.toDTO(any(BookEntity.class))).thenReturn(Book.builder().build());

        Page<Book> result = bookQueryService.getAllBooksByLibraryIdsPaged(libraryIds, userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getAllBooksByLibraryIds_deduplicatesDuplicateBookRowsById() {
        Set<Long> libraryIds = Set.of(10L);
        Long userId = 7L;

        BookEntity duplicateA = BookEntity.builder().id(1L).build();
        BookEntity duplicateB = BookEntity.builder().id(1L).build();

        when(bookRepository.findAllWithMetadataByLibraryIds(eq(libraryIds))).thenReturn(List.of(duplicateA, duplicateB));
        when(contentRestrictionService.applyRestrictions(eq(List.of(duplicateA, duplicateB)), eq(userId))).thenReturn(List.of(duplicateA, duplicateB));
        when(bookMapperV2.toDTO(any(BookEntity.class))).thenReturn(Book.builder().id(1L).build());

        List<Book> result = bookQueryService.getAllBooksByLibraryIds(libraryIds, false, true, userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);
    }
}
