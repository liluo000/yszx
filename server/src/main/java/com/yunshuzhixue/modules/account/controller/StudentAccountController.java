package com.yunshuzhixue.modules.account.controller;

import com.yunshuzhixue.common.api.ApiResponse;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.account.service.StudentAccountService;
import com.yunshuzhixue.modules.account.service.StudentAccountService.AgreementRef;
import com.yunshuzhixue.modules.account.service.StudentAccountService.LoginResult;
import com.yunshuzhixue.modules.account.service.StudentAccountService.StudentProfile;
import com.yunshuzhixue.modules.account.service.StudentVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学员账号（S-ACC-01~05、07）：公开接口与需登录接口按方法区分 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentAccountController {

    private static final String PHONE = "^1[0-9]{10}$";
    private static final List<String> SMS_SCENES = List.of("register", "login", "reset_password",
            "change_phone_old", "change_phone_new", "cancel_account");

    private final StudentVerificationService verification;
    private final StudentAccountService accounts;

    public StudentAccountController(StudentVerificationService verification, StudentAccountService accounts) {
        this.verification = verification;
        this.accounts = accounts;
    }

    @PostMapping("/captchas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CaptchaResponse> captcha() {
        var captcha = verification.createCaptcha();
        return ApiResponse.ok(new CaptchaResponse(captcha.captchaId(), captcha.imageBase64(), captcha.expiresIn()));
    }

    @PostMapping("/sms-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        if (!SMS_SCENES.contains(request.scene())) {
            throw new com.yunshuzhixue.common.error.BusinessException(
                    com.yunshuzhixue.common.error.ErrorCode.VALIDATION_FAILED, "验证码场景不正确");
        }
        int expiresIn = verification.sendSmsCode(request.phone(), request.scene(),
                request.captchaId(), request.captchaCode());
        return ApiResponse.ok(new SmsCodeResponse(expiresIn, 60));
    }

    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(toResponse(accounts.register(request.phone(), request.smsCode(), request.agreements())));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(toResponse(accounts.login(request.loginType(), request.phone(),
                request.smsCode(), request.password(), request.agreements())));
    }

    @GetMapping("/me")
    public ApiResponse<StudentProfile> me() {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(accounts.me());
    }

    @DeleteMapping("/sessions/current")
    public ApiResponse<Void> logout() {
        StudentStp.LOGIC.checkLogin();
        accounts.logout();
        return ApiResponse.ok();
    }

    private static LoginResponse toResponse(LoginResult result) {
        return new LoginResponse(result.token(), result.isNewStudent(), result.profile());
    }

    public record CaptchaResponse(String captchaId, String imageBase64, int expiresIn) {
    }

    public record SmsCodeRequest(@NotBlank @Pattern(regexp = PHONE) String phone,
                                 @NotBlank String scene,
                                 @NotBlank String captchaId,
                                 @NotBlank @Size(max = 8) String captchaCode) {
    }

    public record SmsCodeResponse(int expiresIn, int resendAfter) {
    }

    public record RegisterRequest(@NotBlank @Pattern(regexp = PHONE) String phone,
                                  @NotBlank @Pattern(regexp = "^[0-9]{6}$") String smsCode,
                                  @Valid @Size(min = 2, max = 2) List<AgreementRef> agreements) {
    }

    public record LoginRequest(@NotBlank @Pattern(regexp = "^(sms|password)$") String loginType,
                               @NotBlank @Pattern(regexp = PHONE) String phone,
                               @Pattern(regexp = "^[0-9]{6}$") String smsCode,
                               @Size(min = 6, max = 20) String password,
                               List<AgreementRef> agreements) {
    }

    public record LoginResponse(String token, boolean isNewStudent, StudentProfile profile) {
    }
}
