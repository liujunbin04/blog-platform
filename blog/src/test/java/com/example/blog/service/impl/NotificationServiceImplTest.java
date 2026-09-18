package com.example.blog.service.impl;

import com.example.blog.dto.NotificationMessage;
import com.example.blog.entity.Notification;
import com.example.blog.mapper.NotificationMapper;
import com.example.blog.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl service;

    @Test
    void duplicateEventKeyIsIgnored() {
        NotificationMessage message = new NotificationMessage(
                "LIKE",
                1L,
                2L,
                3L,
                "content",
                "LIKE:8"
        );
        NotificationServiceImpl spyService = spy(service);
        ReflectionTestUtils.setField(spyService, "baseMapper", notificationMapper);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(spyService).save(any(Notification.class));

        spyService.createFromMessage(message);
    }
}
