package com.qeetgroup.qeetpay.payments;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * De-dup store for processed Razorpay webhook ids. {@code existsById}/{@code save} (inherited) are the
 * whole contract: a replayed event whose id is already present is a no-op. The backing table has no
 * RLS (the event id is global), so these calls work with no merchant context.
 */
public interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent, String> {}
