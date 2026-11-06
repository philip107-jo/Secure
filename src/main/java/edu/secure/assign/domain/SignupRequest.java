package edu.secure.assign.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 요청 본문 */
public record SignupRequest(

        @NotBlank(message = "고객 ID는 필수입니다.")
        @Size(max = 20, message = "고객 ID는 20자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "고객 ID는 영문과 숫자만 가능합니다.")
        String customerId,

    @NotBlank(message = "고객 이름은 필수입니다.")
        @Size(max = 50)
        @Pattern(
        regexp = "^[가-힣a-zA-Z0-9 ]+$",
        message = "이름에 허용되지 않는 문자가 포함되어 있습니다."
        )
String customerName,

        @NotBlank(message = "주민등록번호는 필수입니다.")
       @Pattern(regexp = "^\\d{6}-\\d{7}$", message = "주민등록번호 형식이 올바르지 않습니다.")
        String rrn,

        @NotBlank(message = "전화번호는 필수입니다.")
      @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
        String password

) {
}