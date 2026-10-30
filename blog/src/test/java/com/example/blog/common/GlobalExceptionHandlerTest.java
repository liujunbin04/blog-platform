package com.example.blog.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全局异常处理器的错误码映射
 * <p>重点是「客户端错误不能被兜底成 500」：返回 500 会让人以为服务端出故障，
 * 而 `?count=abc` 这类问题应该在提示里指明是哪个参数。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void typeMismatchBecomesBadRequestWithParameterName() {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                "abc", int.class, "count", methodParameter(), new NumberFormatException());

        Result<Void> result = handler.handleMethodArgumentTypeMismatchException(mismatch);

        assertEquals(400, result.getCode());
        assertEquals("参数类型错误: count", result.getMessage());
    }

    private MethodParameter methodParameter() {
        try {
            return new MethodParameter(
                    GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", int.class), 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private void sample(int count) {
        // 仅用于构造 MethodParameter
    }
}