package com.qeetgroup.qeetpay.filing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.gst.GstInvoice;
import com.qeetgroup.qeetpay.gst.GstInvoiceService;
import com.qeetgroup.qeetpay.gst.GstInvoiceStatus;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GST return filing (TAD §7.4). Preparing a return reads the period's invoices from the {@code gst}
 * module (via its public read API — never its tables), aggregates the CGST/SGST/IGST totals, and — for
 * GSTR-1 — projects each invoice into an outward-supply line. Filing submits the prepared return to
 * GSTN through the pluggable {@link GstnFilingAdapter} and records the ARN. Preparation is
 * re-runnable until the return is FILED; filing is idempotent.
 */
@Service
public class FilingService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final GstReturnRepository returns;
    private final GstReturnLineRepository returnLines;
    private final GstInvoiceService gstInvoices;
    private final GstnFilingAdapter gstn;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public FilingService(
            GstReturnRepository returns,
            GstReturnLineRepository returnLines,
            GstInvoiceService gstInvoices,
            GstnFilingAdapter gstn,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.returns = returns;
        this.returnLines = returnLines;
        this.gstInvoices = gstInvoices;
        this.gstn = gstn;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Aggregates the period's invoices into a (re-preparable) GSTR return. */
    @Transactional
    public ReturnWithLines prepareReturn(UUID merchantId, GstReturnType type, String period) {
        merchantScope.apply(merchantId);
        YearMonth ym = parsePeriod(period);
        Instant from = ym.atDay(1).atStartOfDay(INDIA).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(INDIA).toInstant();

        List<GstInvoice> invoices =
                gstInvoices.findIssuedInPeriod(merchantId, from, to).stream()
                        .filter(i -> i.getStatus() != GstInvoiceStatus.CANCELLED)
                        .toList();

        long taxable = 0, cgst = 0, sgst = 0, igst = 0;
        for (GstInvoice i : invoices) {
            taxable += i.getTaxableMinor();
            cgst += i.getCgstMinor();
            sgst += i.getSgstMinor();
            igst += i.getIgstMinor();
        }

        GstReturn ret =
                returns
                        .findByMerchantIdAndReturnTypeAndPeriod(merchantId, type, period)
                        .orElseGet(() -> new GstReturn(merchantId, type, period));
        if (ret.getStatus() == GstReturnStatus.FILED) {
            throw new IllegalStateException(type + " for " + period + " is already filed");
        }

        ret.prepare(invoices.size(), taxable, cgst, sgst, igst);
        returns.save(ret);

        // GSTR-1 carries per-invoice detail; GSTR-3B is summary-only. Re-preparation replaces lines.
        returnLines.deleteByReturnId(ret.getId());
        List<GstReturnLine> lines = new ArrayList<>();
        if (type == GstReturnType.GSTR1) {
            for (GstInvoice i : invoices) {
                lines.add(
                        returnLines.save(
                                new GstReturnLine(
                                        ret.getId(), merchantId, i.getId(), i.getInvoiceNumber(),
                                        i.getBuyerGstin(), i.getPlaceOfSupply(), i.getSupplyType().name(),
                                        i.getTaxableMinor(), i.getCgstMinor(), i.getSgstMinor(), i.getIgstMinor())));
            }
        }

        outbox.enqueue(merchantId, "gst.return.prepared", returnJson(ret));
        return new ReturnWithLines(ret, lines);
    }

    /** Files a prepared return at GSTN and records the ARN. Idempotent once FILED. */
    @Transactional
    public GstReturn fileReturn(UUID merchantId, UUID returnId) {
        merchantScope.apply(merchantId);
        GstReturn ret = load(merchantId, returnId);
        if (ret.getStatus() == GstReturnStatus.FILED) {
            return ret; // idempotent
        }
        List<GstReturnLine> lines = returnLines.findByReturnIdOrderByInvoiceNumber(returnId);
        String arn = gstn.file(ret, lines);
        ret.markFiled(arn);
        returns.save(ret);
        outbox.enqueue(merchantId, "gst.return.filed", returnJson(ret));
        return ret;
    }

    @Transactional(readOnly = true)
    public ReturnWithLines getReturn(UUID merchantId, UUID returnId) {
        merchantScope.apply(merchantId);
        GstReturn ret = load(merchantId, returnId);
        return new ReturnWithLines(ret, returnLines.findByReturnIdOrderByInvoiceNumber(returnId));
    }

    @Transactional(readOnly = true)
    public List<GstReturn> listReturns(UUID merchantId) {
        merchantScope.apply(merchantId);
        return returns.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private GstReturn load(UUID merchantId, UUID returnId) {
        return returns
                .findById(returnId)
                .filter(r -> r.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new GstReturnNotFoundException("no return " + returnId));
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period); // expects YYYY-MM
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("period must be YYYY-MM, got '" + period + "'");
        }
    }

    /** A return plus its outward-supply lines (empty for GSTR-3B). */
    public record ReturnWithLines(GstReturn ret, List<GstReturnLine> lines) {}

    private String returnJson(GstReturn r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("returnId", r.getId().toString());
        b.put("type", r.getReturnType().name());
        b.put("period", r.getPeriod());
        b.put("status", r.getStatus().name());
        b.put("invoiceCount", r.getInvoiceCount());
        b.put("totalTaxMinor", r.getTotalTaxMinor());
        if (r.getGstnArn() != null) {
            b.put("arn", r.getGstnArn());
        }
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise filing event", e);
        }
    }
}
