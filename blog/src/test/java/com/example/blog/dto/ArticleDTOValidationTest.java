package com.example.blog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsContentLargerThanTwoMillionCharacters() {
        ArticleDTO dto = validArticle();
        dto.setContent("a".repeat(2_000_001));

        Set<ConstraintViolation<ArticleDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void acceptsContentAtTwoMillionCharacters() {
        ArticleDTO dto = validArticle();
        dto.setContent("a".repeat(2_000_000));

        Set<ConstraintViolation<ArticleDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    private ArticleDTO validArticle() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("title");
        dto.setContent("content");
        dto.setStatus(1);
        return dto;
    }
}
