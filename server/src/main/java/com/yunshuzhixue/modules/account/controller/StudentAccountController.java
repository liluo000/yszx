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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 学员账号（S-ACC-01~13）：公开接口与需登录接口按方法区分 */
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

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accounts.resetPassword(request.phone(), request.smsCode(), request.newPassword());
        return ApiResponse.ok();
    }

    @PatchMapping("/me")
    public ApiResponse<StudentProfile> updateMe(@RequestBody Map<String, Object> body) {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(accounts.updateProfile(body));
    }

    @PostMapping("/me/avatar")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AvatarResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(new AvatarResponse(accounts.updateAvatar(file)));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        StudentStp.LOGIC.checkLogin();
        accounts.changePassword(request.oldPassword(), request.smsCode(), request.newPassword());
        return ApiResponse.ok();
    }

    @PutMapping("/me/phone")
    public ApiResponse<Void> changePhone(@Valid @RequestBody ChangePhoneRequest request) {
        StudentStp.LOGIC.checkLogin();
        accounts.changePhone(request.newPhone(), request.newSmsCode(), request.oldSmsCode(), request.password());
        return ApiResponse.ok();
    }

    @PostMapping("/me/cancellation")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CancellationResponse> requestCancellation(
            @Valid @RequestBody CancellationRequest request) {
        StudentStp.LOGIC.checkLogin();
        Instant effectiveAt = accounts.requestCancellation(request.smsCode(), request.confirmed());
        return ApiResponse.ok(new CancellationResponse(effectiveAt));
    }

    @DeleteMapping("/me/cancellation")
    public ApiResponse<StudentProfile> cancelCancellation() {
        StudentStp.LOGIC.checkLogin();
        return ApiResponse.ok(accounts.cancelCancellation());
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

    public record ResetPasswordRequest(@NotBlank @Pattern(regexp = PHONE) String phone,
                                       @NotBlank @Pattern(regexp = "^[0-9]{6}$") String smsCode,
                                       @NotBlank @Size(min = 6, max = 20) String newPassword) {
    }

    public record ChangePasswordRequest(@Size(min = 6, max = 20) String oldPassword,
                                        @Pattern(regexp = "^[0-9]{6}$") String smsCode,
                                        @NotBlank @Size(min = 6, max = 20) String newPassword) {
    }

    public record ChangePhoneRequest(@NotBlank @Pattern(regexp = PHONE) String newPhone,
                                     @NotBlank @Pattern(regexp = "^[0-9]{6}$") String newSmsCode,
                                     @Pattern(regexp = "^[0-9]{6}$") String oldSmsCode,
                                     @Size(min = 6, max = 20) String password) {
    }

    public record CancellationRequest(@NotBlank @Pattern(regexp = "^[0-9]{6}$") String smsCode,
                                      boolean confirmed) {
    }

    public record CancellationResponse(Instant cancellationEffectiveAt) {
    }

    public record AvatarResponse(String avatarUrl) {
    }
}
