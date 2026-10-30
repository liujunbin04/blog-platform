package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.common.Result;
import com.example.blog.config.AdminProperties;
import com.example.blog.service.DlqService;
import com.example.blog.utils.SecurityUtil;
import com.example.blog.vo.DlqSummaryVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 死信队列管理（运维用）
 * <p>权限：按 {@code app.admin.usernames} 白名单校验登录名。项目没有角色体系，
 * 这里不引入一套只服务于两个端点的权限模型。</p>
 * <p>刻意<b>不加</b> {@code @Validated}：Spring 6.1+ 已对控制器方法参数做内建校验，
 * 校验失败抛 {@code HandlerMethodValidationException}，全局异常处理器会转成带提示的 400。
 * 加上 {@code @Validated} 会改成走方法级校验、抛 {@code ConstraintViolationException}，
 * 那条异常没人处理，结果是 count 超限时返回 500。两个注解只留一个，这里选不加。</p>
 */
@RestController
@RequestMapping("/api/admin/dlq")
@RequiredArgsConstructor
public class AdminDlqController {

    private final DlqService dlqService;

    private final AdminProperties adminProperties;

    /**
     * 列出全部死信队列的积压条数
     */
    @GetMapping
    public Result<List<DlqSummaryVO>> summary() {
        requireAdmin();
        return Result.success(dlqService.summary());
    }

    /**
     * 重放：把指定死信队列头部的至多 count 条消息搬回原业务队列
     */
    @PostMapping("/replay")
    public Result<Integer> replay(
            @RequestParam @NotBlank(message = "队列名不能为空") String queue,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "count 最小为 1")
            @Max(value = 100, message = "count 最大为 100") int count) {
        requireAdmin();
        return Result.success(dlqService.replay(queue, count));
    }

    private void requireAdmin() {
        String username = SecurityUtil.getLoginUser().getUsername();
        if (adminProperties.getUsernames() == null
                || !adminProperties.getUsernames().contains(username)) {
            throw new BusinessException(403, "无权访问");
        }
    }
}