package com.example.blog.repository;

import com.example.blog.entity.ArticleDocument;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleSearchRepositoryCustomImplTest {

    @Test
    void articleDocumentContainsSearchableAuthorAndTagFields() {
        assertTrue(Arrays.stream(ArticleDocument.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("authorNickname")));
        assertTrue(Arrays.stream(ArticleDocument.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("tagNames")));
    }
}
