package com.qeetgroup.qeetpay.tds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TDS/TCS statutory quarterly returns (PRD Module 06.4). Preparing a return reads the quarter's {@link
 * TdsDeduction} rows (recorded by {@link TdsService}), keeps those belonging to the requested {@link
 * TdsReturnForm} (24Q salary / 26Q non-salary / 27EQ TCS), aggregates the totals, synthesises the
 * consolidated deposit challan, and projects each kept row into a deductee/collectee detail line — a
 * re-preparable worksheet until it is filed. Export renders the return in the NSDL FVU-style layout
 * ({@link TdsReturnExporter}); filing submits it through the pluggable {@link TdsFilingAdapter} and
 * records the acknowledgement (provisional receipt number). Like {@code filing}, no money moves here.
 * All writes are outbox-published; merchant-scoped via platform RLS.
 */
@Service
public class TdsReturnService {

    private static final Pattern QUARTER_KEY = Pattern.compile("^(\\d{4})-Q([1-4])$");

    private final TdsReturnRepository returns;
    private final TdsReturnLineRepository returnLines;
    private final TdsDeductionRepository deductions;
    private final TdsFilingAdapter tdsFiling;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public TdsReturnService(
            TdsReturnRepository returns,
            TdsReturnLineRepository returnLines,
            TdsDeductionRepository deductions,
            TdsFilingAdapter tdsFiling,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.returns = returns;
        this.returnLines = returnLines;
        this.deductions = deductions;
        this.tdsFiling = tdsFiling;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Aggregates the quarter's deductions for {@code form} into a (re-preparable) return.
     *
     * @param quarterKey the FY quarter in the deduction key form, e.g. {@code "2026-Q2"}
     */
    @Transactional
    public ReturnWithLines prepareReturn(UUID merchantId, TdsReturnForm form, String quarterKey) {
        merchantScope.apply(merchantId);
        if (form == null) {
            throw new IllegalArgumentException("form is required");
        }
        FyQuarter fq = FyQuarter.parse(quarterKey);

        List<TdsDeduction> kept =
                deductions.findByMerchantIdAndQuarterOrderByCreatedAtDesc(merchantId, fq.key()).stream()
                        .filter(form::matches)
                        .toList();

        long totalGross = 0, totalTax = 0;
        Set<String> deducteeKeys = new LinkedHashSet<>();
        for (TdsDeduction d : kept) {
            totalGross += d.getGrossMinor();
            totalTax += d.getTaxMinor();
            deducteeKeys.add(d.getDeducteePan() != null ? d.getDeducteePan() : d.getDeducteeName());
        }

        TdsReturn ret =
                returns
                        .findByMerchantIdAndFormAndFyAndQuarter(
                                merchantId, form, fq.fy(), fq.quarterLabel())
                        .orElseGet(() -> new TdsReturn(merchantId, form, fq.fy(), fq.quarterLabel()));
        if (ret.getStatus() == TdsReturnStatus.FILED) {
            throw new IllegalStateException(
                    form.code() + " " + fq.fy() + "/" + fq.quarterLabel() + " is already filed");
        }

        String seed = merchantId + "|" + form + "|" + fq.fy() + "|" + fq.quarterLabel();
        String bsrCode = digits(seed + "|bsr", 7);
        String challanNo = digits(seed + "|challan", 5);
        LocalDate challanDate = fq.depositDueDate();

        ret.prepare(deducteeKeys.size(), kept.size(), totalGross, totalTax, bsrCode, challanNo, challanDate);
        returns.save(ret);

        // A return is a re-preparable worksheet: replace the detail rows on every (re-)prepare.
        returnLines.deleteByReturnId(ret.getId());
        List<TdsReturnLine> lines = new ArrayList<>(kept.size());
        for (TdsDeduction d : kept) {
            lines.add(returnLines.save(new TdsReturnLine(ret.getId(), merchantId, d)));
        }

        outbox.enqueue(merchantId, "tds.return.prepared", returnJson(ret));
        return new ReturnWithLines(ret, lines);
    }

    /** Files a prepared return at the TIN gateway and records the acknowledgement. Idempotent once FILED. */
    @Transactional
    public TdsReturn fileReturn(UUID merchantId, UUID returnId) {
        merchantScope.apply(merchantId);
        TdsReturn ret = load(merchantId, returnId);
        if (ret.getStatus() == TdsReturnStatus.FILED) {
            return ret; // idempotent
        }
        if (ret.getStatus() != TdsReturnStatus.PREPARED) {
            throw new IllegalStateException("only a PREPARED return can be filed (was " + ret.getStatus() + ")");
        }
        String token = tdsFiling.file(ret);
        ret.markFiled(token);
        returns.save(ret);
        outbox.enqueue(merchantId, "tds.return.filed", returnJson(ret));
        return ret;
    }

    /** Renders the return + its detail rows into the NSDL FVU-style export layout. */
    @Transactional(readOnly = true)
    public String export(UUID merchantId, UUID returnId) {
        merchantScope.apply(merchantId);
        TdsReturn ret = load(merchantId, returnId);
        return TdsReturnExporter.export(
                ret, returnLines.findByReturnIdOrderByDeductedOnAscDeducteeNameAsc(returnId));
    }

    @Transactional(readOnly = true)
    public ReturnWithLines getReturn(UUID merchantId, UUID returnId) {
        merchantScope.apply(merchantId);
        TdsReturn ret = load(merchantId, returnId);
        return new ReturnWithLines(
                ret, returnLines.findByReturnIdOrderByDeductedOnAscDeducteeNameAsc(returnId));
    }

    @Transactional(readOnly = true)
    public List<TdsReturn> listReturns(UUID merchantId) {
        merchantScope.apply(merchantId);
        return returns.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private TdsReturn load(UUID merchantId, UUID returnId) {
        return returns
                .findById(returnId)
                .filter(r -> r.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new TdsReturnNotFoundException("no return " + returnId));
    }

    /** A return plus its deductee/collectee detail rows. */
    public record ReturnWithLines(TdsReturn ret, List<TdsReturnLine> lines) {}

    /**
     * A parsed FY quarter: the deduction key ({@code "2026-Q2"}), the assessment-year FY label
     * ({@code "2026-27"}), the quarter label ({@code "Q2"}), and the tax-deposit due date.
     */
    record FyQuarter(int fyStartYear, int quarterNum) {

        static FyQuarter parse(String quarterKey) {
            if (quarterKey == null) {
                throw new IllegalArgumentException("quarter is required");
            }
            Matcher m = QUARTER_KEY.matcher(quarterKey.trim());
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "quarter must be '<fyStartYear>-Q<1-4>', got '" + quarterKey + "'");
            }
            return new FyQuarter(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        }

        String key() {
            return fyStartYear + "-Q" + quarterNum;
        }

        String fy() {
            return fyStartYear + "-" + String.format("%02d", (fyStartYear + 1) % 100);
        }

        String quarterLabel() {
            return "Q" + quarterNum;
        }

        /** The 7th of the month after the quarter's last month — the statutory TDS/TCS deposit due date. */
        LocalDate depositDueDate() {
            return switch (quarterNum) {
                case 1 -> LocalDate.of(fyStartYear, 7, 7);       // Apr–Jun → 7 Jul
                case 2 -> LocalDate.of(fyStartYear, 10, 7);      // Jul–Sep → 7 Oct
                case 3 -> LocalDate.of(fyStartYear + 1, 1, 7);   // Oct–Dec → 7 Jan
                default -> LocalDate.of(fyStartYear + 1, 4, 7);  // Jan–Mar → 7 Apr
            };
        }
    }

    /** Deterministic n-digit token from a seed (for the sandbox BSR / challan serial). */
    private static String digits(String seed, int n) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(n);
            for (int i = 0; sb.length() < n && i < hash.length; i++) {
                sb.append((hash[i] & 0xff) % 10);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String returnJson(TdsReturn r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("returnId", r.getId().toString());
        b.put("form", r.getForm().code());
        b.put("fy", r.getFy());
        b.put("quarter", r.getQuarter());
        b.put("status", r.getStatus().name());
        b.put("deducteeCount", r.getDeducteeCount());
        b.put("deductionCount", r.getDeductionCount());
        b.put("totalTaxMinor", r.getTotalTaxMinor());
        if (r.getAckToken() != null) {
            b.put("ackToken", r.getAckToken());
        }
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise TDS return event", e);
        }
    }
}
