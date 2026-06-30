package com.qeetgroup.qeetpay.gst;

/** A requested invoice line before GST is computed. Amounts in minor units; gstRate is whole percent. */
public record GstLineInput(
        String description, String hsnSac, long quantity, long unitPriceMinor, int gstRate) {}
