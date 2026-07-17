package com.qeetgroup.qeetpay.filing;

import java.util.List;

/**
 * Pluggable GST-return filing backend — sandbox or a live GSTN endpoint (via an ASP/GSP such as
 * ClearTax) (TAD §7.4). Submits a prepared return and returns the ARN (acknowledgement reference
 * number) GSTN issues on acceptance.
 */
public interface GstnFilingAdapter {

    /** Files {@code ret} (with its {@code lines} for GSTR-1) at GSTN and returns the ARN. */
    String file(GstReturn ret, List<GstReturnLine> lines);
}
