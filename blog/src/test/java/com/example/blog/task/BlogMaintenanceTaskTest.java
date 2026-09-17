package com.example.blog.task;

import com.example.blog.service.HotArticleService;
import com.example.blog.service.ViewCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogMaintenanceTaskTest {

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private HotArticleService hotArticleService;

    @InjectMocks
    private BlogMaintenanceTask task;

    @Test
    void flushesViewCountsBeforeRefreshingHotArticles() {
        when(viewCountService.flushToDatabase()).thenReturn(3);

        task.runMaintenance();

        InOrder order = inOrder(viewCountService, hotArticleService);
        order.verify(viewCountService).flushToDatabase();
        order.verify(hotArticleService).refreshHotArticles();
    }
}
