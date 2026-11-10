package com.afterme.backend.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 로그인한 사용자 id(액세스 토큰의 subject)를 주입한다.
 *
 * <pre>
 * public ResponseEntity&lt;...&gt; getMe(@LoginUserId Long userId)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface LoginUserId {
}
