package com.qeetgroup.qeetpay.platform.api;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke endpoint that surfaces the resolved request identity: the merchant from
 * {@link MerchantContext} plus the subject/roles from the Qeet ID JWT (null under an API key or in
 * dev). Proves the auth + merchant-context wiring end-to-end.
 */
@RestController
@RequestMapping("/v1")
public class MeController {

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
                MerchantContext.current() == null ? null : MerchantContext.current().toString(),
                jwt == null ? null : jwt.getSubject(),
                jwt == null ? List.of() : jwt.getClaimAsStringList("roles"),
                jwt != null);
    }

    public record MeResponse(
            String merchantId, String subject, List<String> roles, boolean authenticated) {}
}
